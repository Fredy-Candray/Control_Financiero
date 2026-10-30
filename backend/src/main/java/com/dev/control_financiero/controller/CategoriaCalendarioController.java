package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.service.CategoriaCalendarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController @RequestMapping("/api/calendario/categorias") @RequiredArgsConstructor
public class CategoriaCalendarioController {
    private final CategoriaCalendarioService service;
    public record Migration(@NotNull @Size(max=500) List<@Valid CategoriaCalendarioRequest> categories) {}
    @GetMapping public List<CategoriaCalendarioResponse> listar(HttpServletRequest request) {return service.listar(user(request));}
    @PostMapping("/migracion") public List<CategoriaCalendarioResponse> importar(@Valid @RequestBody Migration body,HttpServletRequest request) {
        return service.importar(user(request),body.categories());
    }
    @PutMapping("/{id}") public CategoriaCalendarioResponse guardar(@PathVariable String id,@Valid @RequestBody CategoriaCalendarioRequest body,HttpServletRequest request) {
        if(!id.equals(body.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El identificador no coincide.");
        return service.guardar(user(request),body);
    }
    @DeleteMapping("/{id}") public List<CategoriaCalendarioResponse> eliminar(@PathVariable String id,@RequestParam String replacement,HttpServletRequest request) {
        return service.eliminar(user(request),id,replacement);
    }
    private Long user(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicia sesión nuevamente.");
        return id;
    }
}
