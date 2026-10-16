package com.dev.control_financiero.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

class BacEmailParserTest {
    private final BacEmailParser parser=new BacEmailParser();
    private String fixture() {
        return "From: info@baccredomatic.com\r\nAuthentication-Results: mx.google.com; dmarc=pass header.from=baccredomatic.com\r\n"
                + "Message-ID: <sample@example.test>\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=iso-8859-1\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n"
                + "Se acaba de realizar una compra con tu tarjeta VISA terminada en 3923=20\r\n\r\nComercio=20\r\n\r\nMonto=20\r\n\r\nSELECTOS ARCE=20\r\n\r\n1.65=20\r\n\r\nFecha y hora=20\r\n2026/10/12-13:02:05=20\r\nTipo de la compra\r\nTarjeta Presente\r\nEstado\r\nAprobada=20\r\n";
    }
    @Test void interpretaQuotedPrintableDeBac() throws Exception {
        var p=parser.parse(fixture().getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("3923",p.lastFour()); assertEquals("SELECTOS ARCE",p.merchant());
        assertEquals(new BigDecimal("1.65"),p.amount()); assertEquals(LocalDateTime.of(2026,10,12,13,2,5),p.date());
    }
    @Test void rechazaAvisoNoAprobado() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("Aprobada","Rechazada").getBytes(StandardCharsets.ISO_8859_1))); }
    @Test void interpretaTarjetaAdicionalConSaltosDeLinea() throws Exception {
        var raw=fixture().replace("tarjeta VISA terminada en 3923", "tarjeta adicional MASTERCARD\r\nterminada en 9990 a nombre de TITULAR DE PRUEBA");
        assertEquals("9990",parser.parse(raw.getBytes(StandardCharsets.ISO_8859_1)).lastFour());
    }
    @Test void rechazaRemitenteImpostor() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("From: info@baccredomatic.com","From: info@example.test").getBytes(StandardCharsets.ISO_8859_1))); }
    @Test void exigeAutenticacionDeGmail() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("dmarc=pass","dmarc=fail").getBytes(StandardCharsets.ISO_8859_1))); }
    @Test void noAceptaFechaInvalida() { assertThrows(Exception.class,()->parser.parse(fixture().replace("2026/10/12","2026/02/30").getBytes(StandardCharsets.ISO_8859_1))); }
    @Test void rechazaMontoFueraDePrecisionDelSistema() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("1.65=20","12345678901234567.65=20").getBytes(StandardCharsets.ISO_8859_1))); }
    @Test void recognizesTrustedNonPurchaseNoticeForReview() throws Exception {
        String notice=fixture().replace("From: info@baccredomatic.com","From: alerta@baccredomatic.com").replace("Message-ID:","Subject: Pago de tarjeta\r\nMessage-ID:");
        assertEquals("Pago de tarjeta",parser.authenticatedNoticeSubject(notice.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(LocalDateTime.of(2026,10,12,13,2,5),parser.noticeOperationDate(notice.getBytes(StandardCharsets.ISO_8859_1)));
        assertThrows(IllegalArgumentException.class,()->parser.authenticatedNoticeSubject(notice.replace("dmarc=pass","dmarc=fail").getBytes(StandardCharsets.ISO_8859_1)));
    }
    @Test void interpretaCorreoOriginalSinGuardarloEnElRepositorio() throws Exception {
        String path=System.getProperty("bac.sample.path"); assumeTrue(path!=null);
        var p=parser.parse(Files.readAllBytes(Path.of(path)));
        assertEquals("3923",p.lastFour()); assertEquals("SELECTOS ARCE",p.merchant()); assertEquals(new BigDecimal("1.65"),p.amount());
    }
    @Test void interpretaAvisosAdicionalesOriginalesSinGuardarlosEnElRepositorio() throws Exception {
        String directory=System.getProperty("bac.additional.sample.dir"); assumeTrue(directory!=null);
        String[] ids={"1a137ceb6e8141f1","1a137db418bf6958","1a137dc824193c03"};
        String[] amounts={"175.21","165.00","12.95"};
        for(int i=0;i<ids.length;i++) {
            var purchase=parser.parse(Files.readAllBytes(Path.of(directory,"cofi-bac-"+ids[i]+".eml")));
            assertEquals("9990",purchase.lastFour());
            assertEquals(new BigDecimal(amounts[i]),purchase.amount());
        }
    }
}
