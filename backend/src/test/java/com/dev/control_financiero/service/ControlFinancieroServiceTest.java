package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ControlFinancieroServiceTest {
    final BankEmailImportRepository notices=mock(BankEmailImportRepository.class);
    final MovimientoRepository movements=mock(MovimientoRepository.class);
    final CuentaRepository accounts=mock(CuentaRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final PrestamoOtorgadoRepository loans=mock(PrestamoOtorgadoRepository.class);
    final ConciliacionSaldoRepository reconciliations=mock(ConciliacionSaldoRepository.class);
    final ControlFinancieroService service=new ControlFinancieroService(notices,movements,accounts,users,loans,reconciliations);
    final Usuario user=Usuario.builder().id(1L).build();
    Cuenta account; PrestamoOtorgado loan;
    @BeforeEach void setup() {
        account=Cuenta.builder().id(2L).nombre("Pay").tipo(TipoCuenta.DEBITO).usuario(user).activa(true).saldoActual(new BigDecimal("100.00")).build();
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));when(accounts.findLockedById(2L)).thenReturn(Optional.of(account));
        when(loans.save(any())).thenAnswer(i->{PrestamoOtorgado p=i.getArgument(0);if(p.getId()==null)p.setId(5L);return p;});
        when(movements.save(any())).thenAnswer(i->{Movimiento m=i.getArgument(0);if(m.getId()==null)m.setId(10L);return m;});
        loan=new PrestamoOtorgado();loan.setId(5L);loan.setUsuarioId(1L);loan.setPersona("Ana");loan.setMonto(new BigDecimal("25.00"));loan.setPendiente(new BigDecimal("25.00"));loan.setFecha(LocalDateTime.of(2026,10,28,12,15));
        when(loans.findByIdAndUsuarioId(5L,1L)).thenReturn(Optional.of(loan));
    }
    Movimiento existing(TipoMovimiento type) {return Movimiento.builder().id(8L).tipo(type).usuario(user).cuentaOrigen(account).monto(new BigDecimal("25.00")).fechaMovimiento(LocalDateTime.of(2026,11,3,12,0)).descripcion("PAY a Ana").build();}
    @Test void linkedLoanDoesNotDebitAgain() {
        var m=existing(TipoMovimiento.GASTO);when(movements.findByIdAndUsuario_Id(8L,1L)).thenReturn(Optional.of(m));
        var result=service.prestar(1L,"Ana",null,null,8L,null,null,"ref");
        assertEquals(8L,result.getMovimientoOrigenId());assertEquals(new BigDecimal("25.00"),result.getPendiente());
        assertEquals(TipoMovimiento.PRESTAMO_OTORGADO,m.getTipo());assertEquals(5L,m.getPrestamoOtorgadoId());
        assertEquals(new BigDecimal("100.00"),account.getSaldoActual());verifyNoInteractions(accounts);
    }
    @Test void existingReceivableNeedsNoCashAndAllowsAnOverdueDate() {
        var result=service.registrarPendiente(1L,"Ana",new BigDecimal("700.00"),LocalDate.of(2026,9,1),null,"opening");
        assertTrue(result.isRegistroInicial());assertNull(result.getMovimientoOrigenId());
        assertEquals(new BigDecimal("700.00"),result.getPendiente());assertEquals(LocalDate.of(2026,9,1),result.getVencimiento());
        verifyNoInteractions(accounts,movements);
    }
    @Test void existingReceivableRetryDoesNotCreateAnotherLoan() {
        loan.setRegistroInicial(true);when(loans.findByUsuarioIdAndReferenciaCliente(1L,"opening")).thenReturn(Optional.of(loan));
        assertSame(loan,service.registrarPendiente(1L,"Ana",new BigDecimal("25.00"),null,null,"opening"));
        verify(loans,never()).save(any());verifyNoInteractions(accounts,movements);
    }
    @Test void newLoanDebitsOnceAndDoesNotCountAsExpense() {
        var result=service.prestar(1L,"Ana",new BigDecimal("25.00"),2L,null,null,null,"ref");
        assertEquals(new BigDecimal("75.00"),account.getSaldoActual());assertEquals(new BigDecimal("25.00"),result.getPendiente());
        verify(movements).save(argThat(m->m.getTipo()==TipoMovimiento.PRESTAMO_OTORGADO&&"PAY a Ana".equals(m.getDescripcion())));
    }
    @Test void insufficientFundsAndCreditOriginsRejected() {
        assertThrows(IllegalArgumentException.class,()->service.prestar(1L,"Ana",new BigDecimal("101.00"),2L,null,null,null,"ref"));
        account.setTipo(TipoCuenta.CREDITO);
        assertThrows(IllegalArgumentException.class,()->service.prestar(1L,"Ana",BigDecimal.TEN,2L,null,null,null,"ref"));verify(loans,never()).save(any());
    }
    @Test void partialRepaymentCreditsOnce() {
        service.cobrar(1L,5L,BigDecimal.TEN,2L,null,"abono");
        assertEquals(new BigDecimal("110.00"),account.getSaldoActual());assertEquals(new BigDecimal("15.00"),loan.getPendiente());
        verify(movements).save(argThat(m->m.getTipo()==TipoMovimiento.COBRO_PRESTAMO&&m.getPrestamoOtorgadoId()==5L));
    }
    @Test void alreadyCreditedIncomeOnlyChangesClassification() {
        var m=existing(TipoMovimiento.INGRESO);when(movements.findByIdAndUsuario_Id(8L,1L)).thenReturn(Optional.of(m));
        service.cobrar(1L,5L,null,null,8L,"abono");
        assertEquals(BigDecimal.ZERO.setScale(2),loan.getPendiente());assertEquals(new BigDecimal("100.00"),account.getSaldoActual());
        assertEquals(TipoMovimiento.COBRO_PRESTAMO,m.getTipo());verifyNoInteractions(accounts);
    }
    @Test void overpaymentRejectedBeforeAccountCredit() {
        assertThrows(IllegalArgumentException.class,()->service.cobrar(1L,5L,new BigDecimal("26.00"),2L,null,"abono"));
        assertEquals(new BigDecimal("100.00"),account.getSaldoActual());assertEquals(new BigDecimal("25.00"),loan.getPendiente());verify(movements,never()).save(any());
    }
    @Test void anotherUsersLoanAndAccountCannotBeUsed() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.cobrar(1L,9L,BigDecimal.ONE,2L,null,"abono"));
        account.setUsuario(Usuario.builder().id(9L).build());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.prestar(1L,"Ana",BigDecimal.ONE,2L,null,null,null,"ref"));
    }
    @Test void repeatedRepaymentDoesNotCreditAgain() {
        var prior=existing(TipoMovimiento.COBRO_PRESTAMO);prior.setPrestamoOtorgadoId(5L);
        when(movements.findByUsuario_IdAndReferenciaCliente(1L,"abono")).thenReturn(Optional.of(prior));
        service.cobrar(1L,5L,new BigDecimal("25.00"),2L,null,"abono");verifyNoInteractions(accounts);verify(movements,never()).save(any());
    }
    @Test void reconciliationRecordsDifferenceWithoutAdjustingBalance() {
        when(reconciliations.save(any())).thenAnswer(i->i.getArgument(0));
        var r=service.conciliar(1L,2L,new BigDecimal("90.00"),null,"revision");
        assertEquals(new BigDecimal("-10.00"),r.getDiferencia());assertEquals(new BigDecimal("100.00"),account.getSaldoActual());verify(accounts,never()).save(any());verifyNoInteractions(movements);
    }
    @Test void resolvingNoticeDoesNotMoveMoneyAndPreservesOriginalDetail() {
        var a=new BankEmailImport();a.setEstado("PENDIENTE");a.setDetalle("Formato no reconocido");a.setMonto(new BigDecimal("25.00"));
        when(notices.findByIdAndUsuarioId(4L,1L)).thenReturn(Optional.of(a));when(notices.save(a)).thenReturn(a);
        when(movements.findByIdAndUsuario_Id(8L,1L)).thenReturn(Optional.of(existing(TipoMovimiento.GASTO)));
        service.resolver(1L,4L,"VINCULAR","Registrado manualmente",8L);
        assertEquals("IMPORTADO",a.getEstado());assertEquals(8L,a.getMovimientoId());assertEquals("Formato no reconocido",a.getDetalle());assertNotNull(a.getRevisadoEn());verifyNoInteractions(accounts);verify(movements,never()).save(any());
    }
    @Test void mismatchedNoticeAmountIsNotResolved() {
        var a=new BankEmailImport();a.setEstado("PENDIENTE");a.setMonto(BigDecimal.TEN);when(notices.findByIdAndUsuarioId(4L,1L)).thenReturn(Optional.of(a));
        when(movements.findByIdAndUsuario_Id(8L,1L)).thenReturn(Optional.of(existing(TipoMovimiento.GASTO)));
        assertThrows(IllegalArgumentException.class,()->service.resolver(1L,4L,"VINCULAR","Revisado",8L));verify(notices,never()).save(any());
    }
}
