package com.dev.control_financiero.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CrearCategoriaRequest {

    @NotBlank
    private String nombre;

}