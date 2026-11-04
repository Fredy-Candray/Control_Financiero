package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="conciliaciones_saldo") @Getter @Setter @NoArgsConstructor
public class ConciliacionSaldo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long usuarioId;
    @Column(nullable=false) private Long cuentaId;
    @Column(nullable=false,length=100) private String cuentaNombre;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal saldoCofi;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal saldoBanco;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal diferencia;
    @Column(nullable=false) private Instant fecha;
    @Column(length=255) private String nota;
    @Column(nullable=false,length=36) private String referenciaCliente;
}
