package com.dev.control_financiero.controller;
import com.dev.control_financiero.service.ControlFinancieroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController @RequestMapping("/api/control-financiero") @RequiredArgsConstructor
public class ControlFinancieroController {
    private final ControlFinancieroService service;
    private Long user(HttpServletRequest r){var s=r.getSession(false);if(s==null||!(s.getAttribute("userId") instanceof Long id))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    public record Resolucion(@NotBlank @Pattern(regexp="VINCULAR|IGNORAR") String accion,@NotBlank @Size(max=255) String nota,@Positive Long movimientoId){}
    public record Revision(@NotNull @Positive Long cuentaId,@NotNull @Digits(integer=16,fraction=2) BigDecimal saldoBanco,@Size(max=255) String nota,@NotBlank @Pattern(regexp="[a-zA-Z0-9-]{1,36}") String referenciaCliente){}
    public record Prestamo(@NotBlank @Size(max=120) String persona,@DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,@Positive Long cuentaId,@Positive Long movimientoId,LocalDate vencimiento,@Size(max=255) String nota,@NotBlank @Pattern(regexp="[a-zA-Z0-9-]{1,36}") String referenciaCliente){}
    public record Abono(@DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,@Positive Long cuentaId,@Positive Long movimientoId,@NotBlank @Pattern(regexp="[a-zA-Z0-9-]{1,36}") String referenciaCliente){}
    public record PendienteInicial(@NotBlank @Size(max=120) String persona,@NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,LocalDate vencimiento,@Size(max=255) String nota,@NotBlank @Pattern(regexp="[a-zA-Z0-9-]{1,36}") String referenciaCliente){}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String,String> invalid(IllegalArgumentException error){return java.util.Map.of("message",error.getMessage()==null?"Revisa los datos ingresados.":error.getMessage());}
    @GetMapping("/pendientes") public Object pendientes(HttpServletRequest r){return service.pendientes(user(r));}
    @PostMapping("/pendientes/{id}/resolver") public Object resolver(@PathVariable Long id,@Valid @RequestBody Resolucion b,HttpServletRequest r){return service.resolver(user(r),id,b.accion(),b.nota(),b.movimientoId());}
    @GetMapping("/conciliaciones") public Object revisiones(HttpServletRequest r){return service.conciliaciones(user(r));}
    @PostMapping("/conciliaciones") public Object conciliar(@Valid @RequestBody Revision b,HttpServletRequest r){return service.conciliar(user(r),b.cuentaId(),b.saldoBanco(),b.nota(),b.referenciaCliente());}
    @GetMapping("/prestamos") public Object prestamos(HttpServletRequest r){return service.prestamos(user(r));}
    @GetMapping("/prestamos/{id}/movimientos") public Object historial(@PathVariable Long id,HttpServletRequest r){return service.historialPrestamo(user(r),id);}
    @PostMapping("/prestamos") public Object prestar(@Valid @RequestBody Prestamo b,HttpServletRequest r){return service.prestar(user(r),b.persona(),b.monto(),b.cuentaId(),b.movimientoId(),b.vencimiento(),b.nota(),b.referenciaCliente());}
    @PostMapping("/prestamos/pendientes") public Object registrarPendiente(@Valid @RequestBody PendienteInicial b,HttpServletRequest r){return service.registrarPendiente(user(r),b.persona(),b.monto(),b.vencimiento(),b.nota(),b.referenciaCliente());}
    @PostMapping("/prestamos/{id}/abonos") public Object cobrar(@PathVariable Long id,@Valid @RequestBody Abono b,HttpServletRequest r){return service.cobrar(user(r),id,b.monto(),b.cuentaId(),b.movimientoId(),b.referenciaCliente());}
}
