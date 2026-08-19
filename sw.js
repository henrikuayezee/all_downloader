/* Grab — offline shell.
   Caches only the app itself. Videos are never cached. */

const CACHE = "grab-v1";
const SHELL = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-maskable.png"
];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;

  // Only ever handle same-origin GETs. Everything else (lookups, video
  // streams, the proxy) goes straight to the network, untouched.
  if (req.method !== "GET") return;
  if (new URL(req.url).origin !== self.location.origin) return;

  // Navigations: network first so a fresh copy wins, cache as the fallback.
  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req).catch(() => caches.match("./index.html"))
    );
    return;
  }

  e.respondWith(
    caches.match(req).then((hit) => hit || fetch(req))
  );
});
