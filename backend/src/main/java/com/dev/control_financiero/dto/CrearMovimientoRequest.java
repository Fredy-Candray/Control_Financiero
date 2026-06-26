package com.dev.control_financiero.dto;

import com.dev.control_financiero.enums.TipoMovimiento;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearMovimientoRequest {

    @NotNull
    private TipoMovimiento tipo;

    @NotNull
    private BigDecimal monto;

    private String descripcion;

    private Long cuentaOrigenId;

    private Long cuentaDestinoId;

    private Long categoriaId;

    @NotNull
    private Long usuarioId;

}