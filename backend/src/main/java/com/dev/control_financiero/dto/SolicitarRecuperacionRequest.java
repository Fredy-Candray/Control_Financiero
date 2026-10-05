package com.dev.control_financiero.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarRecuperacionRequest(
        @NotBlank @Email @Size(max = 100) String correo
) {}
