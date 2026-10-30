package com.dev.control_financiero.dto;

import jakarta.validation.constraints.*;

public record CategoriaCalendarioRequest(
    @NotBlank @Size(max=64) @Pattern(regexp="[a-zA-Z0-9_-]+") String id,
    @NotBlank @Size(max=80) String name,
    @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String color
) {}
