package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.MenuAdminRequest;
import com.dev.control_financiero.dto.MenuAdminResponse;
import com.dev.control_financiero.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu-admin")
@RequiredArgsConstructor
public class MenuAdminController {
    private final MenuService menuService;

    @GetMapping
    public MenuAdminResponse obtenerConfiguracion() {
        return menuService.obtenerConfiguracion();
    }

    @PutMapping
    public MenuAdminResponse guardarConfiguracion(@RequestBody MenuAdminRequest request) {
        return menuService.guardarConfiguracion(request);
    }
}
