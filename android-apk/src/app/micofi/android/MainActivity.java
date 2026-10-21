package app.micofi.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
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
    private LinearLayout root;
    private WebView web;
    private String server="";
    private ValueCallback<Uri[]> imageCallback;
    private byte[] pendingPdf;
    private WebMessagePort pdfPort;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        server=getPreferences(MODE_PRIVATE).getString("server","");
        if(validServer(server)) openServer(); else settings();
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
        disposeWeb();layout();
        root.addView(text("COFI · Pruebas Android",24));
        root.addView(text("Conecta este teléfono y tu laptop a la misma red Wi-Fi. Escribe la dirección del servidor de pruebas de COFI.",16));
        EditText address=new EditText(this);address.setSingleLine(true);address.setTextColor(Color.WHITE);address.setHintTextColor(Color.LTGRAY);address.setHint("http://192.168.1.10:8082");address.setText(server);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams input=new LinearLayout.LayoutParams(-1,-2);input.setMargins(dp(20),dp(8),dp(20),dp(8));root.addView(address,input);
        Button connect=new Button(this);connect.setText("Conectar a COFI");root.addView(connect);
        connect.setOnClickListener(v->{
            String candidate=address.getText().toString().trim().replaceAll("/+$","");
            if(!validServer(candidate)){address.setError("Usa HTTPS o una IP privada de tu red con HTTP. No incluyas usuario, contraseña ni rutas.");return;}
            Runnable save=()->{server=candidate;getPreferences(MODE_PRIVATE).edit().putString("server",server).apply();openServer();};
            if(!server.isEmpty()&&!server.equals(candidate))new AlertDialog.Builder(this).setTitle("Cambiar servidor").setMessage("Tu sesión actual se cerrará en esta app. Confirma que la dirección pertenece a tu servidor COFI.").setNegativeButton("Cancelar",null).setPositiveButton("Cambiar",(d,w)->{CookieManager.getInstance().removeAllCookies(done->save.run());}).show();else save.run();
        });
        root.addView(text("La laptop debe estar encendida y ejecutar COFI. El acceso HTTP está limitado a direcciones privadas para pruebas; usa solo una red de confianza. La app no incluye credenciales ni una base de datos propia.",14));
    }
    static boolean validServer(String value){
        try{
            URI u=new URI(value);String host=u.getHost();if(host==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getPath()==null||u.getPath().isEmpty()||u.getPath().equals("/")))return false;
            if("https".equalsIgnoreCase(u.getScheme()))return true;
            if(!"http".equalsIgnoreCase(u.getScheme()))return false;
            String[] p=host.split("\\.");if(p.length!=4)return false;int[] n=new int[4];for(int i=0;i<4;i++){if(!p[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(p[i]);if(n[i]>255)return false;}
            return n[0]==10||(n[0]==192&&n[1]==168)||(n[0]==172&&n[1]>=16&&n[1]<=31);
        }catch(Exception e){return false;}
    }
    private boolean sameServer(String url){try{URI a=new URI(server),b=new URI(url);return a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&a.getPort()==b.getPort()&&b.getRawUserInfo()==null;}catch(Exception e){return false;}}
    private void disposeWeb(){if(pdfPort!=null){pdfPort.close();pdfPort=null;}if(web!=null){web.stopLoading();web.destroy();web=null;}}
    @SuppressWarnings("SetJavaScriptEnabled") private void openServer(){
        disposeWeb();layout();
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("COFI",18);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button reload=new Button(this);reload.setText("↻");reload.setContentDescription("Recargar COFI");bar.addView(reload);
        Button config=new Button(this);config.setText("Servidor");bar.addView(config);config.setOnClickListener(v->settings());root.addView(bar);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setCacheMode(WebSettings.LOAD_NO_CACHE);s.setSupportMultipleWindows(false);s.setUserAgentString(s.getUserAgentString()+" COFIAndroid/0.1");
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();if(sameServer(url))return false;
                if(request.isForMainFrame()&&(url.startsWith("https://")||url.startsWith("http://"))){
                    if(url.startsWith("https://accounts.google.com/")){new AlertDialog.Builder(MainActivity.this).setTitle("Conexión de Gmail").setMessage("Para autorizar o reconectar Gmail durante estas pruebas, hazlo desde COFI en el navegador de la laptop. La importación ya conectada seguirá funcionando para la app.").setPositiveButton("Entendido",null).show();}
                    else try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));}catch(Exception ignored){}
                }return true;
            }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();Toast.makeText(MainActivity.this,"Certificado no válido. Revisa la dirección del servidor.",Toast.LENGTH_LONG).show();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())Toast.makeText(MainActivity.this,"No se pudo conectar. Revisa Wi-Fi, servidor y que la laptop esté encendida.",Toast.LENGTH_LONG).show();}
            @Override public void onPageFinished(WebView view,String url){if(sameServer(url))configurePdfPort(view);CookieManager.getInstance().flush();}
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
    @Override protected void onDestroy(){if(imageCallback!=null)imageCallback.onReceiveValue(null);pendingPdf=null;disposeWeb();super.onDestroy();}
}
