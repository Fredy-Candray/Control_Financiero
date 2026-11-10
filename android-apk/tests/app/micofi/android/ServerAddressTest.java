package app.micofi.android;

/** Offline checks: no server or Android device required. */
public class ServerAddressTest {
    public static void main(String[] args) {
        for(String url:new String[]{"https://cofi.example","https://cofi.example:443","http://192.168.31.152:8082","http://10.217.20.86:8082","http://172.16.0.1"})
            check(ServerAddress.valid(url),"Valid private/test or HTTPS origin");
        for(String url:new String[]{"","http://8.8.8.8","http://172.32.0.1","http://192.168.999.1","http://192.168.01.1","http://localhost","https://user:pass@cofi.example","https://cofi.example/login","https://cofi.example?token=x","https://cofi.example#x","https://cofi.example:0","https://cofi.example:65536","file:///index.html","javascript:alert(1)"})
            check(!ServerAddress.valid(url),"Reject unsafe or incomplete origin");
        check(ServerAddress.sameOrigin("https://cofi.example","https://cofi.example:443/api/auth/me"),"Default HTTPS port");
        check(!ServerAddress.sameOrigin("https://cofi.example","http://cofi.example/api/auth/me"),"No downgrade");
        check(!ServerAddress.sameOrigin("https://cofi.example","https://cofi.example.evil/api/auth/me"),"No other host");
        check(!ServerAddress.sameOrigin("https://cofi.example","https://user@cofi.example/"),"No credentials");
        check(ServerAddress.bundledPath("/api/auth/me")==null,"API stays on server");
        check(ServerAddress.bundledPath("/assets/../../private")==null,"No traversal");
        check(ServerAddress.bundledPath("/assets/%2e%2e/private")==null,"No encoded traversal");
        check("www/index.html".equals(ServerAddress.bundledPath("/movimientos")),"SPA route bundled");
        check("www/main-ABC.js".equals(ServerAddress.bundledPath("/main-ABC.js")),"App scripts bundled");
        check(ServerAddress.bundledPath("/uploads/profile.jpg")==null,"Private pictures stay on server");
        check("http://192.168.1.10:8082".equals(ServerAddress.normalize(" 192.168.1.10:8082/ ")),"Normalize local IP without protocol");
        check(!ServerAddress.local("https://cofi.example"),"Local variant rejects external host");
        check(!ServerAddress.local("https://8.8.8.8"),"Local variant rejects public IP");
        check(ServerAddress.local("https://192.168.1.10"),"Local TLS allowed");
        check(!ServerAddress.production("http://192.168.1.10:8082"),"Production rejects cleartext");
        check(ServerAddress.production("https://cofi.example"),"Production requires TLS");
        check(!ServerAddress.valid("https://10.138.25.95.8082"),"Invalid address from device screenshot");
        System.out.println("ServerAddress: 36 offline checks passed");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
