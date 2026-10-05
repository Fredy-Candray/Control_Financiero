package com.dev.control_financiero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FotoPerfilRequest(@NotBlank @Size(max = 1_000_000) String fotoPerfil) {
}
