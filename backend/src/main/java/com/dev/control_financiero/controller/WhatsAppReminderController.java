package com.dev.control_financiero.controller;

import com.dev.control_financiero.service.WhatsAppReminderGateway;
import com.dev.control_financiero.service.WhatsAppReminderMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;

@RestController @RequestMapping("/api/calendario/whatsapp") @RequiredArgsConstructor
public class WhatsAppReminderController {
    private final WhatsAppReminderGateway gateway;
    public record PreviewRequest(@NotBlank @Size(max=120) String title, @NotNull LocalDate date, @NotNull LocalTime time, @NotBlank @Size(max=40) String phone) {}
    public record Preview(String phone, String message, boolean sent) {}
    @GetMapping("/estado") public WhatsAppReminderGateway.Status status(HttpServletRequest request) { authorize(request); return gateway.status(); }
    @PostMapping("/vista-previa") public Preview preview(@Valid @RequestBody PreviewRequest body, HttpServletRequest request) {
        authorize(request);
        try { return new Preview(WhatsAppReminderMessage.normalizePhone(body.phone()), WhatsAppReminderMessage.preview(body.title(), LocalDateTime.of(body.date(), body.time())), false); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }
    private void authorize(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión nuevamente.");
    }
}
