package com.dev.control_financiero.repository;
import com.dev.control_financiero.entity.BankEmailImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
public interface BankEmailImportRepository extends JpaRepository<BankEmailImport,Long> {
    interface MovementNotice {
        Long getId();
        String getDescripcion();
        BigDecimal getMonto();
    }
    @Query("select coalesce(max(m.id),0) from BankEmailImport b join Movimiento m on m.id=b.movimientoId where b.usuarioId=:userId and m.usuario.id=:userId and b.estado='IMPORTADO'")
    Long notificationCursor(Long userId);
    @Query("select distinct m.id as id, m.descripcion as descripcion, m.monto as monto from BankEmailImport b join Movimiento m on m.id=b.movimientoId where b.usuarioId=:userId and m.usuario.id=:userId and b.estado='IMPORTADO' and m.id>:after order by m.id")
    List<MovementNotice> notifications(Long userId, Long after, Pageable pageable);
    Optional<BankEmailImport> findByUsuarioIdAndGmailMessageId(Long userId,String messageId);
    boolean existsByUsuarioIdAndRfcMessageIdAndEstado(Long userId,String rfcId,String estado);
    List<BankEmailImport> findTop50ByUsuarioIdOrderByIdDesc(Long userId);
}
