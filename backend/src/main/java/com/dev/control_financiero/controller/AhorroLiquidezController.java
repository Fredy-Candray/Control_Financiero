package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.AhorroRequests.*;
import com.dev.control_financiero.service.AhorroLiquidezService;
import com.dev.control_financiero.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/api/ahorro-liquidez") @RequiredArgsConstructor
public class AhorroLiquidezController {
    private final AhorroLiquidezService service;
    private final UsuarioRepository usuarios;
    private Long user(HttpServletRequest r){
        var session=r.getSession(false);
        if(session==null||!(session.getAttribute("userId") instanceof Long id)||!usuarios.existsByIdAndActivoTrue(id))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicia sesión nuevamente.");
        return id;
    }
    /** Exact decimal strings and explicit dates; never serializes persistence entities. */
    public static Object json(Object data){
        if(data instanceof BigDecimal n)return n.toPlainString();
        if(data instanceof java.sql.Timestamp t)return t.toLocalDateTime().toString();
        if(data instanceof java.sql.Date d)return d.toLocalDate().toString();
        if(data instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,v)->out.put(k.toString(),json(v)));return out;}
        if(data instanceof Collection<?> list)return list.stream().map(AhorroLiquidezController::json).toList();
        return data;
    }
    @GetMapping("/resumen") public Object resumen(@RequestParam String mes,HttpServletRequest r){return json(service.datos(user(r),mes));}
    @PostMapping("/metas") public Object meta(@Valid @RequestBody Meta req,HttpServletRequest r){return Map.of("id",service.meta(user(r),null,req));}
    @PutMapping("/metas/{id}") public Object meta(@PathVariable Long id,@Valid @RequestBody Meta req,HttpServletRequest r){return Map.of("id",service.meta(user(r),id,req));}
    @DeleteMapping("/metas/{id}") public void meta(@PathVariable Long id,@RequestParam Long version,HttpServletRequest r){service.archivarMeta(user(r),id,version);}
    @PostMapping("/metas/{id}/reservas") public Object reservar(@PathVariable Long id,@Valid @RequestBody Reserva req,HttpServletRequest r){return Map.of("id",service.asignar(user(r),id,req,false));}
    @PostMapping("/metas/{id}/liberaciones") public Object liberar(@PathVariable Long id,@Valid @RequestBody Reserva req,HttpServletRequest r){return Map.of("id",service.asignar(user(r),id,req,true));}
    @GetMapping("/metas/{id}/reservas") public Object historial(@PathVariable Long id,@RequestParam(defaultValue="0") int pagina,HttpServletRequest r){return json(service.historial(user(r),id,pagina));}
    @PostMapping("/fijos") public Object fijo(@Valid @RequestBody Fijo req,HttpServletRequest r){return Map.of("id",service.fijo(user(r),null,req));}
    @PutMapping("/fijos/{id}") public Object fijo(@PathVariable Long id,@Valid @RequestBody Fijo req,HttpServletRequest r){return Map.of("id",service.fijo(user(r),id,req));}
    @DeleteMapping("/fijos/{id}") public void fijo(@PathVariable Long id,@RequestParam Long version,HttpServletRequest r){service.archivarFijo(user(r),id,version);}
    @PostMapping("/compromisos/{id}/cancelacion") public void cancelar(@PathVariable Long id,@Valid @RequestBody Revision req,HttpServletRequest r){service.cancelarCompromiso(user(r),id,req);}
    @PutMapping("/compromisos/{id}") public void ajustar(@PathVariable Long id,@Valid @RequestBody CambioCompromiso req,HttpServletRequest r){service.ajustarCompromiso(user(r),id,req);}
    @GetMapping("/movimientos-disponibles") public Object movimientos(HttpServletRequest r){return json(service.movimientosParaFijos(user(r)));}
    @PostMapping("/compromisos/{id}/pagos") public Object pago(@PathVariable Long id,@Valid @RequestBody Pago req,HttpServletRequest r){return Map.of("id",service.aplicarPago(user(r),id,req));}
    @GetMapping("/compromisos/{id}/pagos") public Object pagos(@PathVariable Long id,HttpServletRequest r){return json(service.pagos(user(r),id));}
    @DeleteMapping("/compromisos/{id}/pagos/{pago}") public void anular(@PathVariable Long id,@PathVariable Long pago,@Valid @RequestBody Revision req,HttpServletRequest r){service.anularPago(user(r),id,pago,req);}
    @PostMapping("/presupuestos") public Object presupuesto(@Valid @RequestBody Presupuesto req,HttpServletRequest r){return Map.of("id",service.presupuesto(user(r),null,req));}
    @PutMapping("/presupuestos/{id}") public Object presupuesto(@PathVariable Long id,@Valid @RequestBody Presupuesto req,HttpServletRequest r){return Map.of("id",service.presupuesto(user(r),id,req));}
    @DeleteMapping("/presupuestos/{id}") public void presupuesto(@PathVariable Long id,@RequestParam Long version,HttpServletRequest r){service.archivarPresupuesto(user(r),id,version);}
    @GetMapping("/alertas") public Object alertas(@RequestParam(defaultValue="false") boolean nuevas,HttpServletRequest r){return json(service.alertas(user(r),nuevas));}
    @PatchMapping("/alertas/{id}/lectura") public void lectura(@PathVariable Long id,HttpServletRequest r){service.leerAlerta(user(r),id);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalido(IllegalArgumentException ex){return Map.of("message",Objects.requireNonNullElse(ex.getMessage(),"Datos no válidos."));}
    @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> duplicado(){return Map.of("message","Ya existe un registro para esta categoría y mes, o la operación cambió. Actualiza los datos.");}
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> estado(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(ex.getReason(),"No se pudo completar la operación.")));}
}
