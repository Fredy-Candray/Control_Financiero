package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.ResumenDashboardResponse;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.enums.TipoCuenta;
import com.dev.control_financiero.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CuentaRepository cuentaRepository;

    public ResumenDashboardResponse obtenerResumen(Long usuarioId) {

        List<Cuenta> cuentas = cuentaRepository.findByUsuarioId(usuarioId);

        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal debito = BigDecimal.ZERO;
        BigDecimal credito = BigDecimal.ZERO;
        BigDecimal deudaTarjetas = BigDecimal.ZERO;

        for (Cuenta cuenta : cuentas) {

            if (cuenta.getTipo() == TipoCuenta.EFECTIVO) {
                efectivo = efectivo.add(cuenta.getSaldoActual());
            }

            if (cuenta.getTipo() == TipoCuenta.DEBITO) {
                debito = debito.add(cuenta.getSaldoActual());
            }

            if (cuenta.getTipo() == TipoCuenta.CREDITO) {

                credito = credito.add(cuenta.getSaldoActual());

                if (cuenta.getSaldoActual().compareTo(BigDecimal.ZERO) < 0) {
                    deudaTarjetas = deudaTarjetas.add(
                            cuenta.getSaldoActual().abs()
                    );
                }
            }
        }

        BigDecimal patrimonioDisponible =
                efectivo
                        .add(debito)
                        .subtract(deudaTarjetas);

        return ResumenDashboardResponse.builder()
                .efectivo(efectivo)
                .debito(debito)
                .credito(credito)
                .deudaTarjetas(deudaTarjetas)
                .patrimonioDisponible(patrimonioDisponible)
                .build();
    }
}