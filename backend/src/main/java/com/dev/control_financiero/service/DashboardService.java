package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.ResumenDashboardResponse;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.entity.Movimiento;
import com.dev.control_financiero.enums.TipoCuenta;
import com.dev.control_financiero.enums.TipoMovimiento;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;

    public ResumenDashboardResponse obtenerResumen(Long usuarioId) {
        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal debito = BigDecimal.ZERO;
        BigDecimal creditoDisponible = BigDecimal.ZERO;
        BigDecimal deudaTarjetas = BigDecimal.ZERO;
        List<Cuenta> cuentas = cuentaRepository.findByUsuarioId(usuarioId);
        Set<Long> tarjetasSinLimiteIds = cuentas.stream()
                .filter(c -> c.getTipo() == TipoCuenta.CREDITO && c.getLimiteCredito() == null)
                .map(Cuenta::getId)
                .collect(Collectors.toSet());

        for (Cuenta cuenta : cuentas) {
            BigDecimal saldo = cuenta.getSaldoActual() == null ? BigDecimal.ZERO : cuenta.getSaldoActual();
            if (cuenta.getTipo() == TipoCuenta.EFECTIVO) {
                efectivo = efectivo.add(saldo);
            } else if (cuenta.getTipo() == TipoCuenta.DEBITO) {
                debito = debito.add(saldo);
            } else if (cuenta.getTipo() == TipoCuenta.CREDITO) {
                if (cuenta.getLimiteCredito() != null) {
                    creditoDisponible = creditoDisponible.add(saldo);
                    deudaTarjetas = deudaTarjetas.add(cuenta.getLimiteCredito().subtract(saldo).max(BigDecimal.ZERO));
                }
            }
        }

        BigDecimal ingresosMes = BigDecimal.ZERO;
        BigDecimal gastosMes = BigDecimal.ZERO;
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate inicioMesSiguiente = inicioMes.plusMonths(1);
        List<Movimiento> movimientos = movimientoRepository.findByUsuarioId(usuarioId);
        Map<Long, BigDecimal> cargosLegados = new HashMap<>();
        Map<Long, BigDecimal> pagosLegados = new HashMap<>();
        for (Movimiento movimiento : movimientos) {
            if (movimiento.getCuentaOrigen() != null && tarjetasSinLimiteIds.contains(movimiento.getCuentaOrigen().getId()) && movimiento.getTipo() == TipoMovimiento.GASTO) {
                Long tarjetaId = movimiento.getCuentaOrigen().getId();
                cargosLegados.merge(tarjetaId, movimiento.getMonto(), BigDecimal::add);
            }
            if (movimiento.getCuentaDestino() != null && tarjetasSinLimiteIds.contains(movimiento.getCuentaDestino().getId()) &&
                    (movimiento.getTipo() == TipoMovimiento.PAGO_TARJETA || movimiento.getTipo() == TipoMovimiento.TRANSFERENCIA)) {
                Long tarjetaId = movimiento.getCuentaDestino().getId();
                pagosLegados.merge(tarjetaId, movimiento.getMonto(), BigDecimal::add);
            }
            if (movimiento.getFechaMovimiento() == null) continue;
            LocalDate fecha = movimiento.getFechaMovimiento().toLocalDate();
            if (fecha.isBefore(inicioMes) || !fecha.isBefore(inicioMesSiguiente)) continue;
            if (movimiento.getTipo() == TipoMovimiento.INGRESO) {
                ingresosMes = ingresosMes.add(movimiento.getMonto());
            } else if (movimiento.getTipo() == TipoMovimiento.GASTO) {
                gastosMes = gastosMes.add(movimiento.getMonto());
            }
        }
        for (Long tarjetaId : tarjetasSinLimiteIds) {
            BigDecimal cargos = cargosLegados.getOrDefault(tarjetaId, BigDecimal.ZERO);
            BigDecimal pagos = pagosLegados.getOrDefault(tarjetaId, BigDecimal.ZERO);
            deudaTarjetas = deudaTarjetas.add(cargos.subtract(pagos).max(BigDecimal.ZERO));
        }

        return ResumenDashboardResponse.builder()
                .efectivo(efectivo)
                .debito(debito)
                .credito(creditoDisponible)
                .deudaTarjetas(deudaTarjetas)
                .patrimonioDisponible(efectivo.add(debito).subtract(deudaTarjetas))
                .ingresosMes(ingresosMes)
                .gastosMes(gastosMes)
                .tarjetasSinLimite(tarjetasSinLimiteIds.size())
                .build();
    }
}
