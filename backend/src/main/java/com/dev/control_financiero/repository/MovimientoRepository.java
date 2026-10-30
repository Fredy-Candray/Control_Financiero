package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Movimiento> findByIdAndUsuario_Id(Long id, Long usuarioId);
    java.util.Optional<Movimiento> findByUsuario_IdAndReferenciaCliente(Long user,String reference);

    List<Movimiento> findByUsuarioId(Long usuarioId);

    List<Movimiento> findByCuentaOrigenId(Long cuentaId);

}
