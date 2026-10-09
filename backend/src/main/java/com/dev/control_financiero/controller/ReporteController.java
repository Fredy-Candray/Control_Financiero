package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.PlanReporteRequest;
import com.dev.control_financiero.dto.MovimientoResponse;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.entity.PlanReporte;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {
    private final ReporteService reportes;
    private final MovimientoService movimientos;
    private final CuentaRepository cuentas;
    private final UsuarioService usuarios;
    private final MenuService menus;

    public record DatosReporte(List<Cuenta> cuentas, List<MovimientoResponse> movimientos, List<PlanReporte> planes) {}

    @GetMapping
    public DatosReporte datos(HttpServletRequest request) {
        Long userId = usuarioId(request);
        return new DatosReporte(cuentas.findByUsuarioId(userId), movimientos.listarPorUsuario(userId), reportes.listar(userId));
    }

    @PostMapping("/planes")
    public PlanReporte crear(@Valid @RequestBody PlanReporteRequest body, HttpServletRequest request) {
        return reportes.guardar(usuarioId(request), null, body);
    }

    @PutMapping("/planes/{id}")
    public PlanReporte actualizar(@PathVariable Long id, @Valid @RequestBody PlanReporteRequest body, HttpServletRequest request) {
        return reportes.guardar(usuarioId(request), id, body);
    }

    private Long usuarioId(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión nuevamente.");
        }
        var usuario = usuarios.obtenerUsuarioResponse(id);
        var rutas = menus.obtenerMenu(usuario.rol()).rutasPermitidas();
        if (!Boolean.TRUE.equals(usuario.activo()) || (!rutas.contains("/reporte") && !rutas.contains("/reportes"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a reportes.");
        }
        return id;
    }
}
