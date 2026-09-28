package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.LoginRequest;
import com.dev.control_financiero.dto.LoginResponse;
import com.dev.control_financiero.dto.RegistroUsuarioRequest;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

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
                .password(passwordEncoder.encode(request.getPassword()))
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        return usuarioRepository.save(usuario);
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new RuntimeException("La cuenta de usuario está inactiva");
        }
        String storedPassword = usuario.getPassword();
        boolean isEncoded = storedPassword != null && (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$"));
        boolean validPassword = isEncoded
                ? passwordEncoder.matches(request.getPassword(), storedPassword)
                : storedPassword != null && storedPassword.equals(request.getPassword());
        if (!validPassword) {
            throw new RuntimeException("Contraseña incorrecta");
        }
        if (!isEncoded) {
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
            usuarioRepository.save(usuario);
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
