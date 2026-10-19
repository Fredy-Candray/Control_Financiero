package com.dev.control_financiero.migration;

import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.file.Path;

@Component @Profile("migration") @Order(3)
public class CloudMigrationRunner implements ApplicationRunner {
    private final com.dev.control_financiero.repository.GmailConnectionRepository connections;
    private final com.dev.control_financiero.service.GmailBacClient gmail;
    private final com.dev.control_financiero.service.GmailTokenCipher cipher;
    public CloudMigrationRunner(com.dev.control_financiero.repository.GmailConnectionRepository connections,
            com.dev.control_financiero.service.GmailBacClient gmail,
            com.dev.control_financiero.service.GmailTokenCipher cipher) {
        this.connections=connections; this.gmail=gmail; this.cipher=cipher;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        try(var connection=CloudArchiveTool.postgres()) {
            CloudArchiveTool.importArchive(connection,Path.of("/app/cofi-backup.enc"));
        }
        if (gmail.configured() && cipher.configured()) {
            for(var connection:connections.findByEnabledTrue()) {
                try {
                    String access=gmail.accessToken(cipher.decrypt(connection.getRefreshTokenEncrypted()));
                    String email=gmail.profile(access).path("emailAddress").asText();
                    if(!email.equalsIgnoreCase(connection.getCorreo())) throw new IllegalStateException();
                    System.out.println("MIGRACION_GMAIL_OK: permiso de lectura verificado; importación aún desactivada.");
                } catch(Exception error) {
                    System.out.println("MIGRACION_GMAIL_REQUIERE_RECONEXION");
                }
            }
        }
    }
}
