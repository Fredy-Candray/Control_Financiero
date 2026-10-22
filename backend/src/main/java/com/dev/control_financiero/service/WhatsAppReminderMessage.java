package com.dev.control_financiero.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Pure local formatting, shared by preview and delivery. Never performs I/O. */
public final class WhatsAppReminderMessage {
    private WhatsAppReminderMessage() {}
    public static String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim().replaceAll("[\\s()-]", "");
        if (value.matches("[0-9]{8}")) value = "+503" + value;
        if (!value.matches("\\+[1-9][0-9]{7,14}")) throw new IllegalArgumentException("Usa 8 dígitos locales o un número internacional con + y código de país.");
        return value;
    }
    public static List<String> parameters(String title, LocalDateTime occurrence) {
        if (title == null || title.isBlank() || title.length() > 120 || occurrence == null) throw new IllegalArgumentException("Completa título, fecha y hora para la vista previa.");
        return List.of(title.trim(), occurrence.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), occurrence.format(DateTimeFormatter.ofPattern("HH:mm")));
    }
    public static String preview(String title, LocalDateTime occurrence) {
        var values = parameters(title, occurrence);
        return "COFI · Recordatorio\nActividad: " + values.get(0) + "\nFecha: " + values.get(1) + "\nHora: " + values.get(2) + " (El Salvador)";
    }
    public static Map<String,Object> payload(String phone, String title, LocalDateTime occurrence, String template, String language) {
        var values = parameters(title, occurrence);
        return Map.of("messaging_product", "whatsapp", "to", normalizePhone(phone).substring(1), "type", "template",
            "template", Map.of("name", template, "language", Map.of("code", language),
                "components", List.of(Map.of("type", "body", "parameters", values.stream().map(value -> Map.of("type", "text", "text", value)).toList()))));
    }
}
