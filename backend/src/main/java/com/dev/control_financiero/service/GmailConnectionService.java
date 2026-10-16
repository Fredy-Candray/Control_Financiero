package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.GmailConnection;
import com.dev.control_financiero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class GmailConnectionService {
    private final GmailConnectionRepository connections;
    private final CuentaRepository accounts;
    private final GmailTokenCipher cipher;

    @Transactional public void connect(Long userId,String correo,String refreshToken) {
        if(refreshToken==null || refreshToken.isBlank()) throw new IllegalArgumentException("Autoriza nuevamente Gmail para permitir la sincronización.");
        var c=connections.lockByUser(userId).orElseGet(GmailConnection::new);
        if(c.getCorreo()!=null && !c.getCorreo().equalsIgnoreCase(correo))
            throw new IllegalArgumentException("Reconecta el mismo correo para conservar el historial.");
        c.setUsuarioId(userId); c.setCorreo(correo); c.setRefreshTokenEncrypted(cipher.encrypt(refreshToken));
        c.setLastError(null); connections.save(c);
    }
    @Transactional public void enable(Long userId,boolean enabled) {
        var c=connections.lockByUser(userId).orElseThrow(()->new IllegalArgumentException("Conecta Gmail primero."));
        if(enabled) {
            if(c.getRefreshTokenEncrypted()==null) throw new IllegalArgumentException("Conecta Gmail primero.");
            if(accounts.findByUsuarioId(userId).stream().noneMatch(a->Boolean.TRUE.equals(a.getActiva()) && "BAC".equals(a.getBanco())
                    && a.getTipo()!=com.dev.control_financiero.enums.TipoCuenta.EFECTIVO && a.getUltimosCuatro()!=null))
                throw new IllegalArgumentException("Configura una tarjeta BAC activa antes de iniciar.");
            if(c.getImportSince()==null) c.setImportSince(Instant.now());
        }
        c.setEnabled(enabled); connections.save(c);
    }
    @Transactional public String disconnect(Long userId) {
        var c=connections.lockByUser(userId).orElseThrow(()->new IllegalArgumentException("No hay una conexión."));
        String token=c.getRefreshTokenEncrypted()==null?null:cipher.decrypt(c.getRefreshTokenEncrypted());
        c.setEnabled(false); c.setRefreshTokenEncrypted(null); connections.save(c); return token;
    }
    @Transactional public void syncResult(Long userId,String error) {
        connections.lockByUser(userId).ifPresent(c->{ c.setLastError(error); if(error==null)c.setLastSync(Instant.now()); connections.save(c); });
    }
}
