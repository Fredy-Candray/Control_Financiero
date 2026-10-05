package com.dev.control_financiero.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminUsuariosInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarioRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") instanceof Long userId
                && usuarioRepository.existsByIdAndActivoTrueAndRol_Nombre(userId, "Administrador")) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Solo un administrador puede gestionar usuarios.");
        return false;
    }
}
