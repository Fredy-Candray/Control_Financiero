package com.dev.control_financiero.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;

/** Dedicated send-only permission; never reuses the bank-reading refresh token. */
@Component
@org.springframework.context.annotation.Lazy(false)
public class GmailNotificationSender {
    private final GmailBacClient oauth;
    private final GmailTokenCipher cipher;
    private final String encryptedToken,from;
    private final RestClient http;
    public record PermissionStatus(String state, Instant checkedAt) {}
    private volatile PermissionStatus permission = new PermissionStatus("UNKNOWN",null);
    @Value("${app.email.provider:smtp}") private String provider="smtp";
    @Value("${app.notifications.delivery-enabled:true}") private boolean checksEnabled=true;
    public GmailNotificationSender(GmailBacClient oauth,GmailTokenCipher cipher,
            @Value("${app.email.gmail-refresh-token:}") String encryptedToken,
            @Value("${app.notifications.email-from:}") String from) {
        this.oauth=oauth;this.cipher=cipher;this.encryptedToken=encryptedToken;this.from=from;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(10000);factory.setReadTimeout(20000);
        http=RestClient.builder().requestFactory(factory).build();
    }
    public boolean configured(){return oauth.configured()&&cipher.configured()&&!encryptedToken.isBlank()&&!from.isBlank();}
    public PermissionStatus permissionStatus() {
        return configured()?permission:new PermissionStatus("NOT_CONFIGURED",null);
    }
    /** Refresh validation only: never sends a message or reads the mailbox. */
    @Scheduled(fixedDelayString="${app.email.health-poll-ms:900000}",initialDelayString="${app.email.health-initial-delay-ms:30000}")
    public synchronized void checkPermission() {
        if(!checksEnabled || !"gmail-api".equals(provider) || !configured() || "REAUTH_REQUIRED".equals(permission.state())) return;
        try {
            String access=oauth.accessToken(cipher.decrypt(encryptedToken));
            if(access==null || access.isBlank())throw new IllegalStateException();
            permission=new PermissionStatus("CONNECTED",Instant.now());
        } catch(Exception error) { failedPermission(error); }
    }
    private void failedPermission(Exception error) {
        permission=new PermissionStatus(GmailPermissionErrors.needsReauthorization(error)?"REAUTH_REQUIRED":"TEMPORARY_ERROR",Instant.now());
    }
    public void send(String to,String subject,String body){
        if(!configured())throw new IllegalStateException("Falta el permiso de envío de Gmail.");
        String raw=encode(from,to,subject,body);
        sendRaw(raw);
    }
    public void sendHtml(String to, String subject, String text, String html) {
        if (!configured()) throw new IllegalStateException("Falta el permiso de envío de Gmail.");
        sendRaw(encode(from, to, subject, text, html));
    }
    private void sendRaw(String raw) {
        try {
            String access=oauth.accessToken(cipher.decrypt(encryptedToken));
            http.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(h->h.setBearerAuth(access)).body(Map.of("raw",raw)).retrieve().toBodilessEntity();
            permission=new PermissionStatus("CONNECTED",Instant.now());
        }catch(Exception error){
            failedPermission(error);
            // Provider responses may contain message contents, tokens or addresses.
            if("REAUTH_REQUIRED".equals(permission.state()))throw new GmailAuthorizationException();
            throw new IllegalStateException("No se pudo enviar el correo mediante Gmail.");
        }
    }
    static String encode(String from,String to,String subject,String body){
        return encode(from, to, subject, body, null);
    }
    static String encode(String from,String to,String subject,String body,String html){
        try {
            if(subject==null||subject.contains("\r")||subject.contains("\n"))throw new IllegalArgumentException();
            var sender=new InternetAddress(from,true);sender.validate();
            var recipient=new InternetAddress(to,true);recipient.validate();
            var mime=new MimeMessage(Session.getInstance(new Properties()));
            var helper=new MimeMessageHelper(mime,html == null ? MimeMessageHelper.MULTIPART_MODE_NO : MimeMessageHelper.MULTIPART_MODE_MIXED,"UTF-8");helper.setFrom(sender);helper.setTo(recipient);
            helper.setSubject(subject);
            if (html == null) helper.setText(body,false); else helper.setText(body,html);
            mime.saveChanges();
            var bytes=new ByteArrayOutputStream();mime.writeTo(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        }catch(Exception error){throw new IllegalArgumentException("Los datos del correo no son válidos.");}
    }
}
