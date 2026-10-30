package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CategoriaCalendarioRepository extends JpaRepository<CategoriaCalendario,CategoriaCalendarioId> {
    List<CategoriaCalendario> findByUsuarioIdAndActivaTrueOrderByNombreAsc(Long usuarioId);
    Optional<CategoriaCalendario> findByUsuarioIdAndId(Long usuarioId,String id);
    Optional<CategoriaCalendario> findByUsuarioIdAndNombreClave(Long usuarioId,String nombreClave);
    boolean existsByUsuarioId(Long usuarioId);
}
