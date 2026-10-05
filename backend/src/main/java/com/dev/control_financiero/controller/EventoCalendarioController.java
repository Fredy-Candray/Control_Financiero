package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.EventoCalendarioRequest;
import com.dev.control_financiero.dto.EventoCalendarioResponse;
import com.dev.control_financiero.service.EventoCalendarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/calendario/eventos")
@RequiredArgsConstructor
public class EventoCalendarioController {
    private final EventoCalendarioService service;

    @GetMapping
    public List<EventoCalendarioResponse> listar(HttpServletRequest request) {
        return service.listar(usuarioId(request));
    }

    @PutMapping("/{id}")
    public EventoCalendarioResponse guardar(@PathVariable String id, @Valid @RequestBody EventoCalendarioRequest event,
                                            HttpServletRequest request) {
        if (!id.equals(event.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador no coincide.");
        return service.guardar(usuarioId(request), event);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id, HttpServletRequest request) {
        service.eliminar(usuarioId(request), id);
    }

    private Long usuarioId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
        }
        return userId;
    }
}
