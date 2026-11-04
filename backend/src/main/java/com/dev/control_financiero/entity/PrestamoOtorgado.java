package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="prestamos_otorgados") @Getter @Setter @NoArgsConstructor
public class PrestamoOtorgado {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long usuarioId;
    @org.hibernate.annotations.Nationalized @Column(nullable=false,length=120) private String persona;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal monto;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal pendiente;
    @Column(nullable=false) private LocalDateTime fecha;
    private LocalDate vencimiento;
    @Column(length=255) private String nota;
    private Long movimientoOrigenId;
    @Column(nullable=false) private boolean registroInicial;
    @Column(nullable=false,length=36) private String referenciaCliente;
}
