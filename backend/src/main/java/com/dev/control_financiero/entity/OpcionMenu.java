package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "opciones_menu", uniqueConstraints = {
        @UniqueConstraint(name = "uk_opciones_menu_ruta", columnNames = "ruta")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpcionMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String etiqueta;

    @Column(nullable = false, length = 120)
    private String ruta;

    @Column(nullable = false, length = 40)
    private String icono;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "opciones_menu_roles",
            joinColumns = @JoinColumn(name = "opcion_menu_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_opcion_menu_rol", columnNames = {"opcion_menu_id", "rol_id"}))
    private Set<Rol> roles = new LinkedHashSet<>();
}
