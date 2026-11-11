package com.dev.control_financiero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordInicialRequest(
        @NotBlank @Size(min = 8, max = 72) String nuevaPassword,
        @NotBlank @Size(min = 8, max = 72) String confirmarPassword) {}
