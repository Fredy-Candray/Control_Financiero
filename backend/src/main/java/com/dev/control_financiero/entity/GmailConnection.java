package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity @Table(name="gmail_connections") @Getter @Setter @NoArgsConstructor
public class GmailConnection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private Long usuarioId;
    @Column(unique=true,length=254) private String correo;
    @JsonIgnore @Column(length=4000) private String refreshTokenEncrypted;
    private boolean enabled;
    private Instant importSince;
    private Instant lastSync;
    @Column(length=255) private String lastError;
}
