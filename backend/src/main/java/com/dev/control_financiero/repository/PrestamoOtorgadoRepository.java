package com.dev.control_financiero.repository;
import com.dev.control_financiero.entity.PrestamoOtorgado;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PrestamoOtorgadoRepository extends JpaRepository<PrestamoOtorgado,Long> {
    List<PrestamoOtorgado> findByUsuarioIdOrderByFechaDesc(Long usuarioId);
    Optional<PrestamoOtorgado> findByUsuarioIdAndReferenciaCliente(Long usuarioId,String referenciaCliente);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PrestamoOtorgado> findByIdAndUsuarioId(Long id,Long usuarioId);
}
