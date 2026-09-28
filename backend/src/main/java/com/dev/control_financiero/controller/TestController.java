package com.dev.control_financiero.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        Map<String, String> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("message", "pong");
        return resp;
    }

    @GetMapping("/dashboard-sample")
    public Map<String, Object> dashboardSample() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("efectivo", new BigDecimal("100.00"));
        resp.put("debito", new BigDecimal("200.00"));
        resp.put("credito", new BigDecimal("300.00"));
        resp.put("deudaTarjetas", new BigDecimal("50.00"));
        resp.put("patrimonioDisponible", new BigDecimal("250.00"));
        return resp;
    }

    @GetMapping("/movimientos-sample")
    public java.util.List<Map<String, Object>> movimientosSample() {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();

        Map<String, Object> m1 = new HashMap<>();
        m1.put("id", 1);
        m1.put("tipo", "INGRESO");
        m1.put("monto", new BigDecimal("150.00"));
        m1.put("descripcion", "Salario");
        m1.put("fechaMovimiento", java.time.LocalDateTime.now().toString());
        list.add(m1);

        Map<String, Object> m2 = new HashMap<>();
        m2.put("id", 2);
        m2.put("tipo", "GASTO");
        m2.put("monto", new BigDecimal("45.50"));
        m2.put("descripcion", "Supermercado");
        m2.put("fechaMovimiento", java.time.LocalDateTime.now().minusDays(1).toString());
        list.add(m2);

        return list;
    }
}
