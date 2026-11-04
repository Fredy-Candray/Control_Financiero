package com.dev.control_financiero.repository;
import com.dev.control_financiero.entity.ConciliacionSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConciliacionSaldoRepository extends JpaRepository<ConciliacionSaldo,Long> {
    List<ConciliacionSaldo> findTop50ByUsuarioIdOrderByIdDesc(Long usuarioId);
    Optional<ConciliacionSaldo> findByUsuarioIdAndReferenciaCliente(Long usuarioId,String referenciaCliente);
}
