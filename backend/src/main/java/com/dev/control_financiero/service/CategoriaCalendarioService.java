package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class CategoriaCalendarioService {
    private final CategoriaCalendarioRepository categorias;
    private final UsuarioRepository usuarios;
    private final EventoCalendarioRepository eventos;

    @Transactional
    public List<CategoriaCalendarioResponse> listar(Long user) {
        lock(user);
        initialize(user);
        return active(user).stream().map(this::response).toList();
    }

    /** Missing keys are imported once per browser; server edits and tombstones always win. */
    @Transactional
    public List<CategoriaCalendarioResponse> importar(Long user,List<CategoriaCalendarioRequest> legacy) {
        lock(user);
        for(var request:legacy) {
            var existing=categorias.findByUsuarioIdAndId(user,request.id());
            if(existing.isPresent() && (!existing.get().isActiva() || !existing.get().isRecuperada())) continue;
            save(user,request,existing.orElse(null));
        }
        initialize(user);
        return active(user).stream().map(this::response).toList();
    }

    @Transactional
    public CategoriaCalendarioResponse guardar(Long user,CategoriaCalendarioRequest request) {
        lock(user);
        var existing=categorias.findByUsuarioIdAndId(user,request.id()).orElse(null);
        if(existing!=null && !existing.isActiva()) throw error(HttpStatus.CONFLICT,"Esta categoría ya fue eliminada. Actualiza el calendario.");
        return response(save(user,request,existing));
    }

    @Transactional
    public List<CategoriaCalendarioResponse> eliminar(Long user,String id,String replacement) {
        lock(user);
        var category=required(user,id);
        if(id.equals(replacement)) throw error(HttpStatus.BAD_REQUEST,"Selecciona otra categoría de reemplazo.");
        required(user,replacement);
        // Both reassignment and soft delete commit together. Tombstones prevent stale browsers from resurrecting deleted categories.
        eventos.reasignarCategoria(user,id,replacement);
        category.setActiva(false);
        category.setNombreClave("~"+id);
        categorias.saveAndFlush(category);
        return active(user).stream().map(this::response).toList();
    }

    @Transactional
    public String validarParaEvento(Long user,String id,String type) {
        lock(user);
        initialize(user);
        if(id==null || id.isBlank()) {
            String preferred="PAGO_TARJETA".equals(type)?"payment":"meeting";
            return active(user).stream().filter(c->c.getId().equals(preferred)).findFirst()
                    .orElseGet(()->active(user).get(0)).getId();
        }
        return required(user,id).getId();
    }

    private CategoriaCalendario save(Long user,CategoriaCalendarioRequest request,CategoriaCalendario existing) {
        String name=request.name().strip();
        if(name.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"Escribe un nombre para la categoría.");
        String key=name.toLowerCase(Locale.ROOT);
        categorias.findByUsuarioIdAndNombreClave(user,key).filter(c->!c.getId().equals(request.id()))
                .ifPresent(c->{throw error(HttpStatus.CONFLICT,"Ya existe una categoría con ese nombre.");});
        var category=existing==null?CategoriaCalendario.builder().usuarioId(user).id(request.id()).build():existing;
        category.setNombre(name);category.setNombreClave(key);category.setColor(request.color().toLowerCase(Locale.ROOT));
        category.setActiva(true);category.setRecuperada(false);
        return categorias.saveAndFlush(category);
    }

    private void initialize(Long user) {
        if(categorias.existsByUsuarioId(user)) return;
        for(var item:List.of(new CategoriaCalendarioRequest("meeting","Reunión","#16a06a"),
                new CategoriaCalendarioRequest("payment","Pago de tarjeta","#ef3340"),
                new CategoriaCalendarioRequest("task","Tarea","#f0c419"),new CategoriaCalendarioRequest("event","Evento","#f28c28"))) save(user,item,null);
    }
    private List<CategoriaCalendario> active(Long user) {return categorias.findByUsuarioIdAndActivaTrueOrderByNombreAsc(user);}
    private CategoriaCalendario required(Long user,String id) {
        return categorias.findByUsuarioIdAndId(user,id).filter(CategoriaCalendario::isActiva)
                .orElseThrow(()->error(HttpStatus.NOT_FOUND,"La categoría no existe para este usuario. Actualiza el calendario."));
    }
    private void lock(Long user) {usuarios.findLockedById(user).orElseThrow(()->error(HttpStatus.NOT_FOUND,"No se encontró el usuario."));}
    private CategoriaCalendarioResponse response(CategoriaCalendario category) {return new CategoriaCalendarioResponse(category.getId(),category.getNombre(),category.getColor());}
    private ResponseStatusException error(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
}
