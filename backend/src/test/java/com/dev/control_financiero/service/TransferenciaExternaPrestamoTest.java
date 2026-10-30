package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TransferenciaExternaPrestamoTest {
    final MovimientoRepository records=mock(MovimientoRepository.class);
    final CuentaRepository accounts=mock(CuentaRepository.class);
    final CategoriaRepository categories=mock(CategoriaRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final PrestamoPersonalRepository loans=mock(PrestamoPersonalRepository.class);
    final MovimientoService service=new MovimientoService(records,accounts,categories,users,loans);
    final Usuario owner=Usuario.builder().id(1L).build();
    final Cuenta pay=Cuenta.builder().id(2L).usuario(owner).tipo(TipoCuenta.DEBITO).nombre("PAY").activa(true).saldoActual(new BigDecimal("1000.00")).build();
    final PrestamoPersonal loan=PrestamoPersonal.builder().id(7L).usuarioId(1L).acreedor("Mi hermana").saldoInicial(new BigDecimal("200.00"))
            .saldoPendiente(new BigDecimal("200.00")).tasaInteres(BigDecimal.ZERO).fechaRegistro(LocalDateTime.of(2026,10,28,9,0)).build();
    Movimiento saved;
    @BeforeEach void setup() {
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(accounts.findById(2L)).thenReturn(Optional.of(pay));when(accounts.findLockedById(2L)).thenReturn(Optional.of(pay));
        when(loans.findLockedByIdAndUsuarioId(7L,1L)).thenReturn(Optional.of(loan));
        when(records.save(any())).thenAnswer(a->{saved=a.getArgument(0);saved.setId(40L);return saved;});
    }
    CrearMovimientoRequest request(TipoMovimiento type,String amount) {
        var r=new CrearMovimientoRequest();r.setUsuarioId(1L);r.setCuentaOrigenId(2L);r.setMonto(new BigDecimal(amount));r.setTipo(type);r.setReferenciaCliente("request-1");return r;
    }
    @Test void externalShoppingIsExpenseWithoutCreditingAnotherOwnAccount() {
        var request=request(TipoMovimiento.TRANSFERENCIA,"25.00");request.setDestinatarioExterno("Mi esposa");
        var result=service.crearMovimiento(request);
        assertEquals(TipoMovimiento.GASTO,result.getTipo());assertEquals("Mi esposa",result.getDestinatarioExterno());
        assertNull(result.getCuentaDestinoId());assertEquals(new BigDecimal("975.00"),pay.getSaldoActual());
        verify(accounts,times(1)).save(pay);verifyNoInteractions(loans);
    }
    @Test void repaymentReducesPrincipalAndAvailableMoneyWithoutAnExpense() {
        var request=request(TipoMovimiento.PAGO_PRESTAMO,"50.00");request.setPrestamoId(7L);
        var result=service.crearMovimiento(request);
        assertEquals(TipoMovimiento.PAGO_PRESTAMO,result.getTipo());assertEquals(7L,result.getPrestamoId());
        assertEquals(new BigDecimal("950.00"),pay.getSaldoActual());assertEquals(new BigDecimal("150.00"),loan.getSaldoPendiente());
        assertNull(result.getCuentaDestinoId());assertNull(result.getCategoria());
    }
    @Test void rejectsOverpaymentBeforeAnyBalanceChanges() {
        var request=request(TipoMovimiento.PAGO_PRESTAMO,"201.00");request.setPrestamoId(7L);
        assertThrows(IllegalArgumentException.class,()->service.crearMovimiento(request));
        assertEquals(new BigDecimal("1000.00"),pay.getSaldoActual());assertEquals(new BigDecimal("200.00"),loan.getSaldoPendiente());
        verify(records,never()).save(any());verify(loans,never()).save(any());
    }
    @Test void cannotPayAnotherPersonsLoan() {
        var request=request(TipoMovimiento.PAGO_PRESTAMO,"50.00");request.setPrestamoId(99L);
        assertThrows(IllegalArgumentException.class,()->service.crearMovimiento(request));
        assertEquals(new BigDecimal("1000.00"),pay.getSaldoActual());verify(records,never()).save(any());
    }
    @Test void duplicateRequestNeverDeductsMoneyTwice() {
        var request=request(TipoMovimiento.PAGO_PRESTAMO,"50.00");request.setPrestamoId(7L);
        service.crearMovimiento(request);when(records.findByUsuario_IdAndReferenciaCliente(1L,"request-1")).thenReturn(Optional.of(saved));
        service.crearMovimiento(request);
        assertEquals(new BigDecimal("950.00"),pay.getSaldoActual());assertEquals(new BigDecimal("150.00"),loan.getSaldoPendiente());
        verify(records,times(1)).save(any());
        request.setMonto(new BigDecimal("70.00"));assertThrows(IllegalArgumentException.class,()->service.crearMovimiento(request));
    }
    @Test void ownAccountTransferRemainsNeutral() {
        var debit=Cuenta.builder().id(3L).usuario(owner).tipo(TipoCuenta.DEBITO).activa(true).saldoActual(BigDecimal.ZERO).build();
        when(accounts.findById(3L)).thenReturn(Optional.of(debit));when(accounts.findLockedById(3L)).thenReturn(Optional.of(debit));
        var request=request(TipoMovimiento.TRANSFERENCIA,"100.00");request.setCuentaDestinoId(3L);
        assertEquals(TipoMovimiento.TRANSFERENCIA,service.crearMovimiento(request).getTipo());
        assertEquals(new BigDecimal("1000.00"),pay.getSaldoActual().add(debit.getSaldoActual()));verifyNoInteractions(loans);
    }
    @Test void rejectsAmbiguousDestination() {
        var request=request(TipoMovimiento.TRANSFERENCIA,"25.00");request.setCuentaDestinoId(3L);request.setDestinatarioExterno("Persona");
        assertThrows(IllegalArgumentException.class,()->service.crearMovimiento(request));verifyNoInteractions(records,loans);
    }
    @Test void openingLoanIsASeparateLiabilityNotAnIncomeOrPayment() {
        when(users.findLockedById(1L)).thenReturn(Optional.of(owner));when(loans.save(any())).thenAnswer(a->a.getArgument(0));
        var debt=new PrestamoPersonalService(loans,users).crear(1L,"Mi hermana",new BigDecimal("200.00"),"opening-1");
        assertEquals(new BigDecimal("200.00"),debt.getSaldoPendiente());assertEquals(BigDecimal.ZERO,debt.getTasaInteres());
        verifyNoInteractions(accounts,records);
    }
}
