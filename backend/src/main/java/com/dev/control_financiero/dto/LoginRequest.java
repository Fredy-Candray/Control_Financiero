package com.dev.control_financiero.dto;

import lombok.Data;

@Data
public class LoginRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=50)
    private String username;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=128)
    private String password;
    private Boolean rememberMe;
}
