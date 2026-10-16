package com.dev.control_financiero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FotoPortadaRequest(@NotBlank @Size(max = 1_000_000) String fotoPortada,
                                 @Size(max = 15_000_000) String fotoPortadaOriginal) {}
