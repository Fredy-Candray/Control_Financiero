package com.dev.control_financiero.service;

import com.dev.control_financiero.controller.EmailConnectionStatusController;
import com.dev.control_financiero.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GmailPermissionHealthTest {
    @Test void detectsRevocationWithoutSendingAndDoesNotRepeatedlyRequestConsent() {
        var oauth=mock(GmailBacClient.class);var cipher=mock(GmailTokenCipher.class);
        when(oauth.configured()).thenReturn(true);when(cipher.configured()).thenReturn(true);
        when(cipher.decrypt("encrypted")).thenReturn("refresh");
        when(oauth.accessToken("refresh")).thenThrow(new GmailAuthorizationException());
        var sender=new GmailNotificationSender(oauth,cipher,"encrypted","sender@example.com");
        ReflectionTestUtils.setField(sender,"provider","gmail-api");
        var transport=mock(RestClient.class);ReflectionTestUtils.setField(sender,"http",transport);
        sender.checkPermission();sender.checkPermission();
        assertEquals("REAUTH_REQUIRED",sender.permissionStatus().state());
        assertNotNull(sender.permissionStatus().checkedAt());
        verify(oauth,times(1)).accessToken("refresh");verifyNoInteractions(transport);
    }
    @Test void connectionFailureDoesNotRequireReauthorizationAndCanRecover() {
        var oauth=mock(GmailBacClient.class);var cipher=mock(GmailTokenCipher.class);
        when(oauth.configured()).thenReturn(true);when(cipher.configured()).thenReturn(true);
        when(cipher.decrypt("encrypted")).thenReturn("refresh");
        when(oauth.accessToken("refresh")).thenThrow(new ResourceAccessException("offline")).thenReturn("access");
        var sender=new GmailNotificationSender(oauth,cipher,"encrypted","sender@example.com");
        ReflectionTestUtils.setField(sender,"provider","gmail-api");
        sender.checkPermission();assertEquals("TEMPORARY_ERROR",sender.permissionStatus().state());
        sender.checkPermission();assertEquals("CONNECTED",sender.permissionStatus().state());
    }
    @Test void preparationModeNeverContactsGoogle() {
        var oauth=mock(GmailBacClient.class);var cipher=mock(GmailTokenCipher.class);
        var sender=new GmailNotificationSender(oauth,cipher,"encrypted","sender@example.com");
        ReflectionTestUtils.setField(sender,"provider","gmail-api");ReflectionTestUtils.setField(sender,"checksEnabled",false);
        sender.checkPermission();verifyNoInteractions(oauth,cipher);
    }
    @Test void invalidGrantBecomesSafeTypedFailureButBadClientDoesNot() {
        var client=new GmailBacClient("client","secret","http://localhost/callback");
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client,"http",builder.build());
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\",\"error_description\":\"private value\"}"));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));
        var revoked=assertThrows(GmailAuthorizationException.class,()->client.accessToken("refresh"));
        assertFalse(revoked.getMessage().contains("private"));
        var config=assertThrows(IllegalStateException.class,()->client.accessToken("refresh"));
        assertFalse(GmailPermissionErrors.needsReauthorization(config));server.verify();
    }
    @Test void quotaAndInternetFailuresAreNotExpiredPermissions() {
        var quota=HttpClientErrorException.create(HttpStatus.FORBIDDEN,"quota",HttpHeaders.EMPTY,
                "{\"error\":{\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
        assertFalse(GmailPermissionErrors.needsReauthorization(quota));
        var scope=HttpClientErrorException.create(HttpStatus.FORBIDDEN,"scope",HttpHeaders.EMPTY,
                "{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
        assertTrue(GmailPermissionErrors.needsReauthorization(scope));
        assertFalse(GmailPermissionErrors.needsReauthorization(new ResourceAccessException("offline")));
    }
    @Test void statusRequiresAnAuthenticatedSession() {
        var email=mock(NotificationEmailService.class);var users=mock(UsuarioRepository.class);
        var controller=new EmailConnectionStatusController(email,users);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.status(new MockHttpServletRequest()));
        verifyNoInteractions(email,users);
    }
    @Test void readerStatusIsScopedToSessionAndDoesNotExposeInternalErrorCode() {
        var connections=mock(com.dev.control_financiero.repository.GmailConnectionRepository.class);
        var connection=new com.dev.control_financiero.entity.GmailConnection();
        connection.setUsuarioId(7L);connection.setRefreshTokenEncrypted("encrypted");
        connection.setLastError(GmailAuthorizationException.PREFIX+"Reconecta Gmail.");
        when(connections.findByUsuarioId(7L)).thenReturn(java.util.Optional.of(connection));
        var controller=new com.dev.control_financiero.controller.GmailBacController(mock(GmailBacClient.class),mock(GmailTokenCipher.class),
                connections,mock(com.dev.control_financiero.repository.BankEmailImportRepository.class),mock(GmailConnectionService.class),mock(GmailBacScheduler.class));
        var request=new MockHttpServletRequest();request.getSession().setAttribute("userId",7L);
        var status=controller.status(request);
        assertTrue(status.reauthorizationRequired());assertEquals("Reconecta Gmail.",status.lastError());
        connection.setLastError(null);connection.setEnabled(true);
        ReflectionTestUtils.setField(controller,"importEnabled",false);
        assertFalse(controller.status(request).enabled());
        assertTrue(controller.status(request).lastError().contains("pausada"));
        connection.setRefreshTokenEncrypted(null);
        assertFalse(controller.status(request).reauthorizationRequired());
        verify(connections,times(4)).findByUsuarioId(7L);
    }
}
