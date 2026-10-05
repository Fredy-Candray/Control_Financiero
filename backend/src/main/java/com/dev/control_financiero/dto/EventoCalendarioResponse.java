package com.dev.control_financiero.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record EventoCalendarioResponse(
        String id, String title, String description, String type, String categoryId,
        LocalDate date, LocalTime time, Long accountId, BigDecimal amount,
        String recurrence, Integer reminderMinutes, Boolean completed,
        Boolean notifyEmail, Boolean notifyBrowser, Boolean notifyWhatsapp, LocalDateTime createdAt
) {}
