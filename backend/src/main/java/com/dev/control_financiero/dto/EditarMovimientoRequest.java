package com.dev.control_financiero.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record EditarMovimientoRequest(@Size(max=255) String descripcion, @Positive Long categoriaId) {}
