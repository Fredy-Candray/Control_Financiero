package com.dev.control_financiero.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarPerfilRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ][A-Za-zÁÉÍÓÚÜÑáéíóúüñ .'-]*$")
        String nombre,
        @NotBlank @Email @Size(max = 100)
        String correo,
        @Size(max = 15)
        String telefono,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$")
        String username
) { }
