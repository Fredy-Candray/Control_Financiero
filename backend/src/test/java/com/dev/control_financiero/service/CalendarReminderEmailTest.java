package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.EventoCalendario;
import com.dev.control_financiero.entity.Usuario;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalendarReminderEmailTest {
    @Test void escapesUserContentAndUsesActualOccurrence() {
        var event = EventoCalendario.builder().titulo("Reunión <script>alert(1)</script>")
                .descripcion("Notas & pendientes\nSegunda línea")
                .usuario(Usuario.builder().nombre("Ana <img src=x>").build()).build();
        String html = CalendarReminderEmail.html(event, LocalDateTime.of(2026, 11, 3, 9, 5));
        assertTrue(html.contains("3 de noviembre de 2026"));
        assertTrue(html.contains("09:05"));
        assertTrue(html.contains("Notas &amp; pendientes"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("https://"));
    }

    @Test void gmailMimeContainsPlainTextAndHtml() throws Exception {
        String raw = GmailNotificationSender.encode("sender@example.com", "reader@example.com",
                "COFI · Recordatorio", "Actividad de prueba", "<h1>Actividad de prueba</h1>");
        var mime = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
        assertTrue(mime.isMimeType("multipart/*"));
        var root = (Multipart) mime.getContent();
        var alternative = (Multipart) root.getBodyPart(0).getContent();
        assertTrue(alternative.getBodyPart(0).isMimeType("text/plain"));
        assertTrue(alternative.getBodyPart(1).isMimeType("text/html"));
        assertEquals("Actividad de prueba", alternative.getBodyPart(0).getContent());
    }
}
