package com.dev.control_financiero.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String message;
    private boolean success;
    private Long userId;
    private String username;
    private String rol;
    private boolean cambioPasswordPendiente;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long versionAcceso;
}
