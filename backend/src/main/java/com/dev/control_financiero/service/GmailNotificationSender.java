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

/** Dedicated send-only permission; never reuses the bank-reading refresh token. */
@Component
public class GmailNotificationSender {
    private final GmailBacClient oauth;
    private final GmailTokenCipher cipher;
    private final String encryptedToken,from;
    private final RestClient http;
    public GmailNotificationSender(GmailBacClient oauth,GmailTokenCipher cipher,
            @Value("${app.email.gmail-refresh-token:}") String encryptedToken,
            @Value("${app.notifications.email-from:}") String from) {
        this.oauth=oauth;this.cipher=cipher;this.encryptedToken=encryptedToken;this.from=from;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(10000);factory.setReadTimeout(20000);
        http=RestClient.builder().requestFactory(factory).build();
    }
    public boolean configured(){return oauth.configured()&&cipher.configured()&&!encryptedToken.isBlank()&&!from.isBlank();}
    public void send(String to,String subject,String body){
        if(!configured())throw new IllegalStateException("Falta el permiso de envío de Gmail.");
        String raw=encode(from,to,subject,body);
        try {
            String access=oauth.accessToken(cipher.decrypt(encryptedToken));
            http.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(h->h.setBearerAuth(access)).body(Map.of("raw",raw)).retrieve().toBodilessEntity();
        }catch(Exception error){
            // Provider responses may contain message contents, tokens or addresses.
            throw new IllegalStateException("No se pudo enviar el correo mediante Gmail.");
        }
    }
    static String encode(String from,String to,String subject,String body){
        try {
            if(subject==null||subject.contains("\r")||subject.contains("\n"))throw new IllegalArgumentException();
            var sender=new InternetAddress(from,true);sender.validate();
            var recipient=new InternetAddress(to,true);recipient.validate();
            var mime=new MimeMessage(Session.getInstance(new Properties()));
            var helper=new MimeMessageHelper(mime,"UTF-8");helper.setFrom(sender);helper.setTo(recipient);
            helper.setSubject(subject);helper.setText(body,false);mime.saveChanges();
            var bytes=new ByteArrayOutputStream();mime.writeTo(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        }catch(Exception error){throw new IllegalArgumentException("Los datos del correo no son válidos.");}
    }
}
