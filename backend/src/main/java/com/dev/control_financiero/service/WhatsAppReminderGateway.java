package com.dev.control_financiero.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class WhatsAppReminderGateway {
    private final ObjectProvider<RestClient.Builder> builders;
    @Value("${app.whatsapp.enabled:false}") private boolean enabled;
    @Value("${app.whatsapp.graph-version:}") private String graphVersion = "";
    @Value("${app.whatsapp.phone-number-id:}") private String phoneId = "";
    @Value("${app.whatsapp.access-token:}") private String token = "";
    @Value("${app.whatsapp.template:calendar_reminder}") private String template = "calendar_reminder";
    @Value("${app.whatsapp.template-language:es}") private String language = "es";

    public record Status(boolean enabled, boolean configured, String mode, List<String> missing) {}
    public Status status() {
        List<String> missing = new ArrayList<>();
        if (!graphVersion.matches("v[0-9]+\\.[0-9]+")) missing.add("Versión de API");
        if (!phoneId.matches("[0-9]+")) missing.add("Identificador del número emisor");
        if (token.isBlank()) missing.add("Credencial de acceso");
        if (!template.matches("[a-z0-9_]+")) missing.add("Nombre de plantilla");
        if (!language.matches("[a-z]{2}(?:_[A-Z]{2})?")) missing.add("Idioma de plantilla");
        boolean configured = missing.isEmpty();
        return new Status(enabled, configured, !enabled ? "PREPARACION" : configured ? "ACTIVO" : "SIN_CONFIGURAR", List.copyOf(missing));
    }
    public boolean send(String phone, String title, LocalDateTime occurrence) {
        Status state = status();
        if (!state.enabled() || !state.configured()) return false;
        var body = WhatsAppReminderMessage.payload(phone, title, occurrence, template, language);
        RestClient.Builder builder = builders.getIfAvailable();
        if (builder == null) return false;
        JsonNode result = builder.build().post()
            .uri("https://graph.facebook.com/{version}/{phoneId}/messages", graphVersion, phoneId)
            .headers(headers -> headers.setBearerAuth(token)).body(body).retrieve().body(JsonNode.class);
        // Acceptance by Meta is not proof of delivery to the device.
        if (result == null || result.path("messages").path(0).path("id").asText().isBlank()) throw new IllegalStateException("El proveedor no confirmó la aceptación del mensaje.");
        return true;
    }
}
