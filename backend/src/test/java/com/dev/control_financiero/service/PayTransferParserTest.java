package com.dev.control_financiero.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

class PayTransferParserTest {
    final PayTransferParser parser=new PayTransferParser(new BacEmailParser());
    String fixture() {
        return "From: notificacionespay@bcr.gob.sv\r\nAuthentication-Results: mx.google.com; dmarc=pass header.from=bcr.gob.sv\r\n"
            +"Message-ID: <pay@test>\r\nMIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n"
            +"<h2>Te informamos que la transferencia que realizaste pudo ser procesada correctamente por medio de Transfer365 por el valor de:</h2><h3>$1,990.24</h3>"
            +"<h2>Quien recibe:</h2><h2>Fredy</h2><h2>Banco destino:</h2><h2>BANCO AMÉRICA CENTRAL</h2>"
            +"<h2>Cuenta de banco destino:</h2><h2>XXXXX8564</h2><h2>Estado de transferencia:</h2><h2>Completada</h2>"
            +"<h2>ID transacción:</h2><h2>202610130023811101149e59a</h2><h2>Fecha y hora:</h2><h2>13/10/2026 09:38:42 AM</h2>";
    }
    @Test void parsesHtmlTransfer() throws Exception {
        var transfer=parser.parse(fixture().getBytes(StandardCharsets.UTF_8));
        assertEquals(new BigDecimal("1990.24"),transfer.amount()); assertEquals("XXXXX8564",transfer.destination());
        assertEquals(LocalDateTime.of(2026,10,13,9,38,42),transfer.date()); assertNull(transfer.description());
    }
    @Test void rejectsForgedSender() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("From: notificacionespay@bcr.gob.sv","From: attacker@test").getBytes(StandardCharsets.UTF_8))); }
    @Test void rejectsFailedDmarc() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("dmarc=pass","dmarc=fail").getBytes(StandardCharsets.UTF_8))); }
    @Test void rejectsPendingTransfer() { assertThrows(IllegalArgumentException.class,()->parser.parse(fixture().replace("Completada","Pendiente").getBytes(StandardCharsets.UTF_8))); }
    @Test void readsDescriptionWhenPresent() throws Exception {
        assertEquals("Prueba",parser.parse((fixture()+"<h2>Descripción:</h2><h2>Prueba</h2>").getBytes(StandardCharsets.UTF_8)).description());
    }
    @Test void parsesRealEmail() throws Exception {
        String path=System.getProperty("pay.sample.path"); assumeTrue(path!=null);
        var transfer=parser.parse(Files.readAllBytes(Path.of(path)));
        assertEquals(new BigDecimal("1990.24"),transfer.amount()); assertEquals("XXXXX8564",transfer.destination());
        assertEquals("202610130023811101149e59a",transfer.transactionId());
    }
    @Test void parsesIncomingCredit() throws Exception {
        String mail="From: notificaciones365pay@bcr.gob.sv\r\nAuthentication-Results: mx.google.com; dmarc=pass header.from=bcr.gob.sv\r\n"
            +"Content-Type: text/plain; charset=UTF-8\r\n\r\nTe informamos que has recibido un abono por el valor de:\n$14.00\nDe parte de:\nMayra\nID transacción:\n202602190020707277052ff02\nFecha y hora:\n19/02/2026 01:07 PM";
        var credit=parser.parseCredit(mail.getBytes(StandardCharsets.UTF_8));
        assertEquals(new BigDecimal("14.00"),credit.amount()); assertEquals("Mayra",credit.payer());
        assertEquals(LocalDateTime.of(2026,2,19,13,7),credit.date());
        assertThrows(IllegalArgumentException.class,()->parser.parseCredit(mail.replace("dmarc=pass","dmarc=fail").getBytes(StandardCharsets.UTF_8)));
    }
}
