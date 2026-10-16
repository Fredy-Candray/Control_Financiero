package com.dev.control_financiero.repository;
import com.dev.control_financiero.entity.BankEmailImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BankEmailImportRepository extends JpaRepository<BankEmailImport,Long> {
    Optional<BankEmailImport> findByUsuarioIdAndGmailMessageId(Long userId,String messageId);
    boolean existsByUsuarioIdAndRfcMessageIdAndEstado(Long userId,String rfcId,String estado);
    List<BankEmailImport> findTop50ByUsuarioIdOrderByIdDesc(Long userId);
}
