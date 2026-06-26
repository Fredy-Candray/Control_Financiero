package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.LoginRequest;
import com.dev.control_financiero.dto.LoginResponse;
import com.dev.control_financiero.dto.RegistroUsuarioRequest;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public Usuario registrar(RegistroUsuarioRequest request) {

        if (usuarioRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("El nombre de usuario ya existe");
        }

        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new RuntimeException("El correo ya existe");
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .correo(request.getCorreo())
                .username(request.getUsername())
                .password(request.getPassword())
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        return usuarioRepository.save(usuario);
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        return LoginResponse.builder()
                .message("Login correcto")
                .success(true)
                .userId(usuario.getId())
                .username(usuario.getUsername())
                .build();
    }

    public Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
