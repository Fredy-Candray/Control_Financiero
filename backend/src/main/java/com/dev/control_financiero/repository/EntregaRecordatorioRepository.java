package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.EntregaRecordatorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface EntregaRecordatorioRepository extends JpaRepository<EntregaRecordatorio, Long> {
    boolean existsByEventoIdAndOcurrenciaAndCanal(String eventoId, LocalDateTime ocurrencia, String canal);
}
