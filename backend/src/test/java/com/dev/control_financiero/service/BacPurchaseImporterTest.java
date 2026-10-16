package com.dev.control_financiero.service;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.enums.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

class BacPurchaseImporterTest {
    final GmailConnectionRepository connections=mock(GmailConnectionRepository.class);
    final BankEmailImportRepository imports=mock(BankEmailImportRepository.class);
    final CuentaRepository accounts=mock(CuentaRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final MovimientoService movements=mock(MovimientoService.class);
    final BacEmailParser parser=mock(BacEmailParser.class);
    final MerchantCategoryService merchantCategories=mock(MerchantCategoryService.class);
    final BacPurchaseImporter importer=new BacPurchaseImporter(connections,imports,accounts,users,movements,parser,merchantCategories);
    final GmailConnection connection=new GmailConnection();
    final Instant now=Instant.now();
    final byte[] raw={1};
    @BeforeEach void setup() throws Exception {
        when(merchantCategories.categoryId(anyString())).thenReturn(null);
        connection.setEnabled(true); connection.setImportSince(now.minusSeconds(60));
        when(connections.lockByUser(1L)).thenReturn(Optional.of(connection));
        when(users.findById(1L)).thenReturn(Optional.of(Usuario.builder().activo(true).build()));
        when(parser.parse(raw)).thenReturn(new BacEmailParser.Purchase("3923","COMERCIO",new BigDecimal("7.50"),LocalDateTime.ofInstant(now.minusSeconds(20),ZoneId.of("America/El_Salvador")),"<sample@example.test>"));
    }
    private void account() {
        var c=Cuenta.builder().id(2L).tipo(TipoCuenta.CREDITO).banco("BAC").ultimosCuatro("3923").moneda("USD").activa(true).saldoActual(new BigDecimal("1500")).build();
        when(accounts.findByUsuarioId(1L)).thenReturn(List.of(c)); when(accounts.findLockedById(2L)).thenReturn(Optional.of(c));
        var m=MovimientoResponse.builder().id(5L).build(); when(movements.crearMovimiento(any())).thenReturn(m);
    }
    @Test void registraCompraConFechaDelBanco() throws Exception {
        account(); importer.process(1L,"gmail1",now,raw);
        var request=ArgumentCaptor.forClass(CrearMovimientoRequest.class); verify(movements).crearMovimiento(request.capture());
        assertEquals(TipoMovimiento.GASTO,request.getValue().getTipo()); assertEquals(new BigDecimal("7.50"),request.getValue().getMonto());
        assertEquals(2L,request.getValue().getCuentaOrigenId()); assertNotNull(request.getValue().getFechaMovimiento());
        verify(imports).save(argThat(r->"IMPORTADO".equals(r.getEstado())&&r.getMovimientoId()==5L));
    }
    @Test void noDescuentaUnCorreoDosVeces() throws Exception {
        var record=new BankEmailImport(); record.setEstado("IMPORTADO");
        when(imports.findByUsuarioIdAndGmailMessageId(1L,"gmail1")).thenReturn(Optional.of(record));
        importer.process(1L,"gmail1",now,raw); verifyNoInteractions(movements,parser);
    }
    @Test void asignaCategoriaSinAlterarMontoNiCuenta() throws Exception {
        account(); when(merchantCategories.categoryId("COMERCIO")).thenReturn(9L);
        importer.process(1L,"categorized",now,raw);
        verify(movements).crearMovimiento(argThat(r->r.getCategoriaId()==9L&&r.getCuentaOrigenId()==2L&&new BigDecimal("7.50").equals(r.getMonto())));
        verify(imports).save(argThat(r->r.getDetalle().contains("Categoría automática")));
    }
    @Test void registraAdicionalEnCuentaPrincipalConservandoDigitosDelAviso() throws Exception {
        account();
        org.springframework.test.util.ReflectionTestUtils.setField(importer,"cardAliases","1:9990:3923");
        when(parser.parse(raw)).thenReturn(new BacEmailParser.Purchase("9990","COMERCIO",new BigDecimal("7.50"),LocalDateTime.ofInstant(now.minusSeconds(20),ZoneId.of("America/El_Salvador")),"<additional@example.test>"));
        importer.process(1L,"additional",now,raw);
        verify(movements).crearMovimiento(argThat(r->r.getCuentaOrigenId()==2L));
        verify(imports).save(argThat(r->"9990".equals(r.getUltimosCuatro())&&"IMPORTADO".equals(r.getEstado())&&r.getDetalle().contains("principal 3923")));
    }
    @Test void noUsaAsociacionDeOtroUsuario() throws Exception {
        account();
        org.springframework.test.util.ReflectionTestUtils.setField(importer,"cardAliases","2:9990:3923");
        when(parser.parse(raw)).thenReturn(new BacEmailParser.Purchase("9990","COMERCIO",new BigDecimal("7.50"),LocalDateTime.ofInstant(now.minusSeconds(20),ZoneId.of("America/El_Salvador")),"<additional@example.test>"));
        importer.process(1L,"additional",now,raw);
        verifyNoInteractions(movements);
    }
    @Test void excluyeCorreosAnteriores() throws Exception {
        importer.process(1L,"gmail1",now.minusSeconds(120),raw); verifyNoInteractions(movements,parser);
        verify(imports).save(argThat(r->"IGNORADO".equals(r.getEstado())));
    }
    @Test void noDescuentaTarjetaSinAsociar() throws Exception {
        importer.process(1L,"gmail1",now,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"ERROR".equals(r.getEstado())));
    }
    @Test void excluyeCompraAnteriorAunqueCorreoLlegueDespues() throws Exception {
        when(parser.parse(raw)).thenReturn(new BacEmailParser.Purchase("3923","COMERCIO",new BigDecimal("7.50"),LocalDateTime.ofInstant(now.minusSeconds(120),ZoneId.of("America/El_Salvador")),"<sample@example.test>"));
        importer.process(1L,"gmail1",now,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"IGNORADO".equals(r.getEstado())));
    }
    @Test void respetaPausa() throws Exception {
        connection.setEnabled(false); importer.process(1L,"gmail1",now,raw); verifyNoInteractions(imports,movements,parser);
    }
    @Test void evitaDuplicadoReenviadoConMismoMessageId() throws Exception {
        when(imports.existsByUsuarioIdAndRfcMessageIdAndEstado(1L,"<sample@example.test>","IMPORTADO")).thenReturn(true);
        importer.process(1L,"gmail2",now,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"IGNORADO".equals(r.getEstado())));
    }
    @Test void conservaErrorDeFormatoParaReintentar() throws Exception {
        when(parser.parse(raw)).thenThrow(new IllegalArgumentException("El formato del aviso cambió o faltan datos."));
        importer.process(1L,"gmail1",now,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"ERROR".equals(r.getEstado())));
    }
    @Test void noGuardaIdentificadorQueExcedeLaColumna() throws Exception {
        when(parser.parse(raw)).thenReturn(new BacEmailParser.Purchase("3923","COMERCIO",new BigDecimal("7.50"),LocalDateTime.ofInstant(now.minusSeconds(20),ZoneId.of("America/El_Salvador")),"x".repeat(256)));
        importer.process(1L,"gmail1",now,raw); verifyNoInteractions(movements);
        verify(imports).save(argThat(r->"IGNORADO".equals(r.getEstado())&&r.getRfcMessageId()==null));
    }
    @Test void ignoresDelayedPaymentAlreadyIncludedInConfirmedBalances() throws Exception {
        when(parser.parse(raw)).thenThrow(new IllegalArgumentException("No es compra"));
        when(parser.authenticatedNoticeSubject(raw)).thenReturn("Pago de tarjeta");
        when(parser.noticeOperationDate(raw)).thenReturn(LocalDateTime.ofInstant(now.minusSeconds(120),ZoneId.of("America/El_Salvador")));
        importer.process(1L,"late-payment",now,raw);
        verifyNoInteractions(accounts,movements);
        verify(imports).save(argThat(r->"IGNORADO".equals(r.getEstado())));
    }
    @Test void leavesUnidentifiedNewPaymentPendingWithoutChangingBalances() throws Exception {
        when(parser.parse(raw)).thenThrow(new IllegalArgumentException("No es compra"));
        when(parser.authenticatedNoticeSubject(raw)).thenReturn("Pago de tarjeta");
        importer.process(1L,"new-payment",now,raw);
        verifyNoInteractions(accounts,movements);
        verify(imports).save(argThat(r->"PENDIENTE".equals(r.getEstado())));
    }
    @Test void respectsNewBaselineEvenWhenOriginalConnectionStartedEarlier() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(importer,"eventsSince",now.minusSeconds(5).toString());
        importer.process(1L,"older-purchase",now,raw);
        verifyNoInteractions(accounts,movements);
        verify(imports).save(argThat(r->"IGNORADO".equals(r.getEstado())));
    }
}
