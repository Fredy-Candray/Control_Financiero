package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name="prestamos_personales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PrestamoPersonal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="usuario_id",nullable=false) private Long usuarioId;
    @org.hibernate.annotations.Nationalized @Column(nullable=false,length=120) private String acreedor;
    @Column(name="saldo_inicial",nullable=false,precision=18,scale=2) private BigDecimal saldoInicial;
    @Column(name="saldo_pendiente",nullable=false,precision=18,scale=2) private BigDecimal saldoPendiente;
    @Column(name="tasa_interes",nullable=false,precision=8,scale=3) private BigDecimal tasaInteres;
    @Column(name="fecha_registro",nullable=false) private LocalDateTime fechaRegistro;
    @Column(name="referencia_cliente",nullable=false,length=36) private String referenciaCliente;
    @JsonIgnore @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="usuario_id",insertable=false,updatable=false) private Usuario usuario;
}
