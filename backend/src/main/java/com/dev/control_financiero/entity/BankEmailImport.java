package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="bank_email_imports",uniqueConstraints={
    @UniqueConstraint(name="uq_bank_gmail_message",columnNames={"usuario_id","gmail_message_id"})
}) @Getter @Setter @NoArgsConstructor
public class BankEmailImport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="usuario_id",nullable=false) private Long usuarioId;
    @Column(name="gmail_message_id",nullable=false,length=128) private String gmailMessageId;
    @Column(length=255) private String rfcMessageId;
    @Column(length=4) private String ultimosCuatro;
    @Column(length=255) private String comercio;
    @Column(precision=18,scale=2) private BigDecimal monto;
    private LocalDateTime fechaCompra;
    private Long movimientoId;
    @Column(nullable=false,length=30) private String estado;
    @Column(length=255) private String detalle;
    private Instant procesadoEn;
}
