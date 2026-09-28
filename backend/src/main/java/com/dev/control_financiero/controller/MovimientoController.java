package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.dto.MovimientoResponse;
import com.dev.control_financiero.service.MovimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    @PostMapping
    public MovimientoResponse crearMovimiento(
            @Valid @RequestBody CrearMovimientoRequest request) {

        return movimientoService.crearMovimiento(request);
    }
    @GetMapping("/usuario/{usuarioId}")
    public java.util.List<MovimientoResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return movimientoService.listarPorUsuario(usuarioId);
    }
}
