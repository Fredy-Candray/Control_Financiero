package com.dev.control_financiero.config;

import com.dev.control_financiero.repository.UsuarioRepository;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class FinancialSessionInterceptor implements HandlerInterceptor {
    private final UsuarioRepository usuarios;
    private final org.springframework.web.cors.CorsConfigurationSource cors;
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) return true;
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            String origin=request.getHeader("Origin");
            var config=cors.getCorsConfiguration(request);
            if ((origin!=null && (config==null || config.checkOrigin(origin)==null))
                    || (origin==null && "cross-site".equals(request.getHeader("Sec-Fetch-Site")))) {
                response.sendError(403,"Origen de solicitud no permitido."); return false;
            }
        }
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long id)) {
            response.sendError(401, "Inicia sesión nuevamente."); return false;
        }
        var usuario = usuarios.findById(id).orElse(null);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
            response.sendError(403, "La cuenta está inactiva."); return false;
        }
        long versionSesion = session.getAttribute("versionAcceso") instanceof Long valor ? valor : 0L;
        if (versionSesion != com.dev.control_financiero.service.PasswordAdminService.version(usuario)) {
            session.invalidate(); response.sendError(401, "La contraseña cambió. Inicia sesión nuevamente."); return false;
        }
        if (Boolean.TRUE.equals(usuario.getCambioPasswordPendiente())
                && !request.getRequestURI().equals("/api/auth/me")
                && !request.getRequestURI().equals("/api/auth/password/inicial")) {
            response.setStatus(428); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Debes crear tu nueva contraseña.\"}"); return false;
        }
        var match = java.util.regex.Pattern.compile("/(?:usuario|resumen)/(\\d+)(?:/|$)").matcher(request.getRequestURI());
        if (match.find() && !id.toString().equals(match.group(1))) {
            response.sendError(403, "Solo puedes consultar tus propias cuentas y movimientos."); return false;
        }
        return true;
    }
}
