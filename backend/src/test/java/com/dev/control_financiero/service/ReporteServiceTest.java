package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.PlanReporteRequest;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.TipoCuenta;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;

class ReporteServiceTest {
    final PlanReporteRepository planes = mock(PlanReporteRepository.class);
    final CuentaRepository cuentas = mock(CuentaRepository.class);
    final ReporteService servicio = new ReporteService(planes, cuentas);
    PlanReporteRequest presupuesto() {
        return new PlanReporteRequest("PRESUPUESTO", "Comida", "Alimentación", "2026-10", null,
                new BigDecimal("100"), null, null, null, null);
    }
    @Test void guardaConElUsuarioDeLaSesion() {
        when(planes.findByUsuarioIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(planes.save(any())).thenAnswer(call -> call.getArgument(0));
        var plan = servicio.guardar(7L, null, presupuesto());
        assertEquals(7L, plan.getUsuarioId());
        assertEquals("Alimentación", plan.getCategoria());
    }
    @Test void noPermiteEditarPlanesAjenos() {
        when(planes.findByUsuarioIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(planes.findByIdAndUsuarioId(99L, 7L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> servicio.guardar(7L, 99L, presupuesto()));
        verify(planes, never()).save(any());
    }
    @Test void rechazaPresupuestosDuplicados() {
        PlanReporte plan = new PlanReporte(); plan.setId(1L); plan.setTipo("PRESUPUESTO"); plan.setMes("2026-10"); plan.setCategoria("Alimentación");
        when(planes.findByUsuarioIdOrderByIdAsc(7L)).thenReturn(List.of(plan));
        assertThrows(ResponseStatusException.class, () -> servicio.guardar(7L, null, presupuesto()));
    }
    @Test void rechazaTarjetasDeOtroUsuario() {
        Cuenta cuenta = new Cuenta(); cuenta.setTipo(TipoCuenta.CREDITO); cuenta.setUsuario(Usuario.builder().id(8L).build());
        when(cuentas.findById(3L)).thenReturn(Optional.of(cuenta));
        var request = new PlanReporteRequest("DEUDA", "Tarjeta", null, null, 3L, BigDecimal.ONE, null, null, BigDecimal.TEN, null);
        assertThrows(ResponseStatusException.class, () -> servicio.guardar(7L, null, request));
        verify(planes, never()).save(any());
    }
}
