package com.dev.control_financiero.config;
import com.dev.control_financiero.repository.UsuarioRepository;
import com.dev.control_financiero.entity.Usuario;
import org.springframework.mock.web.*;
import org.springframework.web.cors.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class FinancialSessionInterceptorTest {
    @Test void sesionAnteriorSeInvalidaYTemporalSoloPuedeCambiarClave() throws Exception {
        var user=Usuario.builder().activo(true).versionAcceso(1L).cambioPasswordPendiente(true).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        var vieja=new MockHttpServletRequest("GET","/api/cuentas"); vieja.getSession().setAttribute("userId",1L);
        var respuesta=new MockHttpServletResponse(); assertFalse(interceptor.preHandle(vieja,respuesta,new Object())); assertEquals(401,respuesta.getStatus());
        var temporal=new MockHttpServletRequest("GET","/api/cuentas"); temporal.getSession().setAttribute("userId",1L); temporal.getSession().setAttribute("versionAcceso",1L);
        respuesta=new MockHttpServletResponse(); assertFalse(interceptor.preHandle(temporal,respuesta,new Object())); assertEquals(428,respuesta.getStatus());
        temporal.setRequestURI("/api/auth/password/inicial"); temporal.setMethod("POST");
        assertTrue(interceptor.preHandle(temporal,new MockHttpServletResponse(),new Object()));
    }
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final CorsConfiguration cors=new CorsConfiguration();
    final FinancialSessionInterceptor interceptor=new FinancialSessionInterceptor(users,r->cors);
    @Test void requiereSesion() throws Exception {
        var response=new MockHttpServletResponse(); assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET","/api/cuentas"),response,new Object())); assertEquals(401,response.getStatus());
    }
    @Test void rechazaIdentificadorAjeno() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/cuentas/usuario/2"); request.getSession().setAttribute("userId",1L);
        when(users.findById(1L)).thenReturn(Optional.of(Usuario.builder().activo(true).build()));
        var response=new MockHttpServletResponse(); assertFalse(interceptor.preHandle(request,response,new Object())); assertEquals(403,response.getStatus());
    }
    @Test void rechazaOrigenExternoEnOperacionesFinancieras() throws Exception {
        cors.setAllowedOrigins(List.of("http://localhost:4200"));
        var request=new MockHttpServletRequest("POST","/api/gmail-bac/connect"); request.addHeader("Origin","https://example.test");
        var response=new MockHttpServletResponse(); assertFalse(interceptor.preHandle(request,response,new Object())); assertEquals(403,response.getStatus());
    }
}
