# Grab

A paste-a-link video downloader for Twitter/X, Instagram, and Facebook, shipped as an installable PWA. Static files only — no build step, no bundler, no dependencies.

## Architecture

Everything runs in the browser. There is no backend except an optional CORS proxy the user deploys themselves.

- `index.html` — the entire app. Markup, CSS in a single `<style>`, logic in a single IIFE at the bottom. Deliberately dependency-free and ES5-flavoured so it runs on older mobile browsers.
- `manifest.webmanifest` — install metadata plus an Android `share_target` that feeds shared links in as `?url=` / `?text=`.
- `sw.js` — caches the app shell only. It ignores cross-origin requests entirely so it never sits between the app and the platform it's talking to.
- `worker.js` — Cloudflare Worker CORS proxy. Deployed separately, not part of the static site.
- `icon-*.png` — generated, not hand-drawn. Regenerate with Pillow if the mark changes.

## Two-tier extraction: Twitter's API vs Instagram/Facebook's OG tags

Twitter has a public syndication endpoint (see below) that returns clean JSON with every video variant it encoded — that's why Twitter results list multiple qualities.

Instagram and Facebook have no equivalent unauthenticated API. There is no JSON endpoint to call; the only reliable source for a public post's video is the Open Graph meta tags (`og:video` / `og:video:secure_url`, `og:image`, `og:title`, `og:description`) baked into the post page's own HTML — the same data a link-preview card reads. `fetchPageMeta()` fetches that page (through the proxy) and `metaTag()` regexes the tags out, matching both attribute orders since `<meta property=… content=…>` isn't guaranteed over `<meta content=… property=…>`. There's no DOMParser dependency — plain regex is enough and avoids parsing whole documents.

This is why Instagram/Facebook results carry exactly one "variant" instead of a ranked list: the preview data only ever exposes one rendition, there's nothing to rank. `normalizeTwitter()` and `normalizeMeta()` both produce the same shape (`variants`, `poster`, `caption`, `name`, `handle`) so `render()` doesn't care which platform it's showing.

It also means Instagram/Facebook lookups only work for posts a logged-out visitor can see — private accounts, some Reels, and anything login-gated return no `og:video` tag at all, and that's the page telling the truth about what it will show a stranger, not a bug in `fetchPageMeta()`.

## How the lookup works

1. `detectPlatform()` reads the pasted URL's hostname and routes to `twitter`, `instagram`, or `facebook`; `identify()` then pulls a post ID out with the platform's own validator (`idFrom()`, `instagramId()`, or `facebookId()`) so a non-post link on a recognized domain gets a clearer error than "couldn't find a video."
2. **Twitter:** `idFrom()` pulls the numeric post ID out of any twitter.com / x.com / bare-ID string. `token()` derives the syndication token: `((id / 1e15) * Math.PI).toString(36)` with zeros and dots stripped — this mirrors what Twitter's own embed widget does. `lookup()` hits `cdn.syndication.twimg.com/tweet-result` — the undocumented public endpoint behind embedded tweets. `pickVariants()` flattens `mediaDetails[].video_info.variants` (falling back to `data.video.variants`), keeps MP4 only, reads dimensions from the URL path, and sorts by bitrate descending. `normalizeTwitter()` wraps that into the shared result shape.
3. **Instagram / Facebook:** `fetchPageMeta()` + `metaTag()` scrape the post page's OG tags as described above; `normalizeMeta()` wraps that into the same shared shape, with a single labeled variant instead of a ranked list.
4. `download()` streams the file through a `ReadableStream` reader for progress, then hands a blob to an `<a download>`.

If Twitter changes the response shape, `pickVariants()` and `lookup()` are the only places to touch. If Instagram or Facebook change their page markup, `metaTag()` and `fetchPageMeta()` are the only places to touch.

## Proxy behaviour

`via()` wraps every outbound URL when a proxy is configured. The proxy is stored in localStorage, but reads and writes go through a `store()` helper wrapped in try/catch so private-mode browsers degrade to memory instead of throwing.

## Conventions

- No frameworks, no build tooling, no npm. If a change seems to need a dependency, question it first.
- Design tokens live in `:root` in `index.html`. Petrol ground (`--ink`), marigold accent (`--signal`), monospace for anything numeric. Don't introduce new colours outside those variables.
- Error copy says what happened and what to do about it. No apologies, no vague "something went wrong".
- Every failure path has a fallback — a blocked download opens the video in a tab rather than dead-ending.
- Keep it working without the proxy, just degraded.

## Worker allowlist

`worker.js` can't allowlist Instagram/Facebook by exact hostname the way it does Twitter's fixed set of hosts, because their CDNs serve media off per-request subdomains (`scontent-*.cdninstagram.com`, `video-*.fbcdn.net`, etc.). `isAllowed()` in `worker.js` is the one place to touch if a new CDN host pattern shows up — add an exact hostname to `ALLOWED_EXACT` or a new suffix to `ALLOWED_SUFFIXES`, whichever fits. Post-page fetches (as opposed to media fetches) also get a desktop User-Agent and `Accept-Language` header there, since Instagram/Facebook tend to strip or refuse markup for requests that look script-like; that host list is `POST_PAGE_HOSTS`.

## Testing

`python3 -m http.server 8000` then open `http://localhost:8000`. localhost counts as a secure context, so service worker registration and PWA install both work there.

## Out of scope

- HLS-only posts. They'd need remuxing; the app skips them.
- Protected, deleted, private, and age-restricted posts on any platform. Twitter's embed endpoint and Instagram/Facebook's OG tags only see what a logged-out visitor sees, and no amount of client-side work changes that.
- Any form of authenticated access to Twitter, Instagram, or Facebook.
- Multiple quality options for Instagram/Facebook. The page preview data only ever has one rendition to offer.
