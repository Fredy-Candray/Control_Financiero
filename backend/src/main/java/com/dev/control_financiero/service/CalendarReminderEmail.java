package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.EventoCalendario;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/** Self-contained email: no remote images, tracking pixels or external fonts. */
final class CalendarReminderEmail {
    private CalendarReminderEmail() {}

    static String html(EventoCalendario event, LocalDateTime occurrence) {
        String date = occurrence.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-SV")));
        String time = occurrence.format(DateTimeFormatter.ofPattern("HH:mm"));
        String name = event.getUsuario() == null ? "" : escape(event.getUsuario().getNombre());
        String notes = event.getDescripcion() == null || event.getDescripcion().isBlank() ? "" :
                "<div style=\"margin-top:24px;padding:18px;background:#f2f7f5;border-radius:10px\">"
                + "<strong style=\"color:#104c3d\">Notas de tu actividad</strong>"
                + "<p style=\"margin:10px 0 0;white-space:pre-wrap;overflow-wrap:anywhere\">" + escape(event.getDescripcion()) + "</p></div>";
        return """
                <!doctype html><html lang="es"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#edf3f1;color:#213d35;font-family:Arial,Helvetica,sans-serif">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#edf3f1"><tr><td align="center" style="padding:28px 12px">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #dce8e2;border-radius:16px;overflow:hidden">
                <tr><td style="padding:28px;background:#082b50;color:#ffffff">
                <div style="font-size:30px;font-weight:700;letter-spacing:2px">COFI</div>
                <div style="margin-top:6px;font-size:12px;letter-spacing:1px;color:#9de4c9">CONTROL FINANCIERO</div></td></tr>
                <tr><td style="padding:28px">
                <div style="font-size:12px;font-weight:bold;letter-spacing:1px;color:#00845b">RECORDATORIO DE TU CALENDARIO</div>
                <p style="margin:22px 0 8px">Hola%s,</p>
                <p style="margin:0 0 22px;color:#526b62;line-height:1.6">Tienes una actividad programada. Estos son los detalles:</p>
                <h1 style="margin:0 0 24px;font-size:25px;line-height:1.35;color:#104c3d;overflow-wrap:anywhere">%s</h1>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-left:4px solid #00a877;background:#f2f7f5">
                <tr><td style="padding:18px 20px 8px;font-size:12px;color:#526b62">FECHA</td></tr>
                <tr><td style="padding:0 20px 16px;font-size:17px;font-weight:bold">%s</td></tr>
                <tr><td style="padding:0 20px 8px;font-size:12px;color:#526b62">HORA · EL SALVADOR</td></tr>
                <tr><td style="padding:0 20px 18px;font-size:25px;font-weight:bold;color:#00845b">%s</td></tr></table>
                %s
                <p style="margin:24px 0 0;font-size:13px;line-height:1.6;color:#526b62">Puedes consultar o editar esta actividad en el calendario de COFI.</p>
                </td></tr><tr><td style="padding:20px 28px;border-top:1px solid #e4ece8;font-size:12px;line-height:1.6;color:#60786d">
                Tu tiempo y tus finanzas, en orden.<br>Este recordatorio fue generado automáticamente por COFI.
                </td></tr></table></td></tr></table></body></html>
                """.formatted(name.isBlank() ? "" : " " + name, escape(event.getTitulo()), escape(date), time, notes);
    }

    private static String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8"); }
}
