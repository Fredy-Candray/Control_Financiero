package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.LoginRequest;
import com.dev.control_financiero.dto.LoginResponse;
import com.dev.control_financiero.dto.RegistroUsuarioRequest;
import com.dev.control_financiero.dto.UsuarioResponse;
import com.dev.control_financiero.dto.TelefonoRequest;
import com.dev.control_financiero.dto.FotoPerfilRequest;
import com.dev.control_financiero.dto.PerfilUsuarioResponse;
import com.dev.control_financiero.dto.ActualizarPerfilRequest;
import com.dev.control_financiero.dto.MenuResponse;
import com.dev.control_financiero.dto.SolicitarRecuperacionRequest;
import com.dev.control_financiero.dto.RestablecerPasswordRequest;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.service.PasswordRecoveryService;
import com.dev.control_financiero.service.UsuarioService;
import com.dev.control_financiero.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final MenuService menuService;

    @PostMapping("/password/forgot")
    public Map<String, String> solicitarRecuperacion(@Valid @RequestBody SolicitarRecuperacionRequest request) {
        return Map.of("message", passwordRecoveryService.solicitar(request.correo()));
    }

    @PostMapping("/password/reset")
    public Map<String, String> restablecerPassword(@Valid @RequestBody RestablecerPasswordRequest request) {
        passwordRecoveryService.restablecer(request.token(), request.nuevaPassword());
        return Map.of("message", "Tu contraseña se actualizó. Ya puedes iniciar sesión.");
    }

    @PostMapping("/registro")
    public Usuario registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        return usuarioService.registrar(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        LoginResponse response = usuarioService.login(request);
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute("userId", response.getUserId());
        session.setAttribute("userRole", response.getRol());
        boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
        if (rememberMe) session.setMaxInactiveInterval(-1);
        Cookie sessionCookie = new Cookie("JSESSIONID", session.getId());
        sessionCookie.setHttpOnly(true);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(rememberMe ? Integer.MAX_VALUE : -1);
        servletResponse.addCookie(sessionCookie);
        return response;
    }

    @GetMapping("/me")
    public UsuarioResponse currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
        }
        return usuarioService.obtenerUsuarioResponse(userId);
    }

    @GetMapping("/me/menu")
    public MenuResponse currentMenu(HttpServletRequest request) {
        UsuarioResponse usuario = usuarioService.obtenerUsuarioResponse(usuarioId(request));
        if (!Boolean.TRUE.equals(usuario.activo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta está inactiva.");
        }
        return menuService.obtenerMenu(usuario.rol());
    }

    @PutMapping("/me/telefono")
    public UsuarioResponse actualizarTelefono(@Valid @RequestBody TelefonoRequest body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
        }
        return usuarioService.actualizarTelefono(userId, body.telefono());
    }

    @GetMapping("/me/profile")
    public PerfilUsuarioResponse perfilActual(HttpServletRequest request) {
        return usuarioService.obtenerPerfil(usuarioId(request));
    }

    @PutMapping("/me/profile")
    public PerfilUsuarioResponse actualizarPerfil(@Valid @RequestBody ActualizarPerfilRequest body, HttpServletRequest request) {
        return usuarioService.actualizarPerfil(usuarioId(request), body);
    }

    @PutMapping("/me/foto")
    public PerfilUsuarioResponse actualizarFotoPerfil(@Valid @RequestBody FotoPerfilRequest body, HttpServletRequest request) {
        return usuarioService.actualizarFotoPerfil(usuarioId(request), body.fotoPerfil());
    }

    @DeleteMapping("/me/foto")
    public PerfilUsuarioResponse eliminarFotoPerfil(HttpServletRequest request) {
        return usuarioService.eliminarFotoPerfil(usuarioId(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        Cookie sessionCookie = new Cookie("JSESSIONID", "");
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(0);
        response.addCookie(sessionCookie);
    }

    private Long usuarioId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
        }
        return userId;
    }

}
