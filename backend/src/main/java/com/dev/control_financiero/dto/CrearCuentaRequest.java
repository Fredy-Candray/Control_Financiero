package com.dev.control_financiero.dto;

import com.dev.control_financiero.enums.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearCuentaRequest {

    @NotBlank
    private String nombre;

    @NotNull
    private TipoCuenta tipo;

    @NotNull
    @DecimalMin(value = "0.00", message = "El saldo no puede ser negativo")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal saldoActual;

    @DecimalMin(value = "0.01", message = "El límite de crédito debe ser mayor que cero")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal limiteCredito;

    @NotNull
    private Long usuarioId;

}
