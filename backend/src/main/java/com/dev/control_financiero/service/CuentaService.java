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
    @org.springframework.beans.factory.annotation.Autowired private AhorroEventos ahorroEventos;

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public Cuenta crearCuenta(CrearCuentaRequest request) {

        Usuario usuario = usuarioRepository.findLockedById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getTipo() == com.dev.control_financiero.enums.TipoCuenta.CREDITO) {
            if (request.getLimiteCredito() == null || request.getLimiteCredito().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("La tarjeta de crédito debe tener un límite válido.");
            }
            if (request.getSaldoActual().compareTo(request.getLimiteCredito()) > 0) {
                throw new IllegalArgumentException("El disponible no puede superar el límite.");
            }
        }
        validarTarjeta(request.getUsuarioId(), null, request.getBanco(), request.getUltimosCuatro());
        if (request.getTipo() == com.dev.control_financiero.enums.TipoCuenta.EFECTIVO
                && request.getBanco() != null && !request.getBanco().isBlank())
            throw new IllegalArgumentException("Solo débito o crédito pueden vincularse a alertas bancarias.");

        Cuenta cuenta = Cuenta.builder()
                .nombre(request.getNombre())
                .tipo(request.getTipo())
                .saldoActual(request.getSaldoActual())
                .banco(request.getBanco())
                .ultimosCuatro(request.getUltimosCuatro())
                .moneda("USD")
                .limiteCredito(request.getTipo() == com.dev.control_financiero.enums.TipoCuenta.CREDITO
                        ? request.getLimiteCredito()
                        : null)
                .activa(true)
                .fechaCreacion(LocalDateTime.now())
                .usuario(usuario)
                .build();

        var guardada=cuentaRepository.save(cuenta);
        if(ahorroEventos!=null)ahorroEventos.registrar(cuenta.getUsuario().getId());
        return guardada;
    }

    @Transactional
    public Cuenta actualizarCuenta(Long cuentaId, Long usuarioId, ActualizarCuentaRequest request) {
        usuarioRepository.findLockedById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        Cuenta cuenta = cuentaRepository.findLockedById(cuentaId)
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
        if (request.getBanco() != null && request.getUltimosCuatro() != null) {
            if (cuenta.getTipo() == com.dev.control_financiero.enums.TipoCuenta.EFECTIVO && !request.getBanco().isBlank())
                throw new IllegalArgumentException("Solo débito o crédito pueden vincularse a alertas bancarias.");
            validarTarjeta(usuarioId, cuentaId, request.getBanco(), request.getUltimosCuatro());
            cuenta.setBanco(request.getBanco());
            cuenta.setUltimosCuatro(request.getUltimosCuatro());
        }
        cuenta.setMoneda("USD");
        cuenta.setSaldoActual(request.getSaldoActual());
        if (request.getActiva() != null) {
            cuenta.setActiva(request.getActiva());
        }
        var guardada=cuentaRepository.save(cuenta);
        if(ahorroEventos!=null)ahorroEventos.registrar(cuenta.getUsuario().getId());
        return guardada;
    }

    @Transactional
    public Cuenta guardarCiclo(Long cuentaId, Long usuarioId, com.dev.control_financiero.dto.CicloTarjetaRequest ciclo) {
        Cuenta cuenta = cuentaRepository.findLockedById(cuentaId)
                .filter(c -> c.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "No se encontró la cuenta."));
        if (cuenta.getTipo() != com.dev.control_financiero.enums.TipoCuenta.CREDITO
                || ciclo.fechaPago().isBefore(ciclo.fechaCorte())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Selecciona una tarjeta de crédito y una fecha de pago igual o posterior al corte.");
        }
        cuenta.setFechaCorte(ciclo.fechaCorte());
        cuenta.setFechaPago(ciclo.fechaPago());
        cuenta.setPendienteCiclo(ciclo.pendienteCiclo());
        return cuentaRepository.save(cuenta);
    }

    public List<Cuenta> listarCuentas() {
        return cuentaRepository.findAll();
    }

    private void validarTarjeta(Long userId, Long cuentaId, String banco, String digits) {
        if (banco == null || banco.isBlank()) {
            if (digits != null && !digits.isBlank()) throw new IllegalArgumentException("Selecciona el banco.");
            return;
        }
        if (!"BAC".equals(banco) || digits == null || !digits.matches("[0-9]{4}"))
            throw new IllegalArgumentException("Configura BAC y los últimos cuatro dígitos.");
        if (cuentaRepository.findByUsuarioId(userId).stream().anyMatch(c -> !c.getId().equals(cuentaId)
                && banco.equals(c.getBanco()) && digits.equals(c.getUltimosCuatro())))
            throw new IllegalArgumentException("Esta tarjeta ya está vinculada a otra cuenta.");
    }

    public List<Cuenta> listarPorUsuario(Long usuarioId) {
        return cuentaRepository.findByUsuarioId(usuarioId);
    }
}
