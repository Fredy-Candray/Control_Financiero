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
}
