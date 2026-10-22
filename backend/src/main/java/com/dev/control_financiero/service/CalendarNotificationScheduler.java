package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.EntregaRecordatorio;
import com.dev.control_financiero.entity.EventoCalendario;
import com.dev.control_financiero.repository.EntregaRecordatorioRepository;
import com.dev.control_financiero.repository.EventoCalendarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarNotificationScheduler {
    private static final ZoneId ZONE = ZoneId.of("America/El_Salvador");
    private static final List<String> RECURRENCES = List.of("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY");
    private final EventoCalendarioRepository events;
    private final EntregaRecordatorioRepository deliveries;
    private final NotificationEmailService email;
    private final WhatsAppReminderGateway whatsapp;
    @Value("${app.notifications.delivery-enabled:true}") private boolean deliveryEnabled = true;

    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:60000}")
    public void deliverDueReminders() {
        if (!deliveryEnabled) return;
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (EventoCalendario event : events.findReadyForReminderDelivery()) {
            try {
                LocalDateTime occurrence = nextOccurrence(event, now);
                if (occurrence == null || occurrence.isBefore(now.minusMinutes(10))) continue;
                LocalDateTime remindAt = occurrence.minusMinutes(event.getMinutosRecordatorio());
                if (remindAt.isAfter(now)) continue;
                if (Boolean.TRUE.equals(event.getNotificarCorreo())) sendEmail(event, occurrence);
                if (Boolean.TRUE.equals(event.getNotificarWhatsapp())) sendWhatsApp(event, occurrence);
            } catch (Exception e) {
                log.warn("No se pudo procesar un recordatorio del calendario: {}", event.getId(), e);
            }
        }
    }

    private void sendEmail(EventoCalendario event, LocalDateTime occurrence) {
        if (deliveries.existsByEventoIdAndOcurrenciaAndCanal(event.getId(), occurrence, "EMAIL")) return;
        String to = event.getUsuario().getCorreo();
        if (!email.configured() || blank(to)) {
            log.warn("No se envió correo para el evento {}: envío no configurado o destino no válido.",event.getId());
            return;
        }
        try {
            email.send(to,"Recordatorio: " + event.getTitulo(),emailBody(event, occurrence));
            record(event, occurrence, "EMAIL");
            log.info("Recordatorio por correo enviado para el evento {} a {}.", event.getId(), maskEmail(to));
        } catch (Exception e) {
            log.warn("No se pudo enviar el recordatorio por correo para el evento {}. {}",
                    event.getId(), e.getClass().getSimpleName());
        }
    }

    private void sendWhatsApp(EventoCalendario event, LocalDateTime occurrence) {
        if (deliveries.existsByEventoIdAndOcurrenciaAndCanal(event.getId(), occurrence, "WHATSAPP")) return;
        if (event.getUsuario() == null || blank(event.getUsuario().getTelefono())) return;
        try {
            if (whatsapp.send(event.getUsuario().getTelefono(), event.getTitulo(), occurrence)) record(event, occurrence, "WHATSAPP");
        } catch (Exception e) {
            log.warn("No se pudo enviar el recordatorio por WhatsApp para el evento {}. {}", event.getId(), e.getClass().getSimpleName());
        }
    }

    private String emailBody(EventoCalendario event, LocalDateTime occurrence) {
        return "Hola " + event.getUsuario().getNombre() + ",\n\n"
                + "Este es un recordatorio de tu actividad: " + event.getTitulo() + ".\n"
                + "Fecha: " + occurrence.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n"
                + (event.getDescripcion() == null || event.getDescripcion().isBlank() ? "" : "Notas: " + event.getDescripcion() + "\n")
                + "\nCOFI";
    }

    private void record(EventoCalendario event, LocalDateTime occurrence, String channel) {
        deliveries.save(EntregaRecordatorio.builder().eventoId(event.getId()).ocurrencia(occurrence)
                .canal(channel).enviadoEn(LocalDateTime.now(ZONE)).build());
    }

    private LocalDateTime nextOccurrence(EventoCalendario event, LocalDateTime now) {
        if (event.getFecha() == null || event.getHora() == null) return null;
        LocalDateTime base = LocalDateTime.of(event.getFecha(), event.getHora());
        if ("NONE".equals(event.getRecurrencia())) return base.isBefore(now.minusMinutes(10)) ? null : base;
        if (!RECURRENCES.contains(event.getRecurrencia())) return null;
        LocalDateTime occurrence = base;
        int guard = 0;
        while (occurrence.isBefore(now.minusMinutes(10)) && guard++ < 5000) {
                occurrence = switch (event.getRecurrencia()) {
                case "DAILY" -> occurrence.plusDays(1);
                case "WEEKLY" -> occurrence.plusWeeks(1);
                case "MONTHLY" -> nextMonthOnAnchorDay(occurrence, event.getFecha().getDayOfMonth());
                case "YEARLY" -> nextYearOnAnchorDay(occurrence, event.getFecha().getMonthValue(), event.getFecha().getDayOfMonth());
                default -> occurrence;
            };
        }
        return guard >= 5000 ? null : occurrence;
    }

    private LocalDateTime nextMonthOnAnchorDay(LocalDateTime occurrence, int day) {
        LocalDateTime next = occurrence.withDayOfMonth(1).plusMonths(1);
        int lastDay = next.toLocalDate().lengthOfMonth();
        return next.withDayOfMonth(Math.min(day, lastDay));
    }

    private LocalDateTime nextYearOnAnchorDay(LocalDateTime occurrence, int month, int day) {
        LocalDateTime next = occurrence.withDayOfMonth(1).plusYears(1).withMonth(month);
        return next.withDayOfMonth(Math.min(day, next.toLocalDate().lengthOfMonth()));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String maskEmail(String email) {
        if (blank(email) || !email.contains("@")) return "(correo no válido)";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.length() < 3 ? "*" : local.substring(0, 2) + "***") + email.substring(at);
    }
}
