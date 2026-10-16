package com.dev.control_financiero.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

@Component
public class GmailTokenCipher {
    private final String key;
    public GmailTokenCipher(@Value("${app.gmail.encryption-key:}") String key) { this.key=key; }
    public boolean configured() {
        try { return Base64.getDecoder().decode(key).length==32; } catch(Exception e) { return false; }
    }
    public String encrypt(String text) {
        try {
            byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array());
        } catch(Exception e) { throw new IllegalStateException("No se pudo proteger la conexión de Gmail."); }
    }
    public String decrypt(String text) {
        try {
            var data=ByteBuffer.wrap(Base64.getDecoder().decode(text)); byte[] nonce=new byte[12]; data.get(nonce);
            byte[] encrypted=new byte[data.remaining()]; data.get(encrypted);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,nonce));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new IllegalStateException("No se pudo abrir la conexión de Gmail. Revisa la clave del servidor."); }
    }
}
