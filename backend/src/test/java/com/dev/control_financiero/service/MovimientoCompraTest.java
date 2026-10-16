package com.dev.control_financiero.service;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.enums.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
class MovimientoCompraTest {
    final MovimientoRepository records=mock(MovimientoRepository.class);
    final CuentaRepository accounts=mock(CuentaRepository.class);
    final CategoriaRepository categories=mock(CategoriaRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final MovimientoService service=new MovimientoService(records,accounts,categories,users);
    final Usuario user=Usuario.builder().id(1L).build();
    private Cuenta setup(String available) {
        var account=Cuenta.builder().id(2L).nombre("BAC VISA").usuario(user).tipo(TipoCuenta.CREDITO).activa(true)
                .limiteCredito(new BigDecimal("1500")).saldoActual(new BigDecimal(available)).build();
        when(users.findById(1L)).thenReturn(Optional.of(user)); when(accounts.findLockedById(2L)).thenReturn(Optional.of(account));
        when(accounts.findById(2L)).thenReturn(Optional.of(account));
        when(records.save(any())).thenAnswer(i->{Movimiento m=i.getArgument(0);m.setId(5L);return m;}); return account;
    }
    private CrearMovimientoRequest request() {
        var r=new CrearMovimientoRequest(); r.setUsuarioId(1L); r.setCuentaOrigenId(2L);r.setTipo(TipoMovimiento.GASTO);
        r.setMonto(new BigDecimal("7.50"));r.setFechaMovimiento(LocalDateTime.of(2026,10,12,13,2,5));return r;
    }
    @Test void descuentaDisponibleYConservaFechaOriginal() {
        var account=setup("1500");var result=service.crearMovimiento(request());
        assertEquals(new BigDecimal("1492.50"),account.getSaldoActual()); assertEquals(request().getFechaMovimiento(),result.getFechaMovimiento());
        verify(accounts).findLockedById(2L);verify(records).save(any());
    }
    @Test void noRegistraNiDescuentaSiNoHayDisponible() {
        var account=setup("5.00");assertThrows(IllegalArgumentException.class,()->service.crearMovimiento(request()));
        assertEquals(new BigDecimal("5.00"),account.getSaldoActual());verify(records,never()).save(any());
    }
    @Test void rechazaCuentaInactiva() {
        var account=setup("1500"); account.setActiva(false);
        assertThrows(IllegalArgumentException.class,()->service.crearMovimiento(request()));verify(records,never()).save(any());
    }
}
