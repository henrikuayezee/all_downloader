# Grab

A paste-a-link video downloader for Twitter/X, Instagram, and Facebook, shipped as an installable PWA. Static files only — no build step, no bundler. One deliberate, narrowly-scoped dependency exists (ffmpeg.wasm, for re-encoding Twitter's GIF-sourced videos — see below); everything else stays dependency-free.

## Architecture

Everything runs in the browser. There is no backend except an optional CORS proxy the user deploys themselves.

- `index.html` — the entire app. Markup, CSS in a single `<style>`, logic in a single IIFE at the bottom. Deliberately dependency-free and ES5-flavoured so it runs on older mobile browsers.
- `manifest.webmanifest` — install metadata plus an Android `share_target` that feeds shared links in as `?url=` / `?text=`.
- `sw.js` — caches the app shell only. It ignores cross-origin requests entirely so it never sits between the app and the platform it's talking to.
- `worker.js` — Cloudflare Worker CORS proxy. Deployed separately, not part of the static site.
- `wrangler.toml` — tells Cloudflare's Git integration that `worker.js` is the Worker entry point, so connecting this repo to a Cloudflare Worker project auto-deploys on push instead of needing a manual copy-paste into their dashboard editor.
- `icon-*.png` — generated, not hand-drawn. Regenerate with Pillow if the mark changes.
- `vendor/ffmpeg/` — two small files (`ffmpeg.js` + its `814.ffmpeg.js` worker chunk, ~8KB total) vendored from `@ffmpeg/ffmpeg`, used only to re-encode Twitter's GIF-sourced videos. See "GIF re-encoding" below for why these two specifically are committed instead of loaded from a CDN like everything else.

## Two-tier extraction: Twitter's API vs Instagram/Facebook's OG tags

Twitter has a public syndication endpoint (see below) that returns clean JSON with every video variant it encoded — that's why Twitter results list multiple qualities.

Instagram and Facebook have no equivalent unauthenticated API. There is no JSON endpoint to call; the only reliable source for a public post's video is the Open Graph meta tags (`og:video` / `og:video:secure_url`, `og:image`, `og:title`, `og:description`) baked into the post page's own HTML — the same data a link-preview card reads. `fetchPageMeta()` fetches that page (through the proxy) and `metaTag()` regexes the tags out, matching both attribute orders since `<meta property=… content=…>` isn't guaranteed over `<meta content=… property=…>`. There's no DOMParser dependency — plain regex is enough and avoids parsing whole documents.

This is why Instagram/Facebook results carry exactly one "variant" instead of a ranked list: the preview data only ever exposes one rendition, there's nothing to rank. `normalizeTwitter()` and `normalizeMeta()` both produce the same shape (`variants`, `poster`, `caption`, `name`, `handle`) so `render()` doesn't care which platform it's showing.

It also means Instagram/Facebook lookups only work for posts a logged-out visitor can see — private accounts, some Reels, and anything login-gated return no `og:video` tag at all, and that's the page telling the truth about what it will show a stranger, not a bug in `fetchPageMeta()`.

## How the lookup works

1. `detectPlatform()` reads the pasted URL's hostname and routes to `twitter`, `instagram`, or `facebook`; `identify()` then pulls a post ID out with the platform's own validator (`idFrom()`, `instagramId()`, or `facebookId()`) so a non-post link on a recognized domain gets a clearer error than "couldn't find a video."
2. **Twitter:** `idFrom()` pulls the numeric post ID out of any twitter.com / x.com / bare-ID string. `token()` derives the syndication token: `((id / 1e15) * Math.PI).toString(36)` with zeros and dots stripped — this mirrors what Twitter's own embed widget does. `lookup()` hits `cdn.syndication.twimg.com/tweet-result` — the undocumented public endpoint behind embedded tweets. `pickVariants()` flattens `mediaDetails[].video_info.variants` (falling back to `data.video.variants`), keeps MP4 only, reads dimensions from the URL path, and sorts by bitrate descending. It also tags each variant with `isGif` when its source media is `type: "animated_gif"` rather than `"video"` — Twitter converts posted GIFs into a silent looping MP4 that's byte-valid but has caused playback/thumbnailing failures on some Android video players and gallery apps even though it plays fine in a browser; `render()` labels those variants "GIF loop" instead of a resolution/bitrate so the mismatch is expected, not mistaken for a broken download. `normalizeTwitter()` wraps that into the shared result shape.
3. **Instagram / Facebook:** `fetchPageMeta()` + `metaTag()` scrape the post page's OG tags as described above; `normalizeMeta()` wraps that into the same shared shape, with a single labeled variant instead of a ranked list.
4. `download()` streams the file through a `ReadableStream` reader for progress, then hands a blob to an `<a download>`.

If Twitter changes the response shape, `pickVariants()` and `lookup()` are the only places to touch. If Instagram or Facebook change their page markup, `metaTag()` and `fetchPageMeta()` are the only places to touch.

## GIF re-encoding (the one dependency)

Twitter's GIF-to-MP4 conversions (`isGif` variants, see above) are byte-valid but built purely for looping in a `<video>` tag; they've been observed failing to open, thumbnail, or appear in the gallery on some Android video players even though they play fine in a browser. The only real fix is re-encoding, not just relabeling — a diagnosis reached and confirmed live: the same file downloaded byte-identical through the worker, parsed as a structurally sound MP4 (`ftyp`/`moov`/`mdat` fully accounted for, standard `avc1`/H.264, sane duration), yet a real device still rejected it, while a matching non-GIF video tweet saved and displayed fine — isolating the fault to Twitter's GIF-conversion pipeline itself, not this app's download path.

`reencodeGif()` in `index.html` re-encodes with ffmpeg.wasm (`libx264`, baseline profile, `yuv420p`, `+faststart`, no audio track) — the most broadly device-compatible H.264 encode available — only when `v.isGif` is true, and only once the user clicks Save (never on page load, never for a normal video). This is a real, load-bearing exception to "no dependencies," made with the user's explicit sign-off after being told the concrete cost: a ~32MB one-time library fetch per browser, real CPU time to transcode, and no functionality on browsers without WebAssembly. Any failure in this path — old browser, blocked CDN, a conversion error — falls back to saving the original file untouched, per the "every failure path has a fallback" convention below.

The loading split is deliberate, not arbitrary, and took real trial-and-error to land on:
- `ffmpeg.js` and its worker chunk (`814.ffmpeg.js`) **must** be same-origin. Internally the library does `new Worker(url, {type: "module"})` to spin up its class worker, and browsers refuse to construct a `Worker` from a cross-origin script URL — this is a hard same-origin restriction, not a missing-CORS-header problem, and no amount of `Access-Control-Allow-Origin: *` on the CDN side fixes it. That's why these two tiny files (~8KB combined) are vendored into `vendor/ffmpeg/` instead of pulled from unpkg like everything else.
- The ~32MB core (`ffmpeg-core.js` + `ffmpeg-core.wasm`) does **not** have that restriction — it's loaded with a plain `fetch()` from inside that worker, which cross-origin CORS allows fine (unpkg serves `access-control-allow-origin: *`). Vendoring 32MB into this repo for no reason would be a bad trade, so those two stay on a CDN, fetched lazily.
- Only the single-threaded core (`@ffmpeg/core`, not `@ffmpeg/core-mt`) is used. The multi-threaded core needs `SharedArrayBuffer`, which needs `Cross-Origin-Opener-Policy`/`Cross-Origin-Embedder-Policy` response headers — headers a plain static host like GitHub Pages can't be configured to send. The single-threaded core needs none of that, so this keeps working on any static host with zero server config, at the cost of a slower encode.
- Versions are pinned (`@ffmpeg/ffmpeg@0.12.15`, `@ffmpeg/core@0.12.10`) rather than left to float, and re-verified end-to-end (real tweet, real transcode, real byte-level output check) before shipping — this loading dance is fragile enough across versions that an untested bump is not safe to assume works.

## Proxy behaviour

`via()` wraps every outbound URL when a proxy is configured. The proxy is stored in localStorage, but reads and writes go through a `store()` helper wrapped in try/catch so private-mode browsers degrade to memory instead of throwing.

`DEFAULT_PROXY` in `index.html` pre-fills the Connection box with this deployment's own worker, so a fresh browser/device routes through it without anyone touching Connection. The boot logic distinguishes "never saved anything" (localStorage key absent → `store()` returns `undefined` → falls back to `DEFAULT_PROXY`) from "explicitly cleared" (key present but `""` → respected as "no proxy"), so clearing the box is a real, persistent opt-out rather than snapping back to the default on reload. If this fork points at a different worker, update `DEFAULT_PROXY` to match.

## Conventions

- No frameworks, no build tooling, no npm. If a change seems to need a dependency, question it first — ffmpeg.wasm (see "GIF re-encoding" above) is the one deliberate exception, added only after confirming no lighter fix existed and getting explicit sign-off on the cost.
- Design tokens live in `:root` in `index.html`: paper ground (`--paper`), near-black ink for text and 2.5px borders (`--ink`), cobalt accent (`--signal`), monospace for anything numeric, zero border-radius (`--r:0px`) and hard offset box-shadows throughout — a bold-graphic identity, not a soft one. Don't introduce new colours outside those variables, and don't reach for `border-radius` or soft shadows; the flat/bordered/offset-shadow language is the point. `icon-*.png` follow the same palette — regenerate them (Pillow) if the mark or its colours change, same as before.
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
