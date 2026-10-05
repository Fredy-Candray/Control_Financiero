package com.dev.control_financiero.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminUsuariosWebConfig implements WebMvcConfigurer {

    private final AdminUsuariosInterceptor adminUsuariosInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminUsuariosInterceptor)
                .addPathPatterns("/api/usuarios", "/api/usuarios/**", "/api/menu-admin", "/api/menu-admin/**");
    }
}
