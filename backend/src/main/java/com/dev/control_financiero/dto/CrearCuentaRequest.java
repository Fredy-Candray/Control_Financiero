package com.dev.control_financiero.dto;

import com.dev.control_financiero.enums.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearCuentaRequest {

    @NotBlank
    private String nombre;

    @NotNull
    private TipoCuenta tipo;

    @NotNull
    private BigDecimal saldoActual;

    @NotNull
    private Long usuarioId;

}