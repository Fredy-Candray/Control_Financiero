package com.dev.control_financiero.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="categorias_calendario", uniqueConstraints=@UniqueConstraint(name="uq_categoria_calendario_nombre",columnNames={"usuario_id","nombre_clave"}))
@IdClass(CategoriaCalendarioId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategoriaCalendario {
    @Id @Column(name="usuario_id",nullable=false) private Long usuarioId;
    @Id @Column(length=64,nullable=false) private String id;
    @org.hibernate.annotations.Nationalized @Column(length=80,nullable=false) private String nombre;
    @Column(length=7,nullable=false) private String color;
    @org.hibernate.annotations.Nationalized @Column(name="nombre_clave",length=100,nullable=false) private String nombreClave;
    @Column(nullable=false) private boolean activa;
    @Column(nullable=false) private boolean recuperada;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="usuario_id",insertable=false,updatable=false)
    private Usuario usuario;
}
