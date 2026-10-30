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
    public GmailNotificationSender.PermissionStatus permissionStatus() {
        if("gmail-api".equals(provider))return gmail.permissionStatus();
        return new GmailNotificationSender.PermissionStatus(configured()?"CONFIGURED":"NOT_CONFIGURED",null);
    }
    public void send(String to,String subject,String body) {
        if(!configured())throw new IllegalStateException("El envío de correo no está configurado.");
        if("gmail-api".equals(provider)){gmail.send(to,subject,body);return;}
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);
        message.setSubject(subject);message.setText(body);smtp.getObject().send(message);
    }
    public void sendHtml(String to, String subject, String text, String html) {
        if (!configured()) throw new IllegalStateException("El envío de correo no está configurado.");
        if ("gmail-api".equals(provider)) { gmail.sendHtml(to, subject, text, html); return; }
        try {
            var sender = smtp.getObject();
            var message = sender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from); helper.setTo(to); helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (jakarta.mail.MessagingException error) {
            throw new IllegalStateException("No se pudo preparar el correo de recordatorio.");
        }
    }
}
