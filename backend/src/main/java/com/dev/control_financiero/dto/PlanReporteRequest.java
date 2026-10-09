package com.dev.control_financiero.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PlanReporteRequest(
        @NotBlank @Pattern(regexp = "PRESUPUESTO|META|DEUDA") String tipo,
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) String categoria,
        @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String mes,
        Long cuentaId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal monto,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal acumulado,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal aporteMensual,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 3) BigDecimal tasaInteres,
        LocalDate fechaObjetivo) {}
