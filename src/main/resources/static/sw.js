self.addEventListener('install', (event) => {
    console.log('[Service Worker] Instalado');
});

self.addEventListener('fetch', (event) => {
    event.respondWith(fetch(event.request));
});