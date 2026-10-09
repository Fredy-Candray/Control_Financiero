package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.PlanReporteRequest;
import com.dev.control_financiero.entity.PlanReporte;
import com.dev.control_financiero.enums.TipoCuenta;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.repository.PlanReporteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteService {
    private final PlanReporteRepository planes;
    private final CuentaRepository cuentas;

    @Transactional(readOnly = true)
    public List<PlanReporte> listar(Long usuarioId) {
        return planes.findByUsuarioIdOrderByIdAsc(usuarioId);
    }

    @Transactional
    public PlanReporte guardar(Long usuarioId, Long id, PlanReporteRequest request) {
        if ("PRESUPUESTO".equals(request.tipo()) && (request.mes() == null || request.categoria() == null || request.categoria().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el mes y la categoría del presupuesto.");
        }
        if ("DEUDA".equals(request.tipo()) && request.cuentaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona la tarjeta de crédito.");
        }
        if (request.cuentaId() != null) {
            var cuenta = cuentas.findById(request.cuentaId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!cuenta.getUsuario().getId().equals(usuarioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if ("DEUDA".equals(request.tipo()) && cuenta.getTipo() != TipoCuenta.CREDITO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta debe ser una tarjeta de crédito.");
            }
        }
        boolean duplicado = listar(usuarioId).stream().anyMatch(plan -> !plan.getId().equals(id)
                && plan.getTipo().equals(request.tipo())
                && (("PRESUPUESTO".equals(request.tipo()) && request.mes().equals(plan.getMes()) && request.categoria().trim().equals(plan.getCategoria()))
                || ("DEUDA".equals(request.tipo()) && request.cuentaId().equals(plan.getCuentaId()))));
        if (duplicado) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya existe un registro para esta categoría y mes o tarjeta.");
        var plan = id == null ? new PlanReporte() : planes.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        plan.setUsuarioId(usuarioId);
        plan.setTipo(request.tipo());
        plan.setNombre(request.nombre().trim());
        plan.setCategoria(request.categoria() == null ? null : request.categoria().trim());
        plan.setMes(request.mes());
        plan.setCuentaId(request.cuentaId());
        plan.setMonto(request.monto());
        plan.setAcumulado(request.acumulado());
        plan.setAporteMensual(request.aporteMensual());
        plan.setTasaInteres(request.tasaInteres());
        plan.setFechaObjetivo(request.fechaObjetivo());
        return planes.save(plan);
    }
}
