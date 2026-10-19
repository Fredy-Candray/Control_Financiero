package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordRecoveryEmailTest {
    @Test void httpsProviderSendsCloudResetLinkWithoutChangingPassword() {
        var users=mock(UsuarioRepository.class);var tokens=mock(PasswordResetTokenRepository.class);
        var email=mock(NotificationEmailService.class);when(email.configured()).thenReturn(true);
        var user=Usuario.builder().id(1L).nombre("Prueba").correo("reader@example.com").activo(true).build();
        when(users.findByCorreoIgnoreCase("reader@example.com")).thenReturn(Optional.of(user));
        var service=new PasswordRecoveryService(users,tokens,mock(PasswordEncoder.class),email);
        ReflectionTestUtils.setField(service,"resetUrl","https://cofi.example/reset-password");
        service.solicitar("reader@example.com");
        verify(email).send(eq("reader@example.com"),eq("Restablecer contraseña - COFI"),contains("https://cofi.example/reset-password?token="));
        verify(tokens).save(argThat(t->t.getTokenHash().matches("[0-9a-f]{64}")));
        verify(users,never()).save(any());
    }
    @Test void failedEmailDoesNotLeaveAnUnsentRecoveryToken() {
        var users=mock(UsuarioRepository.class);var tokens=mock(PasswordResetTokenRepository.class);
        var email=mock(NotificationEmailService.class);when(email.configured()).thenReturn(true);
        when(users.findByCorreoIgnoreCase("reader@example.com")).thenReturn(Optional.of(Usuario.builder().id(1L).nombre("Prueba").correo("reader@example.com").activo(true).build()));
        doThrow(new IllegalStateException()).when(email).send(anyString(),anyString(),anyString());
        var service=new PasswordRecoveryService(users,tokens,mock(PasswordEncoder.class),email);
        ReflectionTestUtils.setField(service,"resetUrl","https://cofi.example/reset-password");
        service.solicitar("reader@example.com");
        verify(tokens,times(2)).deleteAllByUsuario_IdAndUsedAtIsNull(1L);verify(users,never()).save(any());
    }
}
