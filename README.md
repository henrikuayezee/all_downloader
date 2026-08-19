# Grab

A paste-a-link video downloader for Twitter/X, Instagram, and Facebook that installs to your home screen. Static files only — no build step, no server of your own except an optional CORS proxy.

## Files

| File | What it's for |
|---|---|
| `index.html` | The whole app: UI, lookup, download |
| `manifest.webmanifest` | Makes it installable; registers it as an Android share target |
| `sw.js` | Service worker so it opens offline |
| `icon-*.png` | Home screen icons |
| `worker.js` | Optional Cloudflare Worker that fixes CORS and fetches post pages |

## 1. Put it online

It needs HTTPS to install as a PWA. Any static host works. GitHub Pages:

```bash
git init && git add . && git commit -m "grab"
git branch -M main
git remote add origin https://github.com/<you>/grab.git
git push -u origin main
```

Then Settings → Pages → deploy from `main` / root. Netlify Drop (drag the folder onto netlify.com/drop) and Cloudflare Pages work the same way.

To test locally first: `python3 -m http.server 8000` and open `http://localhost:8000`. localhost counts as secure, so install and service worker both work.

## 2. Install it

- **Android / Chrome** — menu → Add to Home screen. You'll then be able to share a post straight from Twitter into Grab, and it starts looking up immediately.
- **iOS / Safari** — Share → Add to Home Screen. iOS has no share target, so paste the link in.

## 3. Deploy the proxy (probably necessary)

Browsers won't let a page on your domain read responses from Twitter, Instagram, or Facebook's servers unless they opt in, and they mostly don't. Without the proxy, lookups may fail and saves will fall back to opening the video in a new tab for a long-press save. The proxy makes both work properly, including the download progress bar — and for Instagram/Facebook it's what fetches the post page itself, not just the media.

1. Sign in at dash.cloudflare.com → Workers & Pages → Create → Worker.
2. Replace the default code with the contents of `worker.js`. Deploy.
3. Copy the address it gives you (`https://something.workers.dev`).
4. In Grab, open **Connection** and paste it in. It's remembered.

The worker only forwards requests to Twitter's own hosts (`cdn.syndication.twimg.com`, `video.twimg.com`, `pbs.twimg.com`, `abs.twimg.com`), Instagram/Facebook's post-page domains (`instagram.com`, `facebook.com`, `m.facebook.com`, `fb.watch`), and their CDN subdomains (`*.cdninstagram.com`, `*.fbcdn.net`) — see `isAllowed()` in `worker.js` — so nobody who finds the URL can use it as a general-purpose proxy. The free plan covers 100,000 requests a day.

## How it works

**Twitter/X:** Grab pulls the tweet ID out of whatever you paste, then asks the same public endpoint Twitter's own embed widgets use for that post's data. That comes back with every MP4 rendition Twitter encoded, so the app lists them by resolution and bitrate instead of guessing which one you want.

**Instagram/Facebook:** neither platform has an equivalent public API. Grab instead fetches the post's own page and reads the Open Graph preview tags (`og:video`, `og:image`, `og:title`) embedded in its HTML — the same data a link-preview card would use. That only ever yields one quality, so Instagram/Facebook results show a single "Save" option rather than a list.

Either way, picking a quality streams it into a blob and hands it to the browser as a file download.

## Limits

- Protected, private, deleted, suspended and age-restricted posts return nothing on any platform. Twitter's embed endpoint and Instagram/Facebook's page tags only see what a logged-out visitor sees.
- Instagram and Facebook give one quality, not several — there's no ranked list of renditions to draw from, just the page's own preview data.
- Image-only Twitter posts show "no video". Instagram/Facebook posts with no `og:video` tag show "no public video found."
- Twitter's endpoint is undocumented; Instagram/Facebook's page markup isn't a stable contract either. If either changes shape, the lookup breaks — the fix lives in `pickVariants()`/`lookup()` for Twitter, or `metaTag()`/`fetchPageMeta()` for Instagram/Facebook.
- Some very old Twitter posts only have HLS renditions, which need remuxing and are skipped.

Save what you have the right to save — your own posts, licensed material, or anything else you're permitted to keep a copy of.
