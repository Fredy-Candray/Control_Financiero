package com.dev.control_financiero.controller;

import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.dto.UsuarioResponse;
import com.dev.control_financiero.dto.GuardarUsuarioRequest;
import com.dev.control_financiero.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final com.dev.control_financiero.service.PasswordAdminService passwordAdminService;

    @PostMapping("/{id}/restablecer-password")
    public java.util.Map<String, String> restablecerPassword(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long administradorId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión nuevamente.");
        passwordAdminService.restablecer(administradorId, id);
        return java.util.Map.of("message", "Contraseña temporal: cofi. El usuario deberá crear una nueva al iniciar sesión.");
    }

    @GetMapping
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioService.listarUsuarios();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crearUsuario(@Valid @RequestBody GuardarUsuarioRequest request) {
        return usuarioService.crearUsuario(request);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizarUsuario(@PathVariable Long id, @Valid @RequestBody GuardarUsuarioRequest request) {
        return usuarioService.actualizarUsuario(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
    }

    @GetMapping("/{id}")
    public Usuario obtenerUsuario(@PathVariable Long id) {
        return usuarioService.obtenerUsuario(id);
    }
}
