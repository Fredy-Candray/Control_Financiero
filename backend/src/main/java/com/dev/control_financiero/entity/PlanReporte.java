package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "planes_reporte")
@Getter
@Setter
public class PlanReporte {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long usuarioId;
    @Column(nullable = false, length = 20)
    private String tipo;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(length = 100)
    private String categoria;
    @Column(length = 7)
    private String mes;
    private Long cuentaId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;
    @Column(precision = 18, scale = 2)
    private BigDecimal acumulado;
    @Column(precision = 18, scale = 2)
    private BigDecimal aporteMensual;
    @Column(precision = 7, scale = 3)
    private BigDecimal tasaInteres;
    private LocalDate fechaObjetivo;
}
