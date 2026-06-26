package com.dev.control_financiero.entity;

import com.dev.control_financiero.enums.TipoCuenta;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoActual;

    @Column(nullable = false)
    private Boolean activa;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

}
