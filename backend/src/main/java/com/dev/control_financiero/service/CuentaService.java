package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.CrearCuentaRequest;
import com.dev.control_financiero.dto.ActualizarCuentaRequest;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CuentaService {

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;

    public Cuenta crearCuenta(CrearCuentaRequest request) {

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getTipo() == com.dev.control_financiero.enums.TipoCuenta.CREDITO) {
            if (request.getLimiteCredito() == null || request.getLimiteCredito().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("La tarjeta de crédito debe tener un límite válido.");
            }
        }

        Cuenta cuenta = Cuenta.builder()
                .nombre(request.getNombre())
                .tipo(request.getTipo())
                .saldoActual(request.getTipo() == com.dev.control_financiero.enums.TipoCuenta.CREDITO
                        ? request.getLimiteCredito()
                        : request.getSaldoActual())
                .limiteCredito(request.getTipo() == com.dev.control_financiero.enums.TipoCuenta.CREDITO
                        ? request.getLimiteCredito()
                        : null)
                .activa(true)
                .fechaCreacion(LocalDateTime.now())
                .usuario(usuario)
                .build();

        return cuentaRepository.save(cuenta);
    }

    @Transactional
    public Cuenta actualizarCuenta(Long cuentaId, Long usuarioId, ActualizarCuentaRequest request) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        if (!cuenta.getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("La cuenta no pertenece a este usuario.");
        }
        if (cuenta.getTipo() == com.dev.control_financiero.enums.TipoCuenta.CREDITO) {
            if (request.getLimiteCredito() == null || request.getLimiteCredito().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Ingresa un límite de crédito válido.");
            }
            if (request.getSaldoActual().compareTo(request.getLimiteCredito()) > 0) {
                throw new IllegalArgumentException("El crédito disponible no puede superar el límite.");
            }
            cuenta.setLimiteCredito(request.getLimiteCredito());
        }
        cuenta.setNombre(request.getNombre().trim());
        cuenta.setSaldoActual(request.getSaldoActual());
        return cuentaRepository.save(cuenta);
    }

    public List<Cuenta> listarCuentas() {
        return cuentaRepository.findAll();
    }

    public List<Cuenta> listarPorUsuario(Long usuarioId) {
        return cuentaRepository.findByUsuarioId(usuarioId);
    }
}
