package com.dev.control_financiero.service;

import com.dev.control_financiero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.Base64;
import org.springframework.web.client.HttpClientErrorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Service @RequiredArgsConstructor @Slf4j
public class GmailBacScheduler {
    private final GmailConnectionRepository connections;
    private final GmailBacClient gmail;
    private final GmailTokenCipher cipher;
    private final GmailConnectionService state;
    private final BacPurchaseImporter importer;
    private final BankEmailImportRepository imports;
    private final UsuarioRepository users;
    private final PayTransferImporter pay;
    @Value("${app.gmail.events-since:}") private String eventsSince="";
    @Value("${app.gmail.import-enabled:true}") private boolean importEnabled=true;

    @Scheduled(fixedDelayString="${app.gmail.poll-ms:60000}") public void poll() {
        if(!importEnabled || !gmail.configured() || !cipher.configured()) return;
        for(var connection:connections.findByEnabledTrue()) sync(connection.getUsuarioId());
    }
    public synchronized void sync(Long userId) {
        if(!importEnabled) return;
        var c=connections.findByUsuarioId(userId).orElse(null);
        if(!users.findById(userId).map(u->Boolean.TRUE.equals(u.getActivo())).orElse(false)) return;
        if(c==null || !c.isEnabled() || c.getImportSince()==null || c.getRefreshTokenEncrypted()==null) return;
        try {
            String access=gmail.accessToken(cipher.decrypt(c.getRefreshTokenEncrypted()));
            String page=null; int pages=0;
            // Query from the immutable activation time so delayed deliveries and downtime never lose messages.
            do {
                Instant cutoff=c.getImportSince();
                if(!eventsSince.isBlank() && Instant.parse(eventsSince).isAfter(cutoff)) cutoff=Instant.parse(eventsSince);
                var list=gmail.list(access,cutoff.getEpochSecond()-1,page);
                for(var entry:list.path("messages")) {
                    String id=entry.path("id").asText();
                    var prior=imports.findByUsuarioIdAndGmailMessageId(userId,id);
                    if(prior.isPresent() && ("IMPORTADO".equals(prior.get().getEstado()) || "IGNORADO".equals(prior.get().getEstado()))) continue;
                    var msg=gmail.message(access,id);
                    importer.process(userId,id,Instant.ofEpochMilli(msg.path("internalDate").asLong()),Base64.getUrlDecoder().decode(msg.path("raw").asText()));
                }
                page=list.hasNonNull("nextPageToken")?list.path("nextPageToken").asText():null;
                if(++pages>1000) throw new IllegalStateException("El buzón excedió el límite de esta sincronización.");
            } while(page!=null);
            if(pay.enabled(userId)) {
                page=null; pages=0;
                do {
                    var list=gmail.listPay(access,pay.importSince().getEpochSecond()-1,page);
                    log.info("Avisos PAY pendientes recibidos: {}",list.path("messages").size());
                    for(var entry:list.path("messages")) {
                        String id=entry.path("id").asText();
                        var prior=imports.findByUsuarioIdAndGmailMessageId(userId,id);
                        if(prior.isPresent() && ("IMPORTADO".equals(prior.get().getEstado()) || "IGNORADO".equals(prior.get().getEstado()))) continue;
                        var msg=gmail.message(access,id);
                        pay.process(userId,id,Instant.ofEpochMilli(msg.path("internalDate").asLong()),Base64.getUrlDecoder().decode(msg.path("raw").asText()));
                    }
                    page=list.hasNonNull("nextPageToken")?list.path("nextPageToken").asText():null;
                    if(++pages>1000) throw new IllegalStateException("Demasiados avisos PAY.");
                } while(page!=null);
            } else log.info("Importación PAY sin asociación configurada para este usuario.");
            state.syncResult(userId,null);
        } catch(HttpClientErrorException e) {
            state.syncResult(userId,"Google rechazó el acceso. Reconecta Gmail si el permiso expiró o fue revocado.");
        } catch(Exception e) {
            state.syncResult(userId,"No se completó la sincronización. Los mensajes pendientes se reintentarán; revisa la conexión y los saldos.");
        }
    }
}
