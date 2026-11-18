package com.dev.control_financiero.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CicloTarjetaRequest(
        @NotNull LocalDate fechaCorte,
        @NotNull LocalDate fechaPago,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal pendienteCiclo) {}
