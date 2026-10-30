package com.dev.control_financiero.entity;

import lombok.*;
import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class CategoriaCalendarioId implements Serializable {
    private Long usuarioId;
    private String id;
}
