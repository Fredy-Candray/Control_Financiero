package com.dev.control_financiero.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class BacWithdrawalParserTest {
    final BacEmailParser parser=new BacEmailParser();
    String fixture(){return "From: alerta@baccredomatic.com\r\nSubject: Notificacion Movimiento Monetario\r\nMessage-ID: <withdrawal@example.test>\r\nAuthentication-Results: mx.google.com; dmarc=pass header.from=baccredomatic.com\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nBAC Credomatic le informa que se ha realizado un retiro de un cajero automático a su cuenta en USD, número *****8564, por un monto de 100.00 USD.\r\nFecha\r\n2/11/2026 a las 13:24:16\r\nMonto enviado\r\n100.00 USD.";}
    @Test void recognizesDebitWithdrawalWithSingleDigitDay() throws Exception {
        var w=parser.withdrawal(fixture().getBytes(StandardCharsets.UTF_8)).orElseThrow();
        assertEquals("8564",w.accountLastFour());assertEquals(new BigDecimal("100.00"),w.amount());assertEquals(LocalDateTime.of(2026,11,2,13,24,16),w.date());
    }
    @Test void rejectsUntrustedAndMismatchedAmounts() {
        assertThrows(Exception.class,()->parser.withdrawal(fixture().replace("dmarc=pass","dmarc=fail").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class,()->parser.withdrawal(fixture().replace("Monto enviado\r\n100.00","Monto enviado\r\n110.00").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void invalidDateAndUnsupportedCurrencyAreNotImported() throws Exception {
        assertThrows(Exception.class,()->parser.withdrawal(fixture().replace("2/11/2026","30/2/2026").getBytes(StandardCharsets.UTF_8)));
        assertTrue(parser.withdrawal(fixture().replace("USD","EUR").getBytes(StandardCharsets.UTF_8)).isEmpty());
    }
    @Test @EnabledIfSystemProperty(named="bac.withdrawal.sample",matches=".+")
    void originalLocalNoticeMatchesWithoutPuttingItInGit() throws Exception {
        var w=parser.withdrawal(Files.readAllBytes(Path.of(System.getProperty("bac.withdrawal.sample")))).orElseThrow();
        assertEquals(new BigDecimal("100.00"),w.amount());assertEquals(LocalDateTime.of(2026,11,2,13,24,16),w.date());assertEquals("8564",w.accountLastFour());
    }
}
