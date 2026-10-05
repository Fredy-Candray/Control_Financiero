package com.dev.control_financiero.dto;

import java.time.LocalDateTime;

public record PerfilUsuarioResponse(Long id, String nombre, String correo, String telefono, String username,
                                    Boolean activo, LocalDateTime fechaCreacion, String rol, String fotoPerfil) {
}
