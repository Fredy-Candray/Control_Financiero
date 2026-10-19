package com.dev.control_financiero.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class CloudArchiveToolTest {
    @TempDir Path folder;
    @Test void encryptedBackupRestoresUnicodeAndRejectsTampering() throws Exception {
        byte[] key=new byte[32]; new SecureRandom().nextBytes(key);
        var original=CloudArchiveTool.JSON.createObjectNode().put("nombre","Tarjeta débito").put("saldo","1500.00");
        Path file=folder.resolve("backup.enc");
        CloudArchiveTool.writeArchive(original,file,key);
        assertEquals(original,CloudArchiveTool.readArchive(file,key));
        assertThrows(FileAlreadyExistsException.class,()->CloudArchiveTool.writeArchive(original,file,key));
        byte[] bytes=Files.readAllBytes(file); bytes[bytes.length-1]^=1; Files.write(file,bytes);
        assertThrows(javax.crypto.AEADBadTagException.class,()->CloudArchiveTool.readArchive(file,key));
    }
    @Test void differentKeyCannotReadBackup() throws Exception {
        byte[] key=new byte[32],other=new byte[32]; other[0]=1;
        Path file=folder.resolve("backup.enc");
        CloudArchiveTool.writeArchive(CloudArchiveTool.JSON.createObjectNode(),file,key);
        assertThrows(javax.crypto.AEADBadTagException.class,()->CloudArchiveTool.readArchive(file,other));
    }
}
