# Security Policy

## Supported versions

Grab is a single rolling app with no version branches — security fixes land
on `main` and take effect the next time the site (or your own deployment) is
rebuilt/redeployed.

## Reporting a vulnerability

Please **don't** open a public GitHub issue for a security vulnerability.

Instead, use GitHub's private reporting for this repo: go to the
**Security** tab → **Report a vulnerability**. That opens a private
advisory only the maintainer can see, so details aren't public before a fix
ships.

Useful things to include:

- What's affected — the static app (`index.html`), the optional Cloudflare
  Worker (`worker.js`), or both.
- Steps to reproduce, or a proof of concept.
- What you'd expect to happen instead.

## Scope notes

- `index.html` runs entirely client-side; there's no backend of Grab's own,
  and no user accounts or stored credentials.
- `worker.js` is an optional, user-deployed CORS proxy. Its main security
  property is the `isAllowed()` allowlist, which restricts it to Twitter,
  Instagram, and Facebook's own hosts (plus their CDN subdomains) so it
  can't be turned into an open proxy. A hostname that shouldn't pass
  `isAllowed()` but does is the kind of report we'd want to hear about.
- This project doesn't handle payments, auth tokens, or personal data
  beyond what a user's browser already sends to Twitter/Instagram/Facebook
  directly.
