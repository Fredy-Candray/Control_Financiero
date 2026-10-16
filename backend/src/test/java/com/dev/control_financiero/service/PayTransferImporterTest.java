package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

class PayTransferImporterTest {
    GmailConnectionRepository connections=mock(GmailConnectionRepository.class);
    BankEmailImportRepository imports=mock(BankEmailImportRepository.class);
    CuentaRepository accounts=mock(CuentaRepository.class);
    MovimientoService movements=mock(MovimientoService.class);
    PayTransferParser parser=mock(PayTransferParser.class);
    PayTransferImporter importer=new PayTransferImporter(connections,imports,accounts,movements,parser);
    Instant received=Instant.now();
    byte[] raw=new byte[0];
    Cuenta origin,destination;
    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(importer,"configuredUser",1L);
        ReflectionTestUtils.setField(importer,"since",received.minusSeconds(600).toString());
        ReflectionTestUtils.setField(importer,"lastFour","8564");
        ReflectionTestUtils.setField(importer,"originName","Pay");
        ReflectionTestUtils.setField(importer,"destinationName","Tarjeta débito");
        var connection=new GmailConnection(); connection.setEnabled(true);
        when(connections.lockByUser(1L)).thenReturn(Optional.of(connection));
        when(imports.findByUsuarioIdAndGmailMessageId(1L,"new")).thenReturn(Optional.empty());
        when(parser.parse(raw)).thenReturn(new PayTransferParser.Transfer(new BigDecimal("1990.24"),"XXXXX8564","BANCO AMÉRICA CENTRAL","Fredy","unique-test",LocalDateTime.ofInstant(received.minusSeconds(60),ZoneId.of("America/El_Salvador")),"Otro texto"));
        origin=Cuenta.builder().id(6L).nombre("Pay").tipo(TipoCuenta.DEBITO).activa(true).saldoActual(new BigDecimal("2990.24")).build();
        destination=Cuenta.builder().id(5L).nombre("Tarjeta débito").banco("BAC").tipo(TipoCuenta.DEBITO).activa(true).saldoActual(new BigDecimal("1.25")).build();
        when(accounts.findByUsuarioId(1L)).thenReturn(List.of(origin,destination));
        when(accounts.findLockedById(5L)).thenReturn(Optional.of(destination));
        when(accounts.findLockedById(6L)).thenReturn(Optional.of(origin));
        when(accounts.findById(6L)).thenReturn(Optional.of(origin));
        var response=MovimientoResponse.builder().id(123L).build();
        when(movements.crearMovimiento(any())).thenReturn(response);
    }
    @Test void recordsOnlyTransferWithFixedDescription() throws Exception {
        importer.process(1L,"new",received,raw);
        var request=org.mockito.ArgumentCaptor.forClass(CrearMovimientoRequest.class);
        verify(movements).crearMovimiento(request.capture());
        assertEquals(TipoMovimiento.TRANSFERENCIA,request.getValue().getTipo());
        assertEquals("TRANSFERENCIA",request.getValue().getDescripcion());
        assertEquals(6L,request.getValue().getCuentaOrigenId()); assertEquals(5L,request.getValue().getCuentaDestinoId());
    }
    @Test void doesNotRepeatTransactionFromAnotherEmail() throws Exception {
        when(imports.existsByUsuarioIdAndRfcMessageIdAndEstado(1L,"pay:unique-test","IMPORTADO")).thenReturn(true);
        importer.process(1L,"new",received,raw); verifyNoInteractions(movements);
    }
    @Test void doesNotImportHistoryBeforeActivation() throws Exception {
        importer.process(1L,"new",received.minusSeconds(1000),raw); verifyNoInteractions(parser,movements);
    }
    @Test void insufficientBalanceLeavesPendingError() throws Exception {
        origin.setSaldoActual(BigDecimal.ONE); importer.process(1L,"new",received,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"ERROR".equals(r.getEstado())));
    }
    @Test void doesNotProcessAnotherUser() throws Exception {
        importer.process(2L,"new",received,raw); verifyNoInteractions(connections,movements);
    }
    @Test void importsThirdPartyCreditAsIncome() throws Exception {
        ReflectionTestUtils.setField(importer,"extended",true);
        when(parser.parse(raw)).thenThrow(new IllegalArgumentException("No es transferencia"));
        when(parser.parseCredit(raw)).thenReturn(new PayTransferParser.Credit(new BigDecimal("14.00"),"Mayra","credit-test",LocalDateTime.ofInstant(received.minusSeconds(60),ZoneId.of("America/El_Salvador"))));
        importer.process(1L,"new",received,raw);
        verify(movements).crearMovimiento(argThat(r->r.getTipo()==TipoMovimiento.INGRESO && r.getCuentaDestinoId()==null && r.getCuentaOrigenId()==6L));
    }
    @Test void importsOutgoingThirdPartyTransferAsExpense() throws Exception {
        ReflectionTestUtils.setField(importer,"extended",true); ReflectionTestUtils.setField(importer,"ownerName","Fredy");
        when(parser.parse(raw)).thenReturn(new PayTransferParser.Transfer(BigDecimal.TEN,"XXXXX5687","BANCO AGRÍCOLA","Ana","external-test",LocalDateTime.ofInstant(received.minusSeconds(60),ZoneId.of("America/El_Salvador")),null));
        importer.process(1L,"new",received,raw);
        verify(movements).crearMovimiento(argThat(r->r.getTipo()==TipoMovimiento.GASTO && r.getCuentaDestinoId()==null && r.getCuentaOrigenId()==6L));
    }
    @Test void leavesUnmappedOwnTransferPendingWithoutExpense() throws Exception {
        ReflectionTestUtils.setField(importer,"extended",true); ReflectionTestUtils.setField(importer,"ownerName","Fredy");
        when(parser.parse(raw)).thenReturn(new PayTransferParser.Transfer(BigDecimal.TEN,"XXXXX5687","BANCO AGRÍCOLA","Fredy","own-test",LocalDateTime.ofInstant(received.minusSeconds(60),ZoneId.of("America/El_Salvador")),null));
        importer.process(1L,"new",received,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"PENDIENTE".equals(r.getEstado())));
    }
}
