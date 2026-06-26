package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByUsuarioId(Long usuarioId);

}
