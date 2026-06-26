package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.ResumenDashboardResponse;
import com.dev.control_financiero.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumen/{usuarioId}")
    public ResumenDashboardResponse resumen(
            @PathVariable Long usuarioId) {

        return dashboardService.obtenerResumen(usuarioId);
    }
}