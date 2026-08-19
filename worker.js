/**
 * Grab — CORS proxy.
 *
 * Deploy free on Cloudflare Workers, then paste the worker's address into
 * the app under "Connection".
 *
 * Usage:  https://your-worker.workers.dev/?url=<encoded post or media url>
 *
 * Only Twitter/X, Instagram and Facebook hosts are reachable through it —
 * see isAllowed() below — so it can't be turned into an open proxy for the
 * rest of the internet.
 */

// Exact hostnames: Twitter's syndication API and media CDNs, plus the
// Instagram/Facebook domains themselves (needed to fetch a post's HTML,
// not just its media).
const ALLOWED_EXACT = [
  "cdn.syndication.twimg.com",
  "video.twimg.com",
  "pbs.twimg.com",
  "abs.twimg.com",
  "instagram.com",
  "www.instagram.com",
  "facebook.com",
  "www.facebook.com",
  "m.facebook.com",
  "fb.watch"
];

// Suffix matches: Instagram/Facebook serve media off per-request
// subdomains (scontent-*.cdninstagram.com, video-*.fbcdn.net, etc.), so
// these can't be pinned to exact hostnames. Add new CDN host patterns
// here if a platform starts using another one.
const ALLOWED_SUFFIXES = [
  ".cdninstagram.com",
  ".fbcdn.net"
];

// The subset of ALLOWED_EXACT that serves post pages (HTML) rather than
// media files — these get a browser-like User-Agent below.
const POST_PAGE_HOSTS = [
  "instagram.com",
  "www.instagram.com",
  "facebook.com",
  "www.facebook.com",
  "m.facebook.com",
  "fb.watch"
];

function isAllowed(hostname) {
  if (ALLOWED_EXACT.includes(hostname)) return true;
  return ALLOWED_SUFFIXES.some((suffix) => hostname.endsWith(suffix));
}

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, HEAD, OPTIONS",
  "access-control-allow-headers": "range, content-type",
  "access-control-expose-headers": "content-length, content-type, content-range, accept-ranges"
};

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS });
    }
    if (request.method !== "GET" && request.method !== "HEAD") {
      return text(405, "Only GET and HEAD are supported.");
    }

    const target = new URL(request.url).searchParams.get("url");
    if (!target) return text(400, "Add ?url= followed by an encoded post or media URL.");

    let parsed;
    try {
      parsed = new URL(target);
    } catch {
      return text(400, "That url parameter isn't a valid URL.");
    }
    if (parsed.protocol !== "https:") return text(400, "Only https URLs are allowed.");
    if (!isAllowed(parsed.hostname)) {
      return text(403, `${parsed.hostname} isn't on the allow list.`);
    }

    // Instagram and Facebook often refuse or strip down markup for
    // requests that look script-like, so post-page fetches get a normal
    // desktop User-Agent and an Accept-Language header. Media/API hosts
    // keep the plain UA they've always used.
    const headers = { "user-agent": "Mozilla/5.0", "accept": "*/*" };
    if (POST_PAGE_HOSTS.includes(parsed.hostname)) {
      headers["user-agent"] =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
      headers["accept-language"] = "en-US,en;q=0.9";
    }

    // Forward Range so the browser can resume or seek large files.
    const range = request.headers.get("range");
    if (range) headers.range = range;

    const upstream = await fetch(parsed.toString(), {
      method: request.method,
      headers,
      redirect: "follow"
    });

    const out = new Headers(upstream.headers);
    for (const [k, v] of Object.entries(CORS)) out.set(k, v);
    out.delete("set-cookie");
    out.delete("content-security-policy");

    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: out
    });
  }
};

function text(status, message) {
  return new Response(message, {
    status,
    headers: { ...CORS, "content-type": "text/plain; charset=utf-8" }
  });
}
