package com.dev.control_financiero.dto;

import java.time.LocalDateTime;

public record UsuarioResponse(Long id, String nombre, String correo, String telefono, String username,
                              Boolean activo, LocalDateTime fechaCreacion, String rol, boolean cambioPasswordPendiente) {
    public UsuarioResponse(Long id, String nombre, String correo, String telefono, String username,
                           Boolean activo, LocalDateTime fechaCreacion, String rol) {
        this(id, nombre, correo, telefono, username, activo, fechaCreacion, rol, false);
    }
}
