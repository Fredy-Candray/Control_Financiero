package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "menus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String titulo;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    private Boolean activo;

    @Builder.Default
    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    private List<OpcionMenu> opciones = new ArrayList<>();
}
