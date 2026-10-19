package com.dev.control_financiero.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Same notification behavior with SMTP locally or Gmail HTTPS on Railway. */
@Service
public class NotificationEmailService {
    private final ObjectProvider<JavaMailSender> smtp;
    private final GmailNotificationSender gmail;
    private final String provider,host,from;
    public NotificationEmailService(ObjectProvider<JavaMailSender> smtp, GmailNotificationSender gmail,
            @Value("${app.email.provider:smtp}") String provider,
            @Value("${spring.mail.host:}") String host,
            @Value("${app.notifications.email-from:}") String from) {
        this.smtp=smtp;this.gmail=gmail;this.provider=provider;this.host=host;this.from=from;
    }
    public boolean configured() {
        if("gmail-api".equals(provider))return gmail.configured();
        return "smtp".equals(provider)&&!host.isBlank()&&!from.isBlank()&&smtp.getIfAvailable()!=null;
    }
    public void send(String to,String subject,String body) {
        if(!configured())throw new IllegalStateException("El envío de correo no está configurado.");
        if("gmail-api".equals(provider)){gmail.send(to,subject,body);return;}
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);
        message.setSubject(subject);message.setText(body);smtp.getObject().send(message);
    }
}
