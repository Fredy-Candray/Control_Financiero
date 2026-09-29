package com.dev.control_financiero.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ActualizarCuentaRequest {
    @NotBlank
    private String nombre;

    @NotNull
    @DecimalMin(value = "0.00")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal saldoActual;

    @DecimalMin(value = "0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal limiteCredito;

    private Boolean activa;
}
