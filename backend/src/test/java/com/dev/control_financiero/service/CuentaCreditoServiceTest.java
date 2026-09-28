package com.dev.control_financiero.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuentaCreditoServiceTest {

    @Test
    void noPermiteExcederElLimiteDeCredito() {
        BigDecimal limite = new BigDecimal("5000.00");
        BigDecimal usado = new BigDecimal("4700.00");

        assertDoesNotThrow(() -> MovimientoService.validarLimiteCredito(limite, usado, new BigDecimal("200.00")));
        assertThrows(IllegalArgumentException.class,
                () -> MovimientoService.validarLimiteCredito(limite, usado, new BigDecimal("500.00")));
    }
}
