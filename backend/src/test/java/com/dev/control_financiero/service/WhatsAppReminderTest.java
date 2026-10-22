package com.dev.control_financiero.service;

import com.dev.control_financiero.controller.WhatsAppReminderController;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WhatsAppReminderTest {
    private final LocalDateTime occurrence=LocalDateTime.of(2026,11,22,8,30);
    @Test void normalizesPhonesAndRejectsInvalidDestinations() {
        assertEquals("+50370000000",WhatsAppReminderMessage.normalizePhone("7000-0000"));
        assertEquals("+525512345678",WhatsAppReminderMessage.normalizePhone("+52 (55) 1234-5678"));
        for(String value:List.of("", "123", "+000000000", "phone70000000", "50370000000", "+50370000000ext5"))assertThrows(IllegalArgumentException.class,()->WhatsAppReminderMessage.normalizePhone(value));
    }
    @Test void recurringPayloadUsesActualOccurrenceAndStableParameterOrder() {
        var body=WhatsAppReminderMessage.payload("70000000","Pagar tarjeta",occurrence,"calendar_reminder","es");
        assertEquals("50370000000",body.get("to"));
        var template=(Map<?,?>)body.get("template");
        var component=(Map<?,?>)((List<?>)template.get("components")).get(0);
        var parameters=(List<?>)component.get("parameters");
        assertEquals(List.of(Map.of("type","text","text","Pagar tarjeta"),Map.of("type","text","text","22/11/2026"),Map.of("type","text","text","08:30")),parameters);
    }
    @Test void disabledOrIncompleteGatewayNeverRequestsAnHttpClient() {
        ObjectProvider<RestClient.Builder> provider=mock(ObjectProvider.class);
        var gateway=new WhatsAppReminderGateway(provider);
        ReflectionTestUtils.setField(gateway,"token","private-test-token");
        assertFalse(gateway.send("70000000","Prueba",occurrence));
        ReflectionTestUtils.setField(gateway,"enabled",true);
        assertFalse(gateway.send("70000000","Prueba",occurrence));
        assertFalse(gateway.status().toString().contains("private-test-token"));
        verifyNoInteractions(provider);
    }
    @Test void enabledGatewayUsesOnlyMockTransportAndRequiresAcknowledgement() {
        ObjectProvider<RestClient.Builder> provider=mock(ObjectProvider.class);
        RestClient.Builder builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        when(provider.getIfAvailable()).thenReturn(builder);
        var gateway=new WhatsAppReminderGateway(provider);
        ReflectionTestUtils.setField(gateway,"enabled",true);
        ReflectionTestUtils.setField(gateway,"graphVersion","v99.0");
        ReflectionTestUtils.setField(gateway,"phoneId","12345");
        ReflectionTestUtils.setField(gateway,"token","fake-local-test-token");
        server.expect(requestTo("https://graph.facebook.com/v99.0/12345/messages"))
            .andExpect(header("Authorization","Bearer fake-local-test-token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("22/11/2026")))
            .andRespond(withSuccess("{\"messages\":[{\"id\":\"local-mocked-ack\"}]}",MediaType.APPLICATION_JSON));
        assertTrue(gateway.send("70000000","Actividad",occurrence));server.verify();
        server.reset();
        server.expect(requestTo("https://graph.facebook.com/v99.0/12345/messages"))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class,()->gateway.send("70000000","Actividad",occurrence));server.verify();
    }
    @Test void localPreviewRequiresSessionAndCannotInvokeDelivery() {
        var gateway=mock(WhatsAppReminderGateway.class);
        var controller=new WhatsAppReminderController(gateway);
        var request=new MockHttpServletRequest();
        var body=new WhatsAppReminderController.PreviewRequest("Prueba",occurrence.toLocalDate(),occurrence.toLocalTime(),"70000000");
        assertThrows(ResponseStatusException.class,()->controller.preview(body,request));
        request.getSession().setAttribute("userId",1L);
        var preview=controller.preview(body,request);
        assertFalse(preview.sent());assertEquals("+50370000000",preview.phone());assertTrue(preview.message().contains("22/11/2026"));
        verifyNoInteractions(gateway);
    }
    @Test void disabledPreparationSchedulerDoesNotReadOrWriteDatabase() {
        var events=mock(EventoCalendarioRepository.class);var deliveries=mock(EntregaRecordatorioRepository.class);var gateway=mock(WhatsAppReminderGateway.class);var email=mock(NotificationEmailService.class);
        var scheduler=new CalendarNotificationScheduler(events,deliveries,email,gateway);
        ReflectionTestUtils.setField(scheduler,"deliveryEnabled",false);scheduler.deliverDueReminders();
        verifyNoInteractions(events,deliveries,gateway,email);
    }
    @Test void recordsOnlyAcceptedMessagesAndSkipsDuplicates() {
        var deliveries=mock(EntregaRecordatorioRepository.class);var gateway=mock(WhatsAppReminderGateway.class);
        var scheduler=new CalendarNotificationScheduler(mock(EventoCalendarioRepository.class),deliveries,mock(NotificationEmailService.class),gateway);
        var event=EventoCalendario.builder().id("local-test").titulo("Actividad").fecha(LocalDate.of(2026,10,22)).hora(LocalTime.of(8,30)).usuario(Usuario.builder().telefono("70000000").build()).build();
        ReflectionTestUtils.invokeMethod(scheduler,"sendWhatsApp",event,occurrence);verify(deliveries,never()).save(any());
        when(gateway.send("70000000","Actividad",occurrence)).thenThrow(new IllegalStateException("provider error"));
        ReflectionTestUtils.invokeMethod(scheduler,"sendWhatsApp",event,occurrence);verify(deliveries,never()).save(any());
        doReturn(true).when(gateway).send("70000000","Actividad",occurrence);
        ReflectionTestUtils.invokeMethod(scheduler,"sendWhatsApp",event,occurrence);
        verify(deliveries).save(argThat(d->d.getOcurrencia().equals(occurrence)&&"WHATSAPP".equals(d.getCanal())));
        when(deliveries.existsByEventoIdAndOcurrenciaAndCanal("local-test",occurrence,"WHATSAPP")).thenReturn(true);
        ReflectionTestUtils.invokeMethod(scheduler,"sendWhatsApp",event,occurrence);
        verify(gateway,times(3)).send("70000000","Actividad",occurrence);
    }
}
