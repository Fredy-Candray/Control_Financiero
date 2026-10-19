package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.EventoCalendarioRequest;
import com.dev.control_financiero.dto.EventoCalendarioResponse;
import com.dev.control_financiero.entity.EventoCalendario;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.EventoCalendarioRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import com.dev.control_financiero.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventoCalendarioService {
    private final EventoCalendarioRepository eventos;
    private final UsuarioRepository usuarios;
    private final CuentaRepository cuentas;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<EventoCalendarioResponse> listar(Long usuarioId) {
        return eventos.findByUsuario_IdOrderByFechaAscHoraAsc(usuarioId).stream().map(this::respuesta).toList();
    }

    @Transactional
    public EventoCalendarioResponse guardar(Long usuarioId, EventoCalendarioRequest request) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        var existing = eventos.findById(request.id());
        if (existing.isPresent() && !existing.get().getUsuario().getId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la actividad.");
        }
        if (request.accountId() != null && cuentas.findById(request.accountId())
                .filter(account -> account.getUsuario().getId().equals(usuarioId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta no pertenece a este usuario.");
        }
        EventoCalendario event = existing
                .orElseGet(() -> EventoCalendario.builder().id(request.id()).usuario(usuario).fechaCreacion(LocalDateTime.now()).build());
        event.setTitulo(request.title().trim());
        event.setDescripcion(request.description() == null ? "" : request.description().trim());
        event.setTipo(request.type());
        event.setCategoriaId(request.categoryId());
        event.setFecha(request.date());
        event.setHora(request.time());
        event.setCuentaId(request.accountId());
        event.setMonto(request.amount());
        event.setRecurrencia(request.recurrence());
        event.setMinutosRecordatorio(request.reminderMinutes());
        event.setCompletado(Boolean.TRUE.equals(request.completed()));
        event.setNotificarCorreo(Boolean.TRUE.equals(request.notifyEmail()));
        event.setNotificarNavegador(!Boolean.FALSE.equals(request.notifyBrowser()));
        event.setNotificarWhatsapp(Boolean.TRUE.equals(request.notifyWhatsapp()));
        // Persist new assigned IDs instead of merge: a concurrent insert must not
        // overwrite an activity owned by another user.
        if(existing.isEmpty()) entityManager.persist(event);
        return respuesta(event);
    }

    @Transactional
    public void eliminar(Long usuarioId, String id) {
        EventoCalendario event = eventos.findById(id)
                .filter(existing -> existing.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la actividad."));
        eventos.delete(event);
    }

    private EventoCalendarioResponse respuesta(EventoCalendario e) {
        return new EventoCalendarioResponse(e.getId(), e.getTitulo(), e.getDescripcion(), e.getTipo(), e.getCategoriaId(),
                e.getFecha(), e.getHora(), e.getCuentaId(), e.getMonto(), e.getRecurrencia(), e.getMinutosRecordatorio(),
                e.getCompletado(), e.getNotificarCorreo(), e.getNotificarNavegador(), e.getNotificarWhatsapp(), e.getFechaCreacion());
    }
}
