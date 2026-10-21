// Same-origin local test gateway. No Railway, no external tunnel, no request logging.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const supplied=process.argv[2];
const root=path.resolve(supplied||path.join(__dirname,'../dist/control-financiero-ui/browser'));
if(!fs.existsSync(path.join(root,'index.html')))throw Error('Compila el frontend o indica su carpeta browser.');
const port=Number(process.env.COFI_MOBILE_PORT||8082);
const localAddresses=['localhost','127.0.0.1'];
for(const addresses of Object.values(require('node:os').networkInterfaces()))for(const a of addresses||[])if(a.family==='IPv4'&&!a.internal)localAddresses.push(a.address);
const allowedHosts=new Set(localAddresses.map(address=>`${address}:${port}`));
http.createServer((req,res)=>{
  if(!allowedHosts.has(req.headers.host)){res.writeHead(403);res.end();return;}
  if(!['GET','HEAD'].includes(req.method)&&req.headers.origin!==`http://${req.headers.host}`){res.writeHead(403);res.end();return;}
  if(req.method==='GET'&&req.url==='/cofi.apk'){
    const apk=path.resolve(__dirname,'../../output/android-apk/COFI-pruebas.apk');
    if(!fs.existsSync(apk)){res.writeHead(404);res.end();return;}
    res.writeHead(200,{'Content-Type':'application/vnd.android.package-archive','Content-Disposition':'attachment; filename="COFI-pruebas.apk"','Cache-Control':'no-store'});fs.createReadStream(apk).pipe(res);return;
  }
  if(req.method==='GET'&&req.url==='/instalar'){
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});
    res.end(`<!doctype html><html lang="es"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Instalar COFI</title><style>body{background:#082b50;color:white;font:17px system-ui;max-width:32rem;margin:auto;padding:24px;line-height:1.6}a{display:inline-block;background:#12a563;color:white;padding:12px 20px;border-radius:12px;text-decoration:none}code{overflow-wrap:anywhere}</style><h1>COFI para Android</h1><p>APK de pruebas locales, para Android 8 o posterior.</p><a href="/cofi.apk">Descargar APK</a><p>Después de instalar, abre COFI e introduce esta dirección:</p><code>http://${req.headers.host}</code><p>El teléfono y la laptop deben estar en la misma red Wi-Fi. La laptop debe ejecutar el backend local de COFI.</p></html>`);return;
  }
  if(req.url.startsWith('/api/')){
    // The local backend only receives requests from this gateway. Keep cookies on the LAN origin.
    const headers={...req.headers,host:'localhost:8081',origin:'http://localhost:4200'};
    delete headers['x-forwarded-for'];delete headers['x-forwarded-host'];delete headers['x-forwarded-proto'];
    const proxy=http.request({hostname:'127.0.0.1',port:8081,path:req.url,method:req.method,headers},up=>{
      res.writeHead(up.statusCode,up.headers);up.pipe(res);
    });proxy.on('error',()=>{res.writeHead(502,{'Content-Type':'application/json'});res.end(JSON.stringify({message:'El backend local de COFI no está disponible en el puerto 8081.'}));});req.pipe(proxy);return;
  }
  let target;
  try{target=path.resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));}catch{res.writeHead(400);res.end();return;}
  if(!target.startsWith(root+path.sep)){res.writeHead(404);res.end();return;}
  if(!fs.existsSync(target)||fs.statSync(target).isDirectory())target=path.join(root,'index.html');
  const type={'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.png':'image/png','.jpg':'image/jpeg','.svg':'image/svg+xml','.webmanifest':'application/manifest+json','.ico':'image/x-icon'}[path.extname(target)]||'application/octet-stream';
  res.writeHead(200,{'Content-Type':type,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' blob:; font-src 'self' data:; object-src 'none'; frame-ancestors 'none'"});fs.createReadStream(target).pipe(res);
}).listen(port,'0.0.0.0',()=>{
  console.log(`COFI pruebas: puerto ${port}; backend http://127.0.0.1:8081`);
  for(const addresses of Object.values(require('node:os').networkInterfaces()))for(const a of addresses||[])if(a.family==='IPv4'&&!a.internal)console.log(`Dirección para el APK: http://${a.address}:${port}`);
});
