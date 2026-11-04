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
    private String destinatarioExterno;
    private Long prestamoId;
    private LocalDateTime fechaMovimiento;
    private Long cuentaOrigenId;
    private String cuentaOrigen;
    private Long cuentaDestinoId;
    private String cuentaDestino;
    private String categoria;
    private Long categoriaId;
    private Long prestamoOtorgadoId;

    public static MovimientoResponse from(Movimiento movimiento) {
        return MovimientoResponse.builder()
                .id(movimiento.getId())
                .prestamoOtorgadoId(movimiento.getPrestamoOtorgadoId())
                .tipo(movimiento.getTipo())
                .monto(movimiento.getMonto())
                .descripcion(movimiento.getDescripcion())
                .destinatarioExterno(movimiento.getDestinatarioExterno())
                .prestamoId(movimiento.getPrestamo()==null?null:movimiento.getPrestamo().getId())
                .fechaMovimiento(movimiento.getFechaMovimiento())
                .cuentaOrigenId(movimiento.getCuentaOrigen() == null ? null : movimiento.getCuentaOrigen().getId())
                .cuentaOrigen(movimiento.getCuentaOrigen() == null ? null : movimiento.getCuentaOrigen().getNombre())
                .cuentaDestinoId(movimiento.getCuentaDestino() == null ? null : movimiento.getCuentaDestino().getId())
                .cuentaDestino(movimiento.getCuentaDestino() == null ? null : movimiento.getCuentaDestino().getNombre())
                .categoria(movimiento.getCategoria() == null ? null : movimiento.getCategoria().getNombre())
                .categoriaId(movimiento.getCategoria() == null ? null : movimiento.getCategoria().getId())
                .build();
    }
}
