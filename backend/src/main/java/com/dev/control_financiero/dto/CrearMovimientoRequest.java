package com.dev.control_financiero.dto;

import com.dev.control_financiero.enums.TipoMovimiento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearMovimientoRequest {

    @NotNull
    private TipoMovimiento tipo;

    @NotNull
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal monto;

    private String descripcion;

    @NotNull(message = "La cuenta de origen es obligatoria")
    private Long cuentaOrigenId;

    private Long cuentaDestinoId;

    private Long categoriaId;

    @NotNull
    private Long usuarioId;

}
