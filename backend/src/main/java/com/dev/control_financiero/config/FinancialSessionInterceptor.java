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
        if (!usuarios.findById(id).map(u -> Boolean.TRUE.equals(u.getActivo())).orElse(false)) {
            response.sendError(403, "La cuenta está inactiva."); return false;
        }
        var match = java.util.regex.Pattern.compile("/(?:usuario|resumen)/(\\d+)(?:/|$)").matcher(request.getRequestURI());
        if (match.find() && !id.toString().equals(match.group(1))) {
            response.sendError(403, "Solo puedes consultar tus propias cuentas y movimientos."); return false;
        }
        return true;
    }
}
