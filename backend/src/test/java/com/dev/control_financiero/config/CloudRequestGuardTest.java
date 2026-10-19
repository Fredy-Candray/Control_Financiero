package com.dev.control_financiero.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.cors.CorsConfiguration;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CloudRequestGuardTest {
    CloudRequestGuard guard(boolean maintenance) {
        var cors=new CorsConfiguration(); cors.setAllowedOrigins(List.of("https://cofi.example"));
        return new CloudRequestGuard(r->cors,maintenance,Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"),ZoneOffset.UTC));
    }
    @Test void maintenanceAllowsOnlyHealth() throws Exception {
        var g=guard(true); var chain=new MockFilterChain(); var response=new MockHttpServletResponse();
        g.doFilter(new MockHttpServletRequest("GET","/api/cuentas"),response,chain);
        assertEquals(503,response.getStatus()); assertNull(chain.getRequest());
        var health=new MockFilterChain(); g.doFilter(new MockHttpServletRequest("GET","/api/health"),new MockHttpServletResponse(),health);
        assertNotNull(health.getRequest());
    }
    @Test void rejectsMissingAndForeignOrigins() throws Exception {
        for(String origin:new String[]{"", "https://attacker.example"}) {
            var req=new MockHttpServletRequest("POST","/api/auth/login");
            if(!origin.isEmpty()) req.addHeader("Origin",origin);
            var response=new MockHttpServletResponse(); var chain=new MockFilterChain();
            guard(false).doFilter(req,response,chain);
            assertEquals(403,response.getStatus()); assertNull(chain.getRequest());
        }
    }
    @Test void rateLimitsAuthentication() throws Exception {
        var g=guard(false);
        for(int i=0;i<11;i++) {
            var req=new MockHttpServletRequest("POST","/api/auth/login"); req.addHeader("Origin","https://cofi.example");
            var res=new MockHttpServletResponse(); g.doFilter(req,res,new MockFilterChain());
            assertEquals(i<10?200:429,res.getStatus());
        }
    }
    @Test void addsPrivacyAndContentHeaders() throws Exception {
        var res=new MockHttpServletResponse(); guard(false).doFilter(new MockHttpServletRequest("GET","/api/auth/me"),res,new MockFilterChain());
        assertEquals("no-store",res.getHeader("Cache-Control"));
        assertTrue(res.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
    }
}
