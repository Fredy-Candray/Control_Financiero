package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.PlanReporte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlanReporteRepository extends JpaRepository<PlanReporte, Long> {
    List<PlanReporte> findByUsuarioIdOrderByIdAsc(Long usuarioId);
    Optional<PlanReporte> findByIdAndUsuarioId(Long id, Long usuarioId);
}
