const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'src/manifest.webmanifest')));
assert.equal(manifest.display, 'standalone');
assert.equal(manifest.start_url, '/dashboard');
for (const icon of manifest.icons) {
  const png = fs.readFileSync(path.join(root, 'src', icon.src));
  const size = icon.sizes.split('x').map(Number);
  assert.equal(png.readUInt32BE(16), size[0]);
  assert.equal(png.readUInt32BE(20), size[1]);
}
const handlers = {};
const origin = 'https://micofi.up.railway.app';
let network = async () => new Response('live response');
vm.runInNewContext(fs.readFileSync(path.join(root, 'src/cofi-sw.js'), 'utf8'), {
  self: { location: { origin }, addEventListener: (name, fn) => handlers[name] = fn },
  URL, Response, fetch: request => network(request)
  // Cache Storage is deliberately unavailable: using it fails this check.
});
function request(url, method = 'GET', mode = 'navigate') {
  let intercepted = false, response;
  handlers.fetch({request: {url, method, mode}, respondWith: value => { intercepted = true; response = value; }});
  return {intercepted, response};
}
(async () => {
  for (const url of ['/api/cuentas/usuario/1', '/api/gmail-bac/callback', '/api/reportes/pdf']) {
    assert.equal(request(origin + url).intercepted, false);
  }
  assert.equal(request(origin + '/dashboard', 'POST').intercepted, false);
  assert.equal(request('https://accounts.google.com/').intercepted, false);
  assert.equal(request(origin + '/assets/cofi-logo.png', 'GET', 'no-cors').intercepted, false);
  assert.equal(await (await request(origin + '/dashboard').response).text(), 'live response');
  network = async () => { throw new Error('offline'); };
  const offline = await request(origin + '/dashboard').response;
  assert.equal(offline.status, 503);
  assert.equal(offline.headers.get('Cache-Control'), 'no-store');
  assert.match(await offline.text(), /Necesitas conexión a internet/);
  console.log('PWA verified: standalone manifest, icon dimensions, network-only navigation, API isolation and offline notice.');
})().catch(error => { console.error(error); process.exitCode = 1; });
