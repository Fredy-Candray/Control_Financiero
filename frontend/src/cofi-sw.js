// Network-only: never persist account data, sessions, API replies or PDFs in Cache Storage.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || request.mode !== 'navigate' || url.origin !== self.location.origin
      || url.pathname.startsWith('/api/')) return;
  event.respondWith(fetch(request).catch(() => new Response(`<!doctype html>
    <html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="theme-color" content="#082b50"><title>COFI · Sin conexión</title>
    <style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#082b50;color:white;font:17px system-ui}main{max-width:30rem;padding:2rem}p{line-height:1.6}a{display:inline-block;padding:.8rem 1.2rem;border-radius:.7rem;background:#12a563;color:white;text-decoration:none}</style>
    <main><h1>COFI</h1><h2>Necesitas conexión a internet</h2><p>Vuelve a conectarte para consultar tus cuentas y registrar movimientos. No se han guardado operaciones pendientes en este dispositivo.</p><a href="/dashboard">Volver a intentar</a></main></html>`,
    {status:503,headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'}})));
});
