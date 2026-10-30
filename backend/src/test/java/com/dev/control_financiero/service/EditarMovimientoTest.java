package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditarMovimientoTest {
    final MovimientoRepository records=mock(MovimientoRepository.class);
    final CuentaRepository accounts=mock(CuentaRepository.class);
    final CategoriaRepository categories=mock(CategoriaRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final PrestamoPersonalRepository loans=mock(PrestamoPersonalRepository.class);
    final MovimientoService service=new MovimientoService(records,accounts,categories,users,loans);

    @Test void editsMetadataWithoutChangingMoneyDateOrBankReference() {
        var account=Cuenta.builder().id(2L).saldoActual(new BigDecimal("920.00")).build();
        var movement=Movimiento.builder().id(10L).tipo(TipoMovimiento.GASTO).cuentaOrigen(account)
                .monto(new BigDecimal("25.00")).descripcion("Transferencia")
                .fechaMovimiento(java.time.LocalDateTime.of(2026,10,28,12,15)).referenciaCliente("bank-reference").build();
        var category=Categoria.builder().id(7L).nombre("Préstamo").activa(true).build();
        when(records.findByIdAndUsuario_Id(10L,1L)).thenReturn(Optional.of(movement));
        when(categories.findById(7L)).thenReturn(Optional.of(category));
        when(records.save(movement)).thenReturn(movement);
        var result=service.editarDetalles(1L,10L,new EditarMovimientoRequest(" Dinero prestado ",7L));
        assertEquals("Dinero prestado",result.getDescripcion()); assertEquals(7L,result.getCategoriaId());
        assertEquals(new BigDecimal("920.00"),account.getSaldoActual());
        assertEquals(new BigDecimal("25.00"),result.getMonto());
        assertEquals(java.time.LocalDateTime.of(2026,10,28,12,15),result.getFechaMovimiento());
        assertEquals("bank-reference",movement.getReferenciaCliente());
        verifyNoInteractions(accounts,users,loans);
    }
    @Test void cannotEditAnotherUsersMovement() {
        when(records.findByIdAndUsuario_Id(10L,2L)).thenReturn(Optional.empty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->service.editarDetalles(2L,10L,new EditarMovimientoRequest("X",null)));
        verify(records,never()).save(any()); verifyNoInteractions(accounts,categories);
    }
    @Test void payKeepsRecipientNameWhenChangingCategory() {
        var movement=Movimiento.builder().id(10L).tipo(TipoMovimiento.GASTO)
                .cuentaOrigen(Cuenta.builder().nombre("Pay").build()).destinatarioExterno("Ana")
                .descripcion("PAY a Ana").build();
        when(records.findByIdAndUsuario_Id(10L,1L)).thenReturn(Optional.of(movement));
        when(records.save(movement)).thenReturn(movement);
        var result=service.editarDetalles(1L,10L,new EditarMovimientoRequest("Otro motivo",null));
        assertEquals("PAY a Ana",result.getDescripcion());
        verifyNoInteractions(accounts,users,loans);
    }
    @Test void rejectsInactiveCategoryBeforeChangingDescription() {
        var movement=Movimiento.builder().descripcion("Original").build();
        when(records.findByIdAndUsuario_Id(10L,1L)).thenReturn(Optional.of(movement));
        when(categories.findById(7L)).thenReturn(Optional.of(Categoria.builder().activa(false).build()));
        assertThrows(IllegalArgumentException.class,()->service.editarDetalles(1L,10L,new EditarMovimientoRequest("Otro",7L)));
        assertEquals("Original",movement.getDescripcion()); verify(records,never()).save(any());
    }
}
