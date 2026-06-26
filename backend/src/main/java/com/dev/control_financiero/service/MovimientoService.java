package com.dev.control_financiero.service;

import com.dev.control_financiero.enums.TipoCuenta;
import com.dev.control_financiero.dto.CrearMovimientoRequest;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final CuentaRepository cuentaRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public Movimiento crearMovimiento(CrearMovimientoRequest request) {

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Cuenta cuentaOrigen = null;
        Cuenta cuentaDestino = null;

        if (request.getCuentaOrigenId() != null) {
            cuentaOrigen = cuentaRepository.findById(request.getCuentaOrigenId())
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        }

        Categoria categoria = null;

        if (request.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(request.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        }

        if (request.getTipo() == TipoMovimiento.GASTO) {

            if (cuentaOrigen.getTipo() != TipoCuenta.CREDITO &&
                    cuentaOrigen.getSaldoActual().compareTo(request.getMonto()) < 0) {

                throw new RuntimeException("Saldo insuficiente");
            }

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().subtract(request.getMonto())
            );

            cuentaRepository.save(cuentaOrigen);
        }

        if (request.getTipo() == TipoMovimiento.INGRESO) {

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().add(request.getMonto())
            );

            cuentaRepository.save(cuentaOrigen);
        }

        if (request.getTipo() == TipoMovimiento.TRANSFERENCIA) {

            cuentaDestino = cuentaRepository.findById(request.getCuentaDestinoId())
                    .orElseThrow(() -> new RuntimeException("Cuenta destino no encontrada"));

            if (cuentaOrigen.getTipo() != TipoCuenta.CREDITO &&
                    cuentaOrigen.getSaldoActual().compareTo(request.getMonto()) < 0) {

                throw new RuntimeException("Saldo insuficiente");
            }

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().subtract(request.getMonto())
            );

            cuentaDestino.setSaldoActual(
                    cuentaDestino.getSaldoActual().add(request.getMonto())
            );

            cuentaRepository.save(cuentaOrigen);
            cuentaRepository.save(cuentaDestino);
        }

        if (request.getTipo() == TipoMovimiento.PAGO_TARJETA) {

            cuentaDestino = cuentaRepository.findById(request.getCuentaDestinoId())
                    .orElseThrow(() -> new RuntimeException("Cuenta destino no encontrada"));

            if (cuentaOrigen.getSaldoActual().compareTo(request.getMonto()) < 0) {
                throw new RuntimeException("Saldo insuficiente");
            }

            if (cuentaDestino.getTipo() != TipoCuenta.CREDITO) {
                throw new RuntimeException("La cuenta destino debe ser de tipo CREDITO");
            }

            cuentaOrigen.setSaldoActual(
                    cuentaOrigen.getSaldoActual().subtract(request.getMonto())
            );

            cuentaDestino.setSaldoActual(
                    cuentaDestino.getSaldoActual().add(request.getMonto())
            );

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

        return movimientoRepository.save(movimiento);
    }
    public List<Movimiento> listarMovimientos() {
        return movimientoRepository.findAll();
    }
    public List<Movimiento> listarPorUsuario(Long usuarioId) {
        return movimientoRepository.findByUsuarioId(usuarioId);
    }
    public List<Movimiento> listarPorCuenta(Long cuentaId) {
        return movimientoRepository.findByCuentaOrigenId(cuentaId);
    }
}