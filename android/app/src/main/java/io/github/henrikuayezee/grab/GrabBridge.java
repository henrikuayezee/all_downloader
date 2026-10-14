package io.github.henrikuayezee.grab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;

/**
 * The page's door to things a web page can't do, exposed as window.GrabNative.
 *
 * Saving: a JavascriptInterface can only pass strings, so the page sends the video
 * in base64 chunks (beginSave → appendChunk… → finishSave). It's written straight
 * into a MediaStore entry under Movies/Grab, marked pending until complete so the
 * gallery never shows a half-written file, and dated now so it files under today.
 */
class GrabBridge {

    private static final class Pending {
        final Uri uri;
        final OutputStream out;

        Pending(Uri uri, OutputStream out) {
            this.uri = uri;
            this.out = out;
        }
    }

    private final Activity activity;
    private final Map<Integer, Pending> saves = new HashMap<>();
    private int nextId = 1;

    GrabBridge(Activity activity) {
        this.activity = activity;
    }

    /** Starts a save. Returns an id for the chunks, or 0 if storage refused. */
    @JavascriptInterface
    public synchronized int beginSave(String filename) {
        ContentResolver cr = activity.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Video.Media.DISPLAY_NAME, safeName(filename));
        v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Grab");
        v.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) return 0;
        try {
            OutputStream out = cr.openOutputStream(uri);
            if (out == null) throw new IllegalStateException("no output stream");
            int id = nextId++;
            saves.put(id, new Pending(uri, out));
            return id;
        } catch (Exception e) {
            cr.delete(uri, null, null);
            return 0;
        }
    }

    @JavascriptInterface
    public synchronized boolean appendChunk(int id, String base64) {
        Pending p = saves.get(id);
        if (p == null) return false;
        try {
            p.out.write(Base64.decode(base64, Base64.DEFAULT));
            return true;
        } catch (Exception e) {
            abortSave(id);
            return false;
        }
    }

    /** Closes the file and makes it visible to the gallery. */
    @JavascriptInterface
    public synchronized boolean finishSave(int id) {
        Pending p = saves.remove(id);
        if (p == null) return false;
        ContentResolver cr = activity.getContentResolver();
        try {
            p.out.close();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.IS_PENDING, 0);
            cr.update(p.uri, v, null, null);
            return true;
        } catch (Exception e) {
            cr.delete(p.uri, null, null);
            return false;
        }
    }

    /** Throws away a partly written save so no broken file is left in the gallery. */
    @JavascriptInterface
    public synchronized void abortSave(int id) {
        Pending p = saves.remove(id);
        if (p == null) return;
        try {
            p.out.close();
        } catch (Exception ignored) {
        }
        activity.getContentResolver().delete(p.uri, null, null);
    }

    /** navigator.clipboard.readText() doesn't work in a WebView, so Paste asks here instead. */
    @JavascriptInterface
    public String clipboardText() {
        FutureTask<String> read = new FutureTask<>(() -> {
            ClipboardManager cm = activity.getSystemService(ClipboardManager.class);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence t = clip.getItemAt(0).coerceToText(activity);
            return t == null ? "" : t.toString();
        });
        activity.runOnUiThread(read);
        try {
            return read.get();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (n.isEmpty()) n = "grab";
        if (n.length() > 120) n = n.substring(0, 120);
        return n.toLowerCase().endsWith(".mp4") ? n : n + ".mp4";
    }
}
