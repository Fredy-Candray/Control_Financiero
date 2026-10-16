package com.dev.control_financiero.repository;
import com.dev.control_financiero.entity.GmailConnection;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GmailConnectionRepository extends JpaRepository<GmailConnection,Long> {
    Optional<GmailConnection> findByUsuarioId(Long usuarioId);
    List<GmailConnection> findByEnabledTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select g from GmailConnection g where g.usuarioId = :id")
    Optional<GmailConnection> lockByUser(@Param("id") Long userId);
}
