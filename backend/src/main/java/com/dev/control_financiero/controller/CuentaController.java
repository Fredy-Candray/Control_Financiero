package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.CrearCuentaRequest;
import com.dev.control_financiero.dto.ActualizarCuentaRequest;
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

    @PutMapping("/{cuentaId}/usuario/{usuarioId}")
    public Cuenta actualizarCuenta(
            @PathVariable Long cuentaId,
            @PathVariable Long usuarioId,
            @Valid @RequestBody ActualizarCuentaRequest request) {
        return cuentaService.actualizarCuenta(cuentaId, usuarioId, request);
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<Cuenta> listarPorUsuario(@PathVariable Long usuarioId) {
        return cuentaService.listarPorUsuario(usuarioId);
    }
}
