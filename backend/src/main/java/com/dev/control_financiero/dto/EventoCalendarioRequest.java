package com.dev.control_financiero.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record EventoCalendarioRequest(
        @NotBlank @Size(max = 36) String id,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 300) String description,
        @NotBlank @Pattern(regexp = "ACTIVIDAD|PAGO_TARJETA") String type,
        @Size(max = 64) String categoryId,
        @NotNull LocalDate date,
        @NotNull LocalTime time,
        Long accountId,
        @DecimalMin("0.00") BigDecimal amount,
        @NotBlank @Pattern(regexp = "NONE|DAILY|WEEKLY|MONTHLY|YEARLY") String recurrence,
        @NotNull @Min(0) @Max(525600) Integer reminderMinutes,
        Boolean completed,
        Boolean notifyEmail,
        Boolean notifyBrowser,
        Boolean notifyWhatsapp,
        String createdAt
) {}
