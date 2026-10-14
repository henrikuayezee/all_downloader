package io.github.henrikuayezee.grab;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hosts the web app (index.html, copied into assets at build time) in a WebView.
 *
 * The page is served from https://appassets.androidplatform.net rather than file://
 * so it's a normal secure origin: fetch(), localStorage (history) and the ffmpeg
 * module worker all behave as they do in Chrome. Lookups still go through the
 * Cloudflare worker exactly like the website does; the native side only adds what
 * a web page can't do — saving into the gallery and reading the clipboard
 * (see GrabBridge).
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final Pattern FIRST_URL = Pattern.compile("https?://\\S+");

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.paper));
        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.paper));
        root.addView(web);
        setContentView(root);
        fitSystemBars(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage — without it history is lost on every restart
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.addJavascriptInterface(new GrabBridge(this), "GrabNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return assets.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // Never let a third-party page load in here — it would get GrabNative too.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });
        // Without a WebChromeClient, window.confirm() silently returns false,
        // so "Clear all" in History would never work.
        web.setWebChromeClient(new WebChromeClient());

        open(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (sharedLink(intent) != null) open(intent);
    }

    /** Loads the app; a shared link rides along as ?text=, which index.html already looks up on boot. */
    private void open(Intent intent) {
        String link = sharedLink(intent);
        web.loadUrl(link == null ? START : START + "?text=" + Uri.encode(link));
    }

    /**
     * Apps share "look at this https://…", not a bare URL. Pull out the first link,
     * since the page's platform detection only recognises a URL on its own.
     */
    static String sharedLink(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return null;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null) return null;
        Matcher m = FIRST_URL.matcher(text);
        if (m.find()) return m.group();
        String t = text.toString().trim();
        return t.isEmpty() ? null : t;
    }

    /** Draw edge to edge on Android 11+ and pad the content clear of the status bar, nav bar and keyboard. */
    private void fitSystemBars(FrameLayout root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return; // Android 10: the theme's bar colours handle it
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(light, light);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout()
                    | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
