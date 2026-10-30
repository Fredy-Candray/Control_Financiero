package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CalendarEmailDeliveryTest {
    @Test void sendsWithoutSmtpAndRecordsDeliveryOnlyOnce() {
        var deliveries=mock(EntregaRecordatorioRepository.class);var email=mock(NotificationEmailService.class);
        when(email.configured()).thenReturn(true);
        var service=new CalendarNotificationScheduler(mock(EventoCalendarioRepository.class),deliveries,email,mock(WhatsAppReminderGateway.class));
        var event=EventoCalendario.builder().id("test-event").titulo("Reunión").fecha(LocalDate.of(2026,10,20))
                .hora(LocalTime.of(9,0)).usuario(Usuario.builder().nombre("Prueba").correo("reader@example.com").build()).build();
        var occurrence=LocalDateTime.of(2026,10,20,9,0);
        ReflectionTestUtils.invokeMethod(service,"sendEmail",event,occurrence);
        verify(email).sendHtml(eq("reader@example.com"),eq("COFI · Recordatorio: Reunión"),contains("20/10/2026 09:00"),contains("RECORDATORIO DE TU CALENDARIO"));
        verify(deliveries).save(argThat(d->"EMAIL".equals(d.getCanal())&&"test-event".equals(d.getEventoId())));
        when(deliveries.existsByEventoIdAndOcurrenciaAndCanal("test-event",occurrence,"EMAIL")).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service,"sendEmail",event,occurrence);
        verify(email,times(1)).sendHtml(anyString(),anyString(),anyString(),anyString());
    }
    @Test void failedDeliveryRemainsEligibleForRetry() {
        var deliveries=mock(EntregaRecordatorioRepository.class);var email=mock(NotificationEmailService.class);
        when(email.configured()).thenReturn(true);doThrow(new IllegalStateException()).when(email).sendHtml(anyString(),anyString(),anyString(),anyString());
        var service=new CalendarNotificationScheduler(mock(EventoCalendarioRepository.class),deliveries,email,mock(WhatsAppReminderGateway.class));
        var event=EventoCalendario.builder().id("test-event").titulo("Actividad").usuario(Usuario.builder().nombre("Prueba").correo("reader@example.com").build()).build();
        ReflectionTestUtils.invokeMethod(service,"sendEmail",event,LocalDateTime.of(2026,10,20,9,0));
        verify(deliveries,never()).save(any());
    }
}
