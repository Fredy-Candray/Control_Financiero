package com.dev.control_financiero.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuentaCreditoServiceTest {

    @Test
    void noPermiteExcederElLimiteDeCredito() {
        BigDecimal limite = new BigDecimal("5000.00");
        BigDecimal disponible = new BigDecimal("300.00");

        assertDoesNotThrow(() -> MovimientoService.validarLimiteCredito(limite, disponible, new BigDecimal("200.00")));
        assertThrows(IllegalArgumentException.class,
                () -> MovimientoService.validarLimiteCredito(limite, disponible, new BigDecimal("500.00")));
    }

    @Test void permiteLaPrimeraCompraConTodoElCreditoDisponible() {
        assertDoesNotThrow(() -> MovimientoService.validarLimiteCredito(new BigDecimal("1500"),new BigDecimal("1500"),new BigDecimal("7.50")));
        assertThrows(IllegalArgumentException.class, () -> MovimientoService.validarLimiteCredito(new BigDecimal("1500"),BigDecimal.ZERO,new BigDecimal("0.01")));
    }
}
