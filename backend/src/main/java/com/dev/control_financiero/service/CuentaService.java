package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.CrearCuentaRequest;
import com.dev.control_financiero.entity.Cuenta;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CuentaService {

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;

    public Cuenta crearCuenta(CrearCuentaRequest request) {

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Cuenta cuenta = Cuenta.builder()
                .nombre(request.getNombre())
                .tipo(request.getTipo())
                .saldoActual(request.getSaldoActual())
                .activa(true)
                .fechaCreacion(LocalDateTime.now())
                .usuario(usuario)
                .build();

        return cuentaRepository.save(cuenta);
    }

    public List<Cuenta> listarCuentas() {
        return cuentaRepository.findAll();
    }

    public List<Cuenta> listarPorUsuario(Long usuarioId) {
        return cuentaRepository.findByUsuarioId(usuarioId);
    }
}
