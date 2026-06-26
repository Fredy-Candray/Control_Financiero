package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.entity.Movimiento;
import com.dev.control_financiero.service.MovimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    @PostMapping
    public Movimiento crearMovimiento(
            @Valid @RequestBody CrearMovimientoRequest request) {

        return movimientoService.crearMovimiento(request);
    }
    @GetMapping
    public List<Movimiento> listarMovimientos() {
        return movimientoService.listarMovimientos();
    }

    @GetMapping("/cuenta/{cuentaId}")
    public List<Movimiento> listarPorCuenta(@PathVariable Long cuentaId) {
        return movimientoService.listarPorCuenta(cuentaId);
    }
}
