package com.dev.control_financiero.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
class GmailTokenCipherTest {
    @Test void cifraConNonceDistintoYDetectaAlteraciones() {
        var cipher=new GmailTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String encrypted=cipher.encrypt("test-token"); assertEquals("test-token",cipher.decrypt(encrypted));
        assertNotEquals(encrypted,cipher.encrypt("test-token"));
        byte[] bytes=Base64.getDecoder().decode(encrypted); bytes[bytes.length-1]^=1;
        assertThrows(IllegalStateException.class,()->cipher.decrypt(Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void exigeClaveValida() { assertFalse(new GmailTokenCipher("").configured()); }
}
