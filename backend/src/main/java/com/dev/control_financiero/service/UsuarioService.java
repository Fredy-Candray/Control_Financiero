package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.LoginRequest;
import com.dev.control_financiero.dto.LoginResponse;
import com.dev.control_financiero.dto.RegistroUsuarioRequest;
import com.dev.control_financiero.dto.UsuarioResponse;
import com.dev.control_financiero.dto.PerfilUsuarioResponse;
import com.dev.control_financiero.dto.ActualizarPerfilRequest;
import com.dev.control_financiero.dto.GuardarUsuarioRequest;
import com.dev.control_financiero.entity.Rol;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.RolRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
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
                .telefono(normalizarTelefono(request.getTelefono()))
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .rol(rolRepository.findByNombre("Usuario").orElse(null))
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
                .rol(usuario.getRol() == null ? "Usuario" : usuario.getRol().getNombre())
                .build();
    }

    public Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerUsuarioResponse(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        return respuesta(usuario);
    }

    @Transactional(readOnly = true)
    public PerfilUsuarioResponse obtenerPerfil(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        return perfil(usuario);
    }

    @Transactional
    public PerfilUsuarioResponse actualizarFotoPerfil(Long id, String fotoPerfil) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        if (fotoPerfil == null || !(fotoPerfil.startsWith("data:image/jpeg;base64,")
                || fotoPerfil.startsWith("data:image/png;base64,")
                || fotoPerfil.startsWith("data:image/webp;base64,"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser JPG, PNG o WebP.");
        }
        usuario.setFotoPerfil(fotoPerfil);
        return perfil(usuarioRepository.save(usuario));
    }

    @Transactional
    public PerfilUsuarioResponse actualizarPerfil(Long id, ActualizarPerfilRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        String correo = request.correo().trim().toLowerCase();
        String username = request.username().trim();
        usuarioRepository.findByCorreoIgnoreCase(correo).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está registrado.");
        });
        usuarioRepository.findByUsername(username).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya está en uso.");
        });
        usuario.setNombre(request.nombre().trim());
        usuario.setCorreo(correo);
        usuario.setTelefono(normalizarTelefono(request.telefono()));
        usuario.setUsername(username);
        return perfil(usuarioRepository.save(usuario));
    }

    @Transactional
    public PerfilUsuarioResponse eliminarFotoPerfil(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        usuario.setFotoPerfil(null);
        return perfil(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizarTelefono(Long id, String telefono) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        usuario.setTelefono(normalizarTelefono(telefono));
        return respuesta(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepository.findAllByOrderByIdAsc().stream()
                .map(usuario -> new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getTelefono(),
                        usuario.getUsername(), usuario.getActivo(), usuario.getFechaCreacion(),
                        usuario.getRol() == null ? "Usuario" : usuario.getRol().getNombre()))
                .toList();
    }

    @Transactional
    public UsuarioResponse crearUsuario(GuardarUsuarioRequest request) {
        validarUnicidad(request, null);
        validarPassword(request.getPassword(), true);
        Usuario nuevo = Usuario.builder()
                .nombre(request.getNombre().trim())
                .correo(request.getCorreo().trim().toLowerCase())
                .telefono(normalizarTelefono(request.getTelefono()))
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .activo(request.getActivo() == null || request.getActivo())
                .fechaCreacion(LocalDateTime.now())
                .rol(buscarRol(request.getRol()))
                .build();
        return respuesta(usuarioRepository.save(nuevo));
    }

    @Transactional
    public UsuarioResponse actualizarUsuario(Long id, GuardarUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        validarUnicidad(request, id);
        validarPassword(request.getPassword(), false);
        if ("Administrador".equals(usuario.getRol() == null ? null : usuario.getRol().getNombre())
                && !"Administrador".equals(request.getRol())
                && usuarioRepository.countByRol_Nombre("Administrador") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe permanecer al menos un administrador.");
        }
        usuario.setNombre(request.getNombre().trim());
        usuario.setCorreo(request.getCorreo().trim().toLowerCase());
        if (request.getTelefono() != null) usuario.setTelefono(normalizarTelefono(request.getTelefono()));
        usuario.setUsername(request.getUsername().trim());
        usuario.setRol(buscarRol(request.getRol()));
        if (request.getActivo() != null) usuario.setActivo(request.getActivo());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return respuesta(usuarioRepository.save(usuario));
    }

    @Transactional
    public void eliminarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        if (usuario.getRol() != null && "Administrador".equals(usuario.getRol().getNombre())
                && usuarioRepository.countByRol_Nombre("Administrador") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes eliminar al único administrador.");
        }
        // Conserva cuentas y movimientos relacionados; el usuario deja de estar activo.
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    private Rol buscarRol(String nombre) {
        if (!"Administrador".equals(nombre) && !"Usuario".equals(nombre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol seleccionado no es válido.");
        }
        return rolRepository.findByNombre(nombre)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No están configurados los roles."));
    }

    private void validarUnicidad(GuardarUsuarioRequest request, Long id) {
        usuarioRepository.findByUsername(request.getUsername().trim()).ifPresent(existente -> {
            if (!existente.getId().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya está en uso.");
        });
        usuarioRepository.findByCorreo(request.getCorreo().trim()).ifPresent(existente -> {
            if (!existente.getId().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está registrado.");
        });
    }

    private void validarPassword(String password, boolean requerida) {
        if (requerida && (password == null || password.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria.");
        }
        if (password != null && !password.isBlank() && password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres.");
        }
    }

    private UsuarioResponse respuesta(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getTelefono(), usuario.getUsername(),
                usuario.getActivo(), usuario.getFechaCreacion(), usuario.getRol().getNombre());
    }

    private PerfilUsuarioResponse perfil(Usuario usuario) {
        return new PerfilUsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getTelefono(),
                usuario.getUsername(), usuario.getActivo(), usuario.getFechaCreacion(),
                usuario.getRol() == null ? "Usuario" : usuario.getRol().getNombre(), usuario.getFotoPerfil());
    }

    private String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String valor = telefono.trim();
        if (valor.startsWith("+")) {
            String internacional = valor.replaceAll("[\\s()-]", "");
            if (internacional.length() <= 15 && internacional.matches("\\+[1-9][0-9]{7,13}")) return internacional;
        } else {
            String digitos = valor.replaceAll("\\D", "");
            if (digitos.matches("[0-9]{8}")) return digitos.substring(0, 4) + "-" + digitos.substring(4);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un número de 8 dígitos, por ejemplo 7777-7777.");
    }
}
