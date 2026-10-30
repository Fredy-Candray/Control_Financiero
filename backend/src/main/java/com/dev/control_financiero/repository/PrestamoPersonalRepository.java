package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.PrestamoPersonal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PrestamoPersonalRepository extends JpaRepository<PrestamoPersonal,Long> {
    List<PrestamoPersonal> findByUsuarioIdOrderByIdAsc(Long user);
    Optional<PrestamoPersonal> findByUsuarioIdAndReferenciaCliente(Long user,String reference);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrestamoPersonal p where p.id=:id and p.usuarioId=:user")
    Optional<PrestamoPersonal> findLockedByIdAndUsuarioId(@Param("id") Long id,@Param("user") Long user);
}
