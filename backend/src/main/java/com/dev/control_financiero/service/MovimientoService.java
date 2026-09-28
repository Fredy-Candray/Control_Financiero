package com.dev.control_financiero.service;

import com.dev.control_financiero.enums.TipoCuenta;
import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.dto.MovimientoResponse;
import com.dev.control_financiero.entity.Categoria;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.entity.Movimiento;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.enums.TipoMovimiento;
import com.dev.control_financiero.repository.CategoriaRepository;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.repository.MovimientoRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final CuentaRepository cuentaRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public static void validarLimiteCredito(BigDecimal limiteCredito, BigDecimal saldoActual, BigDecimal monto) {
        if (limiteCredito == null || limiteCredito.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La tarjeta debe tener un límite de crédito válido.");
        }

        BigDecimal nuevoSaldo = saldoActual.subtract(monto);
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El crédito no puede quedar en negativo. Superaste el límite disponible.");
        }
    }

    @Transactional
    public MovimientoResponse crearMovimiento(CrearMovimientoRequest request) {

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Cuenta cuentaOrigen = null;
        Cuenta cuentaDestino = null;

        if (request.getCuentaOrigenId() == null) {
            throw new IllegalArgumentException("Selecciona una cuenta de origen.");
        }
        cuentaOrigen = cuentaRepository.findById(request.getCuentaOrigenId())
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        if (!cuentaOrigen.getUsuario().getId().equals(usuario.getId())) {
            throw new IllegalArgumentException("La cuenta de origen no pertenece a este usuario.");
        }
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        }
        if (request.getTipo() == TipoMovimiento.TRANSFERENCIA || request.getTipo() == TipoMovimiento.PAGO_TARJETA) {
            if (request.getCuentaDestinoId() == null) {
                throw new IllegalArgumentException("Selecciona una cuenta de destino.");
            }
            cuentaDestino = cuentaRepository.findById(request.getCuentaDestinoId())
                    .orElseThrow(() -> new RuntimeException("Cuenta destino no encontrada"));
            if (!cuentaDestino.getUsuario().getId().equals(usuario.getId())) {
                throw new IllegalArgumentException("La cuenta de destino no pertenece a este usuario.");
            }
            if (cuentaDestino.getId().equals(cuentaOrigen.getId())) {
                throw new IllegalArgumentException("El origen y el destino deben ser cuentas diferentes.");
            }
        }

        Categoria categoria = null;

        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        }

        if (request.getTipo() == TipoMovimiento.GASTO) {

            if (cuentaOrigen.getTipo() == TipoCuenta.CREDITO) {
                validarLimiteCredito(
                        cuentaOrigen.getLimiteCredito(),
                        cuentaOrigen.getSaldoActual(),
                        request.getMonto()
                );

                cuentaOrigen.setSaldoActual(
                        cuentaOrigen.getSaldoActual().subtract(request.getMonto())
                );
            } else {
                if (cuentaOrigen.getSaldoActual().compareTo(request.getMonto()) < 0) {
                    throw new RuntimeException("Saldo insuficiente");
                }

                cuentaOrigen.setSaldoActual(
                        cuentaOrigen.getSaldoActual().subtract(request.getMonto())
                );
            }

            cuentaRepository.save(cuentaOrigen);
        }

        if (request.getTipo() == TipoMovimiento.INGRESO) {

            if (cuentaOrigen.getTipo() == TipoCuenta.CREDITO) {
                throw new IllegalArgumentException("No se puede registrar un ingreso en una tarjeta de crédito.");
            }

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().add(request.getMonto())
            );

            cuentaRepository.save(cuentaOrigen);
        }

        if (request.getTipo() == TipoMovimiento.TRANSFERENCIA) {

            if (cuentaOrigen.getTipo() == TipoCuenta.CREDITO) {
                throw new IllegalArgumentException("No se puede transferir desde una tarjeta de crédito.");
            }

            if (cuentaOrigen.getSaldoActual().compareTo(request.getMonto()) < 0) {

                throw new RuntimeException("Saldo insuficiente");
            }

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().subtract(request.getMonto())
            );

            if (cuentaDestino.getTipo() == TipoCuenta.CREDITO) {
                BigDecimal limite = cuentaDestino.getLimiteCredito() == null ? BigDecimal.ZERO : cuentaDestino.getLimiteCredito();
                BigDecimal deudaActual = limite.subtract(cuentaDestino.getSaldoActual()).max(BigDecimal.ZERO);
                if (request.getMonto().compareTo(deudaActual) > 0) {
                    throw new IllegalArgumentException("El monto supera la deuda actual de la tarjeta.");
                }
                BigDecimal nuevoCredito = cuentaDestino.getSaldoActual().add(request.getMonto());
                cuentaDestino.setSaldoActual(nuevoCredito);
            } else {
                cuentaDestino.setSaldoActual(
                        cuentaDestino.getSaldoActual().add(request.getMonto())
                );
            }

            cuentaRepository.save(cuentaOrigen);
            cuentaRepository.save(cuentaDestino);
        }

        if (request.getTipo() == TipoMovimiento.PAGO_TARJETA) {

            if (cuentaOrigen.getTipo() == TipoCuenta.CREDITO) {
                throw new IllegalArgumentException("El pago de tarjeta debe salir de efectivo o débito.");
            }

            if (cuentaOrigen.getSaldoActual().compareTo(request.getMonto()) < 0) {
                throw new RuntimeException("Saldo insuficiente");
            }

            if (cuentaDestino.getTipo() != TipoCuenta.CREDITO) {
                throw new RuntimeException("La cuenta destino debe ser de tipo CREDITO");
            }

            BigDecimal limite = cuentaDestino.getLimiteCredito() == null ? BigDecimal.ZERO : cuentaDestino.getLimiteCredito();
            BigDecimal deudaActual = limite.subtract(cuentaDestino.getSaldoActual()).max(BigDecimal.ZERO);
            if (request.getMonto().compareTo(deudaActual) > 0) {
                throw new IllegalArgumentException("El pago supera la deuda actual de la tarjeta.");
            }

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().subtract(request.getMonto())
            );

            BigDecimal nuevoCreditoDisponible = cuentaDestino.getSaldoActual().add(request.getMonto());
            if (nuevoCreditoDisponible.compareTo(limite) > 0) {
                nuevoCreditoDisponible = limite;
            }
            cuentaDestino.setSaldoActual(nuevoCreditoDisponible);

            cuentaRepository.save(cuentaOrigen);
            cuentaRepository.save(cuentaDestino);
        }

        Movimiento movimiento = Movimiento.builder()
                .tipo(request.getTipo())
                .monto(request.getMonto())
                .descripcion(request.getDescripcion())
                .fechaMovimiento(LocalDateTime.now())
                .cuentaOrigen(cuentaOrigen)
                .categoria(categoria)
                .usuario(usuario)
                .cuentaDestino(cuentaDestino)
                .build();

        return MovimientoResponse.from(movimientoRepository.save(movimiento));
    }
    public List<Movimiento> listarMovimientos() {
        return movimientoRepository.findAll();
    }
    @Transactional(readOnly = true)
    public List<MovimientoResponse> listarPorUsuario(Long usuarioId) {
        return movimientoRepository.findByUsuarioId(usuarioId).stream()
                .map(MovimientoResponse::from)
                .toList();
    }
    public List<Movimiento> listarPorCuenta(Long cuentaId) {
        return movimientoRepository.findByCuentaOrigenId(cuentaId);
    }
}
