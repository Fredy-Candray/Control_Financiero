package com.dev.control_financiero.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Additional protections for the public deployment containing real data. */
@Component @Profile("cloud & !migration") @Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CloudRequestGuard extends OncePerRequestFilter {
    private final CorsConfigurationSource cors;
    private final boolean maintenance;
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private record Attempts(long started, int count) {}
    @org.springframework.beans.factory.annotation.Autowired
    public CloudRequestGuard(CorsConfigurationSource cors, @Value("${app.cloud.maintenance:true}") boolean maintenance) {
        this(cors, maintenance, Clock.systemUTC());
    }
    CloudRequestGuard(CorsConfigurationSource cors, boolean maintenance, Clock clock) {
        this.cors=cors; this.maintenance=maintenance; this.clock=clock;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        String path=request.getRequestURI();
        if(path.startsWith("/api/")) response.setHeader("Cache-Control", "no-store");
        if(maintenance && !path.equals("/api/health")) {
            response.setHeader("Retry-After", "300"); response.sendError(503, "COFI está en mantenimiento."); return;
        }
        if(path.startsWith("/api/") && !Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            var config=cors.getCorsConfiguration(request);
            String origin=request.getHeader("Origin");
            if(origin==null || config==null || config.checkOrigin(origin)==null) {
                response.sendError(403, "Origen de solicitud no permitido."); return;
            }
        }
        if(Set.of("/api/auth/login", "/api/auth/password/forgot", "/api/auth/password/reset").contains(path)
                && "POST".equals(request.getMethod())) {
            long now=clock.millis();
            attempts.entrySet().removeIf(e -> now-e.getValue().started()>=300_000);
            String key=request.getRemoteAddr();
            if(attempts.size()>=2048 && !attempts.containsKey(key)) { response.sendError(429); return; }
            var window=attempts.compute(key,(k,old)-> old==null ? new Attempts(now,1) : new Attempts(old.started(),old.count()+1));
            if(window.count()>10) { response.setHeader("Retry-After","300"); response.sendError(429,"Intenta nuevamente en unos minutos."); return; }
        }
        chain.doFilter(request,response);
    }
}
