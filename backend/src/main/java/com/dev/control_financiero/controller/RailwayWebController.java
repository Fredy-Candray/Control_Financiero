package com.dev.control_financiero.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Map;

/** Serves the compiled Angular app from the same origin as the trial API. */
@Controller
@Profile("railway")
public class RailwayWebController {
    @GetMapping({"/", "/login", "/register", "/reset-password", "/dashboard", "/cuentas",
            "/movimientos", "/calendario", "/reporte", "/reportes", "/perfil", "/usuarios", "/administrar-menu"})
    public String application() {
        return "forward:/index.html";
    }

    @GetMapping("/api/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
