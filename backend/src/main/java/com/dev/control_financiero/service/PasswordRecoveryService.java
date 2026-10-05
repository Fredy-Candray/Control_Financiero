package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.PasswordResetToken;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.PasswordResetTokenRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private static final ZoneId ZONE = ZoneId.of("America/El_Salvador");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_RESPONSE = "Si el correo está registrado y activo, recibirás instrucciones para restablecer tu contraseña.";

    private final UsuarioRepository usuarios;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.notifications.email-from:}") private String emailFrom;
    @Value("${spring.mail.host:}") private String mailHost;
    @Value("${app.password-reset.frontend-url:http://localhost:4200/reset-password}") private String resetUrl;

    @Transactional
    public String solicitar(String correo) {
        Optional<Usuario> encontrado = usuarios.findByCorreoIgnoreCase(correo.trim());
        if (encontrado.isEmpty() || !Boolean.TRUE.equals(encontrado.get().getActivo())) return GENERIC_RESPONSE;

        Usuario usuario = encontrado.get();
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || blank(mailHost) || blank(emailFrom)) {
            log.warn("No se envió enlace de recuperación: falta la configuración SMTP o MAIL_FROM.");
            return GENERIC_RESPONSE;
        }

        tokens.deleteAllByUsuario_IdAndUsedAtIsNull(usuario.getId());
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(PasswordResetToken.builder()
                .tokenHash(hash(token))
                .usuario(usuario)
                .expiresAt(LocalDateTime.now(ZONE).plusMinutes(30))
                .build());

        String separator = resetUrl.contains("?") ? "&" : "?";
        String link = resetUrl + separator + "token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom);
        message.setTo(usuario.getCorreo());
        message.setSubject("Restablecer contraseña - Control Financiero");
        message.setText("Hola " + usuario.getNombre() + ",\n\n"
                + "Recibimos una solicitud para cambiar la contraseña de tu cuenta. "
                + "Abre este enlace para elegir una nueva contraseña (vence en 30 minutos):\n\n"
                + link + "\n\nSi no solicitaste este cambio, ignora este correo.");
        try {
            sender.send(message);
            log.info("Se envió correo de recuperación de contraseña a un usuario registrado.");
        } catch (Exception ex) {
            tokens.deleteAllByUsuario_IdAndUsedAtIsNull(usuario.getId());
            log.error("No se pudo enviar el correo de recuperación: {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
        return GENERIC_RESPONSE;
    }

    @Transactional
    public void restablecer(String rawToken, String nuevaPassword) {
        PasswordResetToken token = tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                        hash(rawToken), LocalDateTime.now(ZONE))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El enlace ya venció o no es válido. Solicita uno nuevo."));
        Usuario usuario = token.getUsuario();
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta no está activa.");
        }
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        token.setUsedAt(LocalDateTime.now(ZONE));
        usuarios.save(usuario);
        tokens.save(token);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
