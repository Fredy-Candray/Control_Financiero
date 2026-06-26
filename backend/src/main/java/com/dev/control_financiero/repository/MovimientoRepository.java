package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByUsuarioId(Long usuarioId);

    List<Movimiento> findByCuentaOrigenId(Long cuentaId);

}