package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "eventos_calendario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventoCalendario {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String titulo;
    @Column(length = 300)
    private String descripcion;
    @Column(nullable = false, length = 30)
    private String tipo;
    @Column(name = "categoria_id", length = 64)
    private String categoriaId;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(nullable = false)
    private LocalTime hora;
    @Column(name = "cuenta_id")
    private Long cuentaId;
    @Column(precision = 18, scale = 2)
    private BigDecimal monto;
    @Column(nullable = false, length = 20)
    private String recurrencia;
    @Column(name = "minutos_recordatorio", nullable = false)
    private Integer minutosRecordatorio;
    @Column(nullable = false)
    private Boolean completado;
    @Column(name = "notificar_correo", nullable = false)
    private Boolean notificarCorreo;
    @Column(name = "notificar_navegador", nullable = false)
    private Boolean notificarNavegador;
    @Column(name = "notificar_whatsapp", nullable = false)
    private Boolean notificarWhatsapp;
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
