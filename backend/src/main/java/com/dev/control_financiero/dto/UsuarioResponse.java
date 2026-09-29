package com.dev.control_financiero.dto;

import java.time.LocalDateTime;

public record UsuarioResponse(Long id, String nombre, String correo, String username,
                              Boolean activo, LocalDateTime fechaCreacion, String rol) {
}
