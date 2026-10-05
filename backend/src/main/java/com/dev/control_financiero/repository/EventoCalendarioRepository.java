package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.EventoCalendario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EventoCalendarioRepository extends JpaRepository<EventoCalendario, String> {
    List<EventoCalendario> findByUsuario_IdOrderByFechaAscHoraAsc(Long usuarioId);
    @Query("select e from EventoCalendario e join fetch e.usuario u where e.completado = false and u.activo = true")
    List<EventoCalendario> findReadyForReminderDelivery();
}
