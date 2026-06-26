package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.CrearCuentaRequest;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.service.CuentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    @PostMapping
    public Cuenta crearCuenta(@Valid @RequestBody CrearCuentaRequest request) {
        return cuentaService.crearCuenta(request);
    }

    @GetMapping
    public List<Cuenta> listarCuentas() {
        return cuentaService.listarCuentas();
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<Cuenta> listarPorUsuario(@PathVariable Long usuarioId) {
        return cuentaService.listarPorUsuario(usuarioId);
    }
}