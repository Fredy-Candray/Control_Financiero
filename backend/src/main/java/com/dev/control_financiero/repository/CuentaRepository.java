package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByUsuarioId(Long usuarioId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cuenta c where c.id = :id")
    java.util.Optional<Cuenta> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

}
