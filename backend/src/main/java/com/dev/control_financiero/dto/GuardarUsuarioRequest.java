package com.dev.control_financiero.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GuardarUsuarioRequest {
    @NotBlank @Size(max = 100)
    private String nombre;
    @NotBlank @Email @Size(max = 100)
    private String correo;
    @Size(max = 15)
    private String telefono;
    @NotBlank @Size(max = 50)
    private String username;
    private String password;
    @NotBlank
    private String rol;
    private Boolean activo;
}
