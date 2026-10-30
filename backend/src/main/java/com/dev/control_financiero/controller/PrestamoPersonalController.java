package com.dev.control_financiero.controller;

import com.dev.control_financiero.entity.PrestamoPersonal;
import com.dev.control_financiero.service.PrestamoPersonalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.List;

@RestController @RequestMapping("/api/prestamos-personales") @RequiredArgsConstructor
public class PrestamoPersonalController {
    private final PrestamoPersonalService service;
    public record Request(@NotBlank @Size(max=120) String acreedor,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal saldoPendiente,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{1,36}") String referenciaCliente) {}
    @GetMapping public List<PrestamoPersonal> listar(HttpServletRequest request) {return service.listar(user(request));}
    @PostMapping public PrestamoPersonal crear(@Valid @RequestBody Request body,HttpServletRequest request) {
        return service.crear(user(request),body.acreedor(),body.saldoPendiente(),body.referenciaCliente());
    }
    private Long user(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long id))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicia sesión nuevamente.");
        return id;
    }
}
