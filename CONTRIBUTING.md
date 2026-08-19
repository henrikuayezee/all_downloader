# Contributing to Grab

Thanks for taking a look. Grab is intentionally small — read this before
sending a change, since a few of its constraints aren't obvious from the code
alone.

## The ground rules

These come straight from [CLAUDE.md](CLAUDE.md), which is the source of truth
for conventions:

- **No build step, no bundler, no npm, no frameworks.** `index.html` is
  served exactly as it sits in the repo. If a change seems to need a
  dependency, that's a sign to reconsider the approach, not to add one.
- **ES5-flavoured JS.** The app runs on older mobile browsers, so avoid
  arrow functions, `let`/`const`-only patterns, template literals, classes,
  etc. in `index.html`'s script. (`worker.js` runs on Cloudflare's V8
  runtime and can use modern syntax.)
- **Design tokens only.** Colors and fonts live in `:root` in `index.html`.
  Don't introduce new colors outside those CSS variables.
- **Error copy says what happened and what to do about it.** No apologies,
  no vague "something went wrong."
- **Every failure path has a fallback.** e.g. a blocked download opens the
  video in a new tab rather than dead-ending.

## Local setup

No install step:

```bash
python3 -m http.server 8000
```

Then open `http://localhost:8000`. `localhost` counts as a secure context,
so service worker registration and "Add to Home Screen" both work there.

There's no automated test suite. Before submitting a change, manually verify
the golden path (paste a working Twitter/X, Instagram, and Facebook link and
confirm a video renders) and the failure paths (a private/login-gated post,
a non-post URL, a bad link) still show sensible messages.

## Making a change

1. Fork the repo and create a branch off `main`.
2. Keep changes scoped — this repo favors small, focused diffs over broad
   refactors.
3. If you're touching `worker.js`'s allowlist (e.g. a platform changed its
   CDN hostnames), see the "Worker allowlist" section of `CLAUDE.md` —
   `isAllowed()` is the one place to edit.
4. Open a pull request against `main` describing what changed and why, and
   how you tested it.

## Reporting bugs or requesting features

Open a GitHub issue using the provided templates. For a lookup that broke
(Twitter's API or Instagram/Facebook's page markup changed shape), include
the URL you tried (redact anything private) and what happened instead.

## Security issues

Please don't open a public issue for a vulnerability — see
[SECURITY.md](SECURITY.md) instead.
