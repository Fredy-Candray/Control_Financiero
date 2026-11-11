package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordAdminServiceTest {
    final UsuarioRepository users = mock(UsuarioRepository.class);
    final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final PasswordAdminService service = new PasswordAdminService(users, tokens, encoder);
    final Usuario usuario = Usuario.builder().id(2L).activo(true).password("hash-anterior").versionAcceso(4L).build();

    @Test void soloAdministradorPuedeRestablecer() {
        assertThrows(ResponseStatusException.class, () -> service.restablecer(1L, 2L));
        verify(users, never()).findLockedById(any());
        verify(encoder, never()).encode(any());
    }
    @Test void restablecerInvalidaSesionesYEnlacesSinGuardarTextoPlano() {
        when(users.existsByIdAndActivoTrueAndRol_Nombre(1L,"Administrador")).thenReturn(true);
        when(users.findLockedById(2L)).thenReturn(Optional.of(usuario));
        when(encoder.encode("cofi")).thenReturn("hash-temporal");
        service.restablecer(1L, 2L);
        assertEquals("hash-temporal",usuario.getPassword());
        assertTrue(usuario.getCambioPasswordPendiente()); assertEquals(5L,usuario.getVersionAcceso());
        verify(tokens).deleteAllByUsuario_IdAndUsedAtIsNull(2L);
    }
    @Test void cambiarExigeOchoCaracteresConfirmacionYSesionVigente() {
        when(users.findLockedById(2L)).thenReturn(Optional.of(usuario));
        usuario.setCambioPasswordPendiente(true);
        assertThrows(ResponseStatusException.class, () -> service.cambiarInicial(2L,4L,"Clave12","Clave12"));
        assertThrows(ResponseStatusException.class, () -> service.cambiarInicial(2L,4L,"Clave123","Diferente"));
        assertThrows(ResponseStatusException.class, () -> service.cambiarInicial(2L,3L,"Clave123","Clave123"));
        when(encoder.encode("Clave123")).thenReturn("hash-personal");
        service.cambiarInicial(2L,4L,"Clave123","Clave123");
        assertFalse(usuario.getCambioPasswordPendiente()); assertEquals(5L,usuario.getVersionAcceso());
        assertEquals("hash-personal",usuario.getPassword());
    }
    @Test void noRestableceUsuarioInactivo() {
        when(users.existsByIdAndActivoTrueAndRol_Nombre(1L,"Administrador")).thenReturn(true);
        when(users.findLockedById(2L)).thenReturn(Optional.of(usuario)); usuario.setActivo(false);
        assertThrows(ResponseStatusException.class, () -> service.restablecer(1L,2L));
        verify(encoder,never()).encode(any());
    }
    @Test void releeLaVersionSiLaSesionFueRevocadaMientrasEsperabaElBloqueo() {
        var em=mock(jakarta.persistence.EntityManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"entityManager",em);
        when(users.findLockedById(2L)).thenReturn(Optional.of(usuario));
        usuario.setCambioPasswordPendiente(true);
        doAnswer(invocation -> { usuario.setVersionAcceso(5L); return null; })
                .when(em).refresh(usuario,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ResponseStatusException.class, () -> service.cambiarInicial(2L,4L,"Clave123","Clave123"));
        verify(encoder,never()).encode(any());
    }
}
