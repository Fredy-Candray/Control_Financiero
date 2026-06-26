package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.LoginRequest;
import com.dev.control_financiero.dto.LoginResponse;
import com.dev.control_financiero.dto.RegistroUsuarioRequest;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;

    @PostMapping("/registro")
    public Usuario registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        return usuarioService.registrar(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return usuarioService.login(request);
    }

}
