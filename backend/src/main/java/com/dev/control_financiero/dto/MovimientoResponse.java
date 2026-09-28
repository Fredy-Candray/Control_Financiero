package com.dev.control_financiero.dto;

import com.dev.control_financiero.entity.Movimiento;
import com.dev.control_financiero.enums.TipoMovimiento;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MovimientoResponse {
    private Long id;
    private TipoMovimiento tipo;
    private BigDecimal monto;
    private String descripcion;
    private LocalDateTime fechaMovimiento;
    private Long cuentaOrigenId;
    private String cuentaOrigen;
    private Long cuentaDestinoId;
    private String cuentaDestino;
    private String categoria;

    public static MovimientoResponse from(Movimiento movimiento) {
        return MovimientoResponse.builder()
                .id(movimiento.getId())
                .tipo(movimiento.getTipo())
                .monto(movimiento.getMonto())
                .descripcion(movimiento.getDescripcion())
                .fechaMovimiento(movimiento.getFechaMovimiento())
                .cuentaOrigenId(movimiento.getCuentaOrigen() == null ? null : movimiento.getCuentaOrigen().getId())
                .cuentaOrigen(movimiento.getCuentaOrigen() == null ? null : movimiento.getCuentaOrigen().getNombre())
                .cuentaDestinoId(movimiento.getCuentaDestino() == null ? null : movimiento.getCuentaDestino().getId())
                .cuentaDestino(movimiento.getCuentaDestino() == null ? null : movimiento.getCuentaDestino().getNombre())
                .categoria(movimiento.getCategoria() == null ? null : movimiento.getCategoria().getNombre())
                .build();
    }
}
