package com.dev.control_financiero.service;

import com.dev.control_financiero.repository.UsuarioRepository;
import com.dev.control_financiero.repository.PasswordResetTokenRepository;
import com.dev.control_financiero.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class PasswordAdminService {
    private final UsuarioRepository usuarios;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public void restablecer(Long administradorId, Long destinoId) {
        if (!usuarios.existsByIdAndActivoTrueAndRol_Nombre(administradorId, "Administrador"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede restablecer contraseñas.");
        Usuario usuario = usuarios.findLockedById(destinoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        refrescarBloqueado(usuario);
        if (!Boolean.TRUE.equals(usuario.getActivo()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Activa el usuario antes de restablecer su contraseña.");
        usuario.setPassword(encoder.encode("cofi"));
        usuario.setCambioPasswordPendiente(true);
        usuario.setVersionAcceso(version(usuario) + 1);
        tokens.deleteAllByUsuario_IdAndUsedAtIsNull(destinoId);
        usuarios.save(usuario);
    }

    @Transactional
    public void cambiarInicial(Long usuarioId, Long versionSesion, String nueva, String confirmacion) {
        validarNueva(nueva);
        if (!nueva.equals(confirmacion))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las contraseñas no coinciden.");
        Usuario usuario = usuarios.findLockedById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión nuevamente."));
        refrescarBloqueado(usuario);
        if (!Boolean.TRUE.equals(usuario.getActivo()) || version(usuario) != versionSesion)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión nuevamente.");
        if (!Boolean.TRUE.equals(usuario.getCambioPasswordPendiente()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay un cambio de contraseña pendiente.");
        usuario.setPassword(encoder.encode(nueva));
        usuario.setCambioPasswordPendiente(false);
        usuario.setVersionAcceso(version(usuario) + 1);
        tokens.deleteAllByUsuario_IdAndUsedAtIsNull(usuarioId);
        usuarios.save(usuario);
    }

    public static long version(Usuario usuario) { return usuario.getVersionAcceso() == null ? 0L : usuario.getVersionAcceso(); }
    private void refrescarBloqueado(Usuario usuario) {
        // El interceptor puede haber leído esta entidad antes de comenzar la transacción.
        // Relee la versión bajo el bloqueo para no reutilizar una sesión invalidada mientras esperaba.
        if (entityManager != null) entityManager.refresh(usuario, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
    public static void validarNueva(String nueva) {
        if (nueva == null || nueva.isBlank() || nueva.length() < 8 || nueva.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres y un máximo de 72 bytes.");
    }
}
