package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryResetTest {
    @Test void enlaceValidoCreaClavePersonalYAnulaSesionesAnteriores() {
        var users=mock(UsuarioRepository.class); var tokens=mock(PasswordResetTokenRepository.class);
        var encoder=mock(PasswordEncoder.class);
        var u=Usuario.builder().id(2L).activo(true).cambioPasswordPendiente(true).versionAcceso(3L).build();
        var token=PasswordResetToken.builder().id(1L).usuario(u).expiresAt(LocalDateTime.now().plusMinutes(20)).build();
        when(tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(),any())).thenReturn(Optional.of(token));
        when(users.findLockedById(2L)).thenReturn(Optional.of(u));
        when(encoder.encode("Clave123")).thenReturn("hash-personal");
        var service=new PasswordRecoveryService(users,tokens,encoder,mock(NotificationEmailService.class));
        service.restablecer("enlace-ficticio", "Clave123");
        assertEquals("hash-personal",u.getPassword()); assertFalse(u.getCambioPasswordPendiente());
        assertEquals(4L,u.getVersionAcceso()); assertNotNull(token.getUsedAt());
        verify(tokens).deleteAllByUsuario_IdAndUsedAtIsNull(2L);
        when(tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(),any())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.restablecer("enlace-ficticio","Clave123"));
        assertThrows(ResponseStatusException.class, () -> service.restablecer("enlace-ficticio","Clave12"));
    }
}
