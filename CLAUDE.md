# Grab

A paste-a-link Twitter/X video downloader, shipped as an installable PWA. Static files only — no build step, no bundler, no dependencies.

## Architecture

Everything runs in the browser. There is no backend except an optional CORS proxy the user deploys themselves.

- `index.html` — the entire app. Markup, CSS in a single `<style>`, logic in a single IIFE at the bottom. Deliberately dependency-free and ES5-flavoured so it runs on older mobile browsers.
- `manifest.webmanifest` — install metadata plus an Android `share_target` that feeds shared links in as `?url=` / `?text=`.
- `sw.js` — caches the app shell only. It ignores cross-origin requests entirely so it never sits between the app and Twitter.
- `worker.js` — Cloudflare Worker CORS proxy. Deployed separately, not part of the static site.
- `icon-*.png` — generated, not hand-drawn. Regenerate with Pillow if the mark changes.

## How the lookup works

1. `idFrom()` pulls the numeric post ID out of any twitter.com / x.com / fxtwitter / bare-ID string.
2. `token()` derives the syndication token: `((id / 1e15) * Math.PI).toString(36)` with zeros and dots stripped. This mirrors what Twitter's own embed widget does.
3. `lookup()` hits `cdn.syndication.twimg.com/tweet-result` — the undocumented public endpoint behind embedded tweets.
4. `pickVariants()` flattens `mediaDetails[].video_info.variants` (falling back to `data.video.variants`), keeps MP4 only, reads dimensions from the URL path, and sorts by bitrate descending.
5. `download()` streams the file through a `ReadableStream` reader for progress, then hands a blob to an `<a download>`.

If Twitter changes the response shape, `pickVariants()` and `lookup()` are the only places to touch.

## Proxy behaviour

`via()` wraps every outbound URL when a proxy is configured. The proxy is stored in localStorage, but reads and writes go through a `store()` helper wrapped in try/catch so private-mode browsers degrade to memory instead of throwing.

## Conventions

- No frameworks, no build tooling, no npm. If a change seems to need a dependency, question it first.
- Design tokens live in `:root` in `index.html`. Petrol ground (`--ink`), marigold accent (`--signal`), monospace for anything numeric. Don't introduce new colours outside those variables.
- Error copy says what happened and what to do about it. No apologies, no vague "something went wrong".
- Every failure path has a fallback — a blocked download opens the video in a tab rather than dead-ending.
- Keep it working without the proxy, just degraded.

## Testing

`python3 -m http.server 8000` then open `http://localhost:8000`. localhost counts as a secure context, so service worker registration and PWA install both work there.

## Out of scope

- HLS-only posts. They'd need remuxing; the app skips them.
- Protected, deleted, and age-restricted posts. The embed endpoint can't see them and no amount of client-side work changes that.
- Any form of authenticated access to Twitter.
