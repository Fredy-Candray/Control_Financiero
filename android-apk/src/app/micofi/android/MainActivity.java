package app.micofi.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;

/** Local-test client. The server owns accounts, sessions and financial operations. */
public class MainActivity extends Activity {
    private static final int PICK_IMAGE=10, SAVE_PDF=11, MAX_PDF=20*1024*1024;
    private static final long SPLASH_DURATION_MS=2000L;
    private LinearLayout root;
    private WebView web;
    private String server="";
    private ValueCallback<Uri[]> imageCallback;
    private byte[] pendingPdf;
    private WebMessagePort pdfPort;
    private int connectionAttempt;
    private volatile HttpURLConnection connectionCheck;
    private boolean destroyed;
    private boolean localMode=true;
    private String releaseOrigin="";
    private final Handler startupHandler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Bundle metadata=getPackageManager().getApplicationInfo(getPackageName(),android.content.pm.PackageManager.GET_META_DATA).metaData;
            if(metadata!=null){localMode=metadata.getBoolean("cofi.local_mode",true);releaseOrigin=metadata.getString("cofi.release_origin","");}
        }catch(Exception ignored){}
        WebView.setWebContentsDebuggingEnabled(false);
        getWindow().setStatusBarColor(Color.rgb(8,43,80));getWindow().setNavigationBarColor(Color.rgb(8,43,80));
        server=localMode?getPreferences(MODE_PRIVATE).getString("server",""):releaseOrigin;
        if(state==null)showStartupSplash();else settings();
    }
    private void showStartupSplash(){
        if(Build.VERSION.SDK_INT>=31){
            settings();
            getSplashScreen().setOnExitAnimationListener(splash->{
                startupHandler.postDelayed(()->{
                    if(destroyed||isFinishing()){splash.remove();return;}
                    splash.animate().alpha(0f).setDuration(180).withEndAction(splash::remove).start();
                },SPLASH_DURATION_MS);
            });
            return;
        }
        layout();root.setGravity(Gravity.CENTER);
        ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("cofi","drawable",getPackageName()));logo.setContentDescription("COFI");
        root.addView(logo,new LinearLayout.LayoutParams(dp(112),dp(112)));
        TextView name=text("COFI",36);name.setGravity(Gravity.CENTER);name.setTypeface(null,android.graphics.Typeface.BOLD);root.addView(name);
        TextView tagline=text("Tus finanzas, contigo",16);tagline.setGravity(Gravity.CENTER);tagline.setTextColor(Color.rgb(168,213,225));root.addView(tagline);
        startupHandler.postDelayed(()->{if(!destroyed&&!isFinishing()){root.animate().alpha(0f).setDuration(180).withEndAction(()->{if(!destroyed&&!isFinishing())settings();}).start();}},SPLASH_DURATION_MS);
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density);}
    private void layout(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,43,80));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);root.requestApplyInsets();
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(dp(20),dp(12),dp(20),dp(12));return t;}
    private void settings(){
        cancelConnection();disposeWeb();layout();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(12),dp(28),dp(12),dp(28));scroll.addView(content);
        ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("cofi","drawable",getPackageName()));logo.setContentDescription("Logo COFI");content.addView(logo,new LinearLayout.LayoutParams(dp(100),dp(100)));
        TextView heading=text("COFI",32);heading.setGravity(Gravity.CENTER);content.addView(heading);
        TextView intro=text("Tus finanzas, contigo",18);intro.setGravity(Gravity.CENTER);content.addView(intro);
        TextView badge=text(localMode?"PRUEBAS LOCALES":"CONTROL FINANCIERO",11);badge.setTextColor(Color.rgb(116,226,194));badge.setGravity(Gravity.CENTER);content.addView(badge);
        content.addView(text(localMode?"Conecta con tu computadora":"Bienvenido a COFI",21));
        content.addView(text(localMode?"Usa la dirección de la computadora donde tienes COFI. Ambos dispositivos deben estar en la misma Wi-Fi.":"Accede a tus cuentas y movimientos de forma segura.",15));
        EditText address=new EditText(this);address.setSingleLine(true);address.setTextColor(Color.WHITE);address.setHintTextColor(Color.LTGRAY);address.setHint("Dirección del servidor");address.setContentDescription("Dirección del servidor COFI");address.setText(server);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams input=new LinearLayout.LayoutParams(-1,-2);input.setMargins(dp(20),dp(8),dp(20),dp(8));content.addView(address,input);
        address.setPadding(dp(16),dp(16),dp(16),dp(16));address.setTextSize(16);address.setMinHeight(dp(56));GradientDrawable field=new GradientDrawable();field.setColor(Color.rgb(14,56,91));field.setCornerRadius(dp(12));field.setStroke(dp(1),Color.rgb(54,94,125));address.setBackground(field);
        if(!localMode)address.setVisibility(View.GONE);
        TextView status=text("",15);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setVisibility(View.GONE);
        Button connect=new Button(this);connect.setText("Conectar a COFI");styleButton(connect);content.addView(connect,input);content.addView(status);
        connect.setOnClickListener(v->{
            String candidate=ServerAddress.normalize(localMode?address.getText().toString():releaseOrigin);
            if(!(localMode?ServerAddress.local(candidate):ServerAddress.production(candidate))){address.setError("Ejemplo: http://192.168.1.10:8082. Usa : antes del puerto, sin rutas ni contraseñas.");status.setVisibility(View.VISIBLE);status.setText(localMode?"Esta versión solo conecta con IP privadas. Puedes pegar la IP y el puerto: 192.168.1.10:8082.":"La conexión segura de esta versión no está configurada.");return;}
            address.setText(candidate);
            Runnable check=()->checkConnection(candidate,connect,address,status);
            if(!server.isEmpty()&&!server.equals(candidate))new AlertDialog.Builder(this).setTitle("Cambiar servidor").setMessage("Confirma que la dirección pertenece a tu servidor COFI. Al conectar se cerrará la sesión anterior de esta app.").setNegativeButton("Cancelar",null).setPositiveButton("Comprobar",(d,w)->check.run()).show();else check.run();
        });
        if(localMode){
            Button help=new Button(this);help.setText("¿Dónde encuentro la dirección?");help.setAllCaps(false);help.setTextColor(Color.WHITE);help.setBackgroundColor(Color.TRANSPARENT);content.addView(help,input);
            help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Conexión local").setMessage("1. Inicia COFI en la computadora.\n2. Conecta ambos dispositivos a la misma Wi-Fi.\n3. Copia su IP actual seguida de :8082.\n\nEjemplo: http://192.168.1.10:8082\n\nSi cambia la red, puede cambiar la IP. Una red de trabajo puede bloquear la comunicación entre dispositivos.").setPositiveButton("Entendido",null).show());
            content.addView(text("La pantalla de inicio abre sin conexión. Para consultar o guardar movimientos, la computadora debe estar encendida y COFI disponible.",13));
        }
        Button privacy=new Button(this);privacy.setText("Privacidad y datos");privacy.setAllCaps(false);privacy.setTextColor(Color.rgb(155,213,226));privacy.setBackgroundColor(Color.TRANSPARENT);content.addView(privacy,input);
        privacy.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Tu información en COFI").setMessage("Las cuentas, movimientos y permisos de Gmail pertenecen a tu instalación de COFI. Esta app conserva la dirección configurada, las preferencias y las cookies de sesión en su espacio privado. El instalador no incluye contraseñas ni permisos de Gmail.\n\nLas imágenes y los informes se eligen con el selector de Android. No se solicitan permisos de contactos, SMS, ubicación, cámara ni micrófono. No se guardan movimientos pendientes sin conexión.").setPositiveButton("Cerrar",null).show());
        TextView version=text(localMode?"Versión 0.6 · Local":"Versión 1.0",12);version.setGravity(Gravity.CENTER);version.setTextColor(Color.rgb(168,190,207));content.addView(version);
    }
    private void styleButton(Button button){
        GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(0,165,115));background.setCornerRadius(dp(12));button.setBackground(background);button.setTextColor(Color.WHITE);button.setAllCaps(false);button.setMinHeight(dp(50));
    }
    static boolean validServer(String value){return ServerAddress.valid(value);}
    private boolean sameServer(String url){return ServerAddress.sameOrigin(server,url);}
    private void cancelConnection(){connectionAttempt++;HttpURLConnection old=connectionCheck;connectionCheck=null;if(old!=null)old.disconnect();}
    private void checkConnection(String candidate,Button button,EditText address,TextView status){
        cancelConnection();final int attempt=connectionAttempt;
        button.setEnabled(false);address.setEnabled(false);button.setText("Comprobando conexión…");status.setVisibility(View.VISIBLE);status.setText("Contactando con tu servidor COFI…");
        new Thread(()->{
            HttpURLConnection c=null;String failure=null;
            try{
                c=(HttpURLConnection)new URL(candidate+"/api/auth/me").openConnection();connectionCheck=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setRequestProperty("Accept","application/json");
                int code=c.getResponseCode();String type=c.getContentType();
                if((code!=200&&code!=401)||type==null||!type.toLowerCase(java.util.Locale.ROOT).contains("application/json"))failure="La dirección responde, pero el servicio de COFI no está disponible. Revisa la dirección y que el backend esté iniciado.";
            }catch(javax.net.ssl.SSLException e){failure="No se pudo verificar el certificado HTTPS. Revisa la dirección y el certificado del servidor.";}
            catch(java.net.SocketTimeoutException e){failure="El servidor no respondió a tiempo. Comprueba la IP actual, la misma Wi-Fi y que el puerto 8082 permita conexiones. Algunas redes de trabajo aíslan los dispositivos.";}
            catch(Exception e){failure="No se pudo conectar al servidor. Revisa la dirección, la conexión Wi-Fi y que COFI esté iniciado.";}
            finally{if(c!=null)c.disconnect();if(connectionCheck==c)connectionCheck=null;}
            final String message=failure;runOnUiThread(()->{
                if(destroyed||attempt!=connectionAttempt)return;
                if(message!=null){button.setEnabled(true);address.setEnabled(true);button.setText("Volver a intentar");status.setText(message);return;}
                Runnable save=()->{if(destroyed||attempt!=connectionAttempt)return;server=candidate;getPreferences(MODE_PRIVATE).edit().putString("server",server).apply();openServer();};
                if(!server.equals(candidate)){WebStorage.getInstance().deleteAllData();CookieManager.getInstance().removeAllCookies(done->{CookieManager.getInstance().flush();save.run();});}else save.run();
            });
        },"cofi-connection-check").start();
    }
    private void disposeWeb(){if(pdfPort!=null){pdfPort.close();pdfPort=null;}if(web!=null){web.stopLoading();web.destroy();web=null;}}
    @SuppressWarnings("SetJavaScriptEnabled") private void openServer(){
        disposeWeb();layout();
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("COFI",18);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button reload=new Button(this);reload.setText("↻");reload.setTextSize(23);reload.setTextColor(Color.WHITE);reload.setBackgroundColor(Color.TRANSPARENT);reload.setContentDescription("Recargar COFI");bar.addView(reload,new LinearLayout.LayoutParams(dp(52),dp(52)));
        Button config=new Button(this);config.setText(localMode?"Conexión":"Inicio");config.setAllCaps(false);config.setTextColor(Color.WHITE);config.setBackgroundColor(Color.TRANSPARENT);bar.addView(config);config.setOnClickListener(v->settings());root.addView(bar);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setCacheMode(WebSettings.LOAD_NO_CACHE);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSafeBrowsingEnabled(!localMode);s.setUserAgentString(s.getUserAgentString()+" COFIAndroid/0.3");
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String scheme=request.getUrl().getScheme();
                if(("http".equals(scheme)||"https".equals(scheme))&&!sameServer(request.getUrl().toString()))return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",null,new ByteArrayInputStream(new byte[0]));
                if(!"GET".equals(request.getMethod())||!sameServer(request.getUrl().toString()))return null;
                String asset=ServerAddress.bundledPath(request.getUrl().getPath());if(asset==null)return null;
                try{
                    String mime=asset.endsWith(".html")?"text/html":asset.endsWith(".js")?"text/javascript":asset.endsWith(".css")?"text/css":asset.endsWith(".svg")?"image/svg+xml":asset.endsWith(".png")?"image/png":asset.endsWith(".jpg")?"image/jpeg":asset.endsWith(".ico")?"image/x-icon":asset.endsWith(".webmanifest")?"application/manifest+json":"application/octet-stream";
                    InputStream body=getAssets().open(asset);
                    if(asset.equals("www/index.html")){
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=body.read(buffer))!=-1)bytes.write(buffer,0,count);body.close();
                        String html=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).replace("<head>","<head><meta name=\"financial-api-base\" content=\""+server+"/api\">");body=new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    java.util.Map<String,String> headers=new java.util.HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' blob:; font-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,body);
                }catch(IOException ignored){return null;}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();if(sameServer(url))return false;
                if(localMode){new AlertDialog.Builder(MainActivity.this).setTitle("Modo local").setMessage("Los enlaces externos están deshabilitados en esta versión. Continúa las pruebas dentro de tu instalación local de COFI.").setPositiveButton("Entendido",null).show();return true;}
                if(request.isForMainFrame()&&(url.startsWith("https://")||url.startsWith("http://"))){
                    if(url.startsWith("https://accounts.google.com/")){new AlertDialog.Builder(MainActivity.this).setTitle("Conexión de Gmail").setMessage("Para autorizar o reconectar Gmail durante estas pruebas, hazlo desde COFI en el navegador de la laptop. La importación ya conectada seguirá funcionando para la app.").setPositiveButton("Entendido",null).show();}
                    else new AlertDialog.Builder(MainActivity.this).setTitle("Enlace externo").setMessage("Este enlace está fuera de tu instalación de COFI. ¿Quieres abrirlo en el navegador?").setNegativeButton("Cancelar",null).setPositiveButton("Abrir navegador",(dialog,which)->{try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));}catch(Exception ignored){}}).show();
                }return true;
            }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();Toast.makeText(MainActivity.this,"Certificado no válido. Revisa la dirección del servidor.",Toast.LENGTH_LONG).show();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())showConnectionError();}
            @Override public void onPageFinished(WebView view,String url){if(web==view&&sameServer(url))configurePdfPort(view);CookieManager.getInstance().flush();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(imageCallback!=null)imageCallback.onReceiveValue(null);imageCallback=callback;
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                try{startActivityForResult(pick,PICK_IMAGE);}catch(Exception e){imageCallback.onReceiveValue(null);imageCallback=null;}return true;
            }
        });
        web.setDownloadListener((url,agent,disposition,mime,length)->{
            if(url.startsWith("blob:"+server+"/")){
                String js="(function(){fetch("+JSONObject.quote(url)+").then(r=>r.blob()).then(b=>{if(b.size>20971520)throw Error('large');var f=new FileReader();f.onload=()=>{if(window.cofiPdfPort)window.cofiPdfPort.postMessage(f.result.split(',')[1]);};f.readAsDataURL(b);}).catch(()=>alert('No se pudo preparar el PDF.'));})()";
                web.evaluateJavascript(js,null);
            }else if(sameServer(url)&&"application/pdf".equalsIgnoreCase(mime)){downloadPdf(url);}
            else Toast.makeText(this,"Esta descarga no es un PDF compatible.",Toast.LENGTH_LONG).show();
        });
        reload.setOnClickListener(v->web.reload());web.loadUrl(server+"/login");
    }
    private void showConnectionError(){
        disposeWeb();layout();root.addView(text("No se pudo abrir COFI",24));root.addView(text("La aplicación está instalada. Revisa la conexión con tu servidor y vuelve a intentarlo.",16));
        Button retry=new Button(this);retry.setText("Revisar conexión");styleButton(retry);retry.setOnClickListener(v->settings());root.addView(retry);
    }
    private void configurePdfPort(WebView view){
        if(pdfPort!=null)pdfPort.close();WebMessagePort[] ports=view.createWebMessageChannel();pdfPort=ports[0];
        pdfPort.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort port,WebMessage message){
            if(web==null||!sameServer(web.getUrl())||message.getData()==null||message.getData().length()>MAX_PDF*4/3+8)return;
            try{offerPdf(android.util.Base64.decode(message.getData(),android.util.Base64.DEFAULT));}catch(Exception e){Toast.makeText(MainActivity.this,"No se pudo abrir el PDF.",Toast.LENGTH_LONG).show();}
        }});
        view.evaluateJavascript("window.addEventListener('message',function cofiExport(e){if(e.data==='cofi-export'&&e.ports.length){window.cofiPdfPort=e.ports[0];window.removeEventListener('message',cofiExport);}});",ignored->{if(web==view&&sameServer(view.getUrl()))view.postWebMessage(new WebMessage("cofi-export",new WebMessagePort[]{ports[1]}),Uri.parse(server));});
    }
    private void offerPdf(byte[] bytes){
        if(bytes.length<5||bytes.length>MAX_PDF||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")){Toast.makeText(this,"El archivo no es un PDF válido.",Toast.LENGTH_LONG).show();return;}
        pendingPdf=bytes;
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,"COFI-informe.pdf");
        try{startActivityForResult(save,SAVE_PDF);}catch(Exception e){pendingPdf=null;Toast.makeText(this,"No hay un gestor de archivos disponible.",Toast.LENGTH_LONG).show();}
    }
    private void downloadPdf(String url){
        final String cookies=CookieManager.getInstance().getCookie(url);
        new Thread(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);if(cookies!=null)c.setRequestProperty("Cookie",cookies);
            if(c.getResponseCode()!=200)throw new IOException();ByteArrayOutputStream output=new ByteArrayOutputStream();try(InputStream input=c.getInputStream()){byte[] b=new byte[8192];int count;while((count=input.read(b))!=-1){if(output.size()+count>MAX_PDF)throw new IOException();output.write(b,0,count);}}byte[] bytes=output.toByteArray();runOnUiThread(()->offerPdf(bytes));
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"No se pudo descargar el informe.",Toast.LENGTH_LONG).show());}finally{if(c!=null)c.disconnect();}}).start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE&&imageCallback!=null){imageCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);imageCallback=null;}
        if(request==SAVE_PDF){byte[] bytes=pendingPdf;pendingPdf=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException();out.write(bytes);Toast.makeText(this,"PDF guardado.",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"No se pudo guardar el PDF.",Toast.LENGTH_LONG).show();}}}
    }
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else if(web!=null)settings();else super.onBackPressed();}
    @Override protected void onDestroy(){destroyed=true;startupHandler.removeCallbacksAndMessages(null);cancelConnection();if(imageCallback!=null)imageCallback.onReceiveValue(null);pendingPdf=null;disposeWeb();super.onDestroy();}
}
