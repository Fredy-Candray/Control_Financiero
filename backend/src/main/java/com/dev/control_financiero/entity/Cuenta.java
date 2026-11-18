package com.dev.control_financiero.entity;

import com.dev.control_financiero.enums.TipoCuenta;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "cuentas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 30)
    private String banco;

    @Column(length = 4)
    private String ultimosCuatro;

    @Column(length = 3)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoActual;

    @Column(precision = 18, scale = 2)
    private BigDecimal limiteCredito;

    @Column(name = "fecha_corte")
    private java.time.LocalDate fechaCorte;

    @Column(name = "fecha_pago")
    private java.time.LocalDate fechaPago;

    @Column(name = "pendiente_ciclo", precision = 18, scale = 2)
    private BigDecimal pendienteCiclo;

    @Column(nullable = false)
    private Boolean activa;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

}
