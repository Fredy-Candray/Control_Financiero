package com.dev.control_financiero.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.ByteArrayInputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GmailNotificationSenderTest {
    @Test void preservesAccentsAndPlainText() throws Exception {
        String raw=GmailNotificationSender.encode("sender@example.com","reader@example.com","Recordatorio: reunión","Hola, mañana a las 10:00.\nCOFI");
        var mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
        assertEquals("Recordatorio: reunión",mime.getSubject());
        assertTrue(mime.getContent().toString().contains("mañana"));
        assertEquals("reader@example.com",mime.getAllRecipients()[0].toString());
    }
    @Test void rejectsHeaderInjection() {
        assertThrows(IllegalArgumentException.class,()->GmailNotificationSender.encode("a@example.com","b@example.com","Title\r\nBcc: victim@example.com","Body"));
    }
    @Test void sendsOnlyViaGmailHttpsWithDedicatedToken() {
        var oauth=mock(GmailBacClient.class);var cipher=mock(GmailTokenCipher.class);
        when(oauth.configured()).thenReturn(true);when(cipher.configured()).thenReturn(true);
        when(cipher.decrypt("encrypted-sender-token")).thenReturn("sender-refresh");
        when(oauth.accessToken("sender-refresh")).thenReturn("sender-access");
        var sender=new GmailNotificationSender(oauth,cipher,"encrypted-sender-token","sender@example.com");
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(sender,"http",builder.build());
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer sender-access"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"raw\"")))
                .andRespond(withSuccess("{\"id\":\"test\"}",MediaType.APPLICATION_JSON));
        sender.send("reader@example.com","Prueba","Solo datos ficticios");
        server.verify();verify(oauth).accessToken("sender-refresh");
    }
    @Test void neverSendsWithoutSeparatePermission() {
        var sender=new GmailNotificationSender(mock(GmailBacClient.class),mock(GmailTokenCipher.class),"","sender@example.com");
        assertFalse(sender.configured());assertThrows(IllegalStateException.class,()->sender.send("a@example.com","Title","Body"));
    }
}
