package com.dev.control_financiero.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ResumenDashboardResponse {

    private BigDecimal efectivo;
    private BigDecimal debito;
    private BigDecimal credito;
    private BigDecimal deudaTarjetas;
    private BigDecimal patrimonioDisponible;
}