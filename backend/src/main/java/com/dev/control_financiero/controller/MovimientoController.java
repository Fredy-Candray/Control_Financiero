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

    @PutMapping("/{id}/detalles")
    public MovimientoResponse editarDetalles(@PathVariable Long id,
            @Valid @RequestBody com.dev.control_financiero.dto.EditarMovimientoRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        var session=servletRequest.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long userId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return movimientoService.editarDetalles(userId,id,request);
    }

    @PostMapping
    public MovimientoResponse crearMovimiento(
            @Valid @RequestBody CrearMovimientoRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        request.setUsuarioId((Long) servletRequest.getSession(false).getAttribute("userId"));

        return movimientoService.crearMovimiento(request);
    }
    @GetMapping("/usuario/{usuarioId}")
    public java.util.List<MovimientoResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return movimientoService.listarPorUsuario(usuarioId);
    }
}
