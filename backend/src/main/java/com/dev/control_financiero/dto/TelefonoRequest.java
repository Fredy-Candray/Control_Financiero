package com.dev.control_financiero.dto;

import jakarta.validation.constraints.Size;

public record TelefonoRequest(@Size(max = 15) String telefono) {}
