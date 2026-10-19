package com.dev.control_financiero.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationEmailServiceTest {
    @Test void localConfigurationStillUsesSmtp() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);var smtp=mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(smtp);when(provider.getObject()).thenReturn(smtp);
        var gmail=mock(GmailNotificationSender.class);
        var service=new NotificationEmailService(provider,gmail,"smtp","smtp.example.com","sender@example.com");
        service.send("reader@example.com","Recuperación","Enlace de prueba");
        verify(smtp).send(any(SimpleMailMessage.class));verifyNoInteractions(gmail);
    }
    @Test void gmailDoesNotFallBackToBlockedSmtp() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);var gmail=mock(GmailNotificationSender.class);
        when(gmail.configured()).thenReturn(true);
        var service=new NotificationEmailService(provider,gmail,"gmail-api","","sender@example.com");
        service.send("reader@example.com","Recordatorio","Prueba");
        verify(gmail).send("reader@example.com","Recordatorio","Prueba");verifyNoInteractions(provider);
    }
}
