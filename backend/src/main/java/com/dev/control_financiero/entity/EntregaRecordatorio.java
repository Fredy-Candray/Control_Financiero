package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "entregas_recordatorio", uniqueConstraints = @UniqueConstraint(
        name = "uq_recordatorio_evento_ocurrencia_canal",
        columnNames = {"evento_id", "ocurrencia", "canal"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntregaRecordatorio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "evento_id", nullable = false, length = 36)
    private String eventoId;
    @Column(nullable = false)
    private LocalDateTime ocurrencia;
    @Column(nullable = false, length = 20)
    private String canal;
    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;
}
