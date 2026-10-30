package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.*;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.controller.CategoriaCalendarioController;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CategoriaCalendarioServiceTest {
    final CategoriaCalendarioRepository categories=mock(CategoriaCalendarioRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final EventoCalendarioRepository events=mock(EventoCalendarioRepository.class);
    final CategoriaCalendarioService service=new CategoriaCalendarioService(categories,users,events);
    final Map<CategoriaCalendarioId,CategoriaCalendario> rows=new HashMap<>();
    @BeforeEach void setup() {
        when(users.findLockedById(anyLong())).thenAnswer(a->Optional.of(Usuario.builder().id(a.getArgument(0)).build()));
        when(categories.findByUsuarioIdAndId(anyLong(),anyString())).thenAnswer(a->Optional.ofNullable(rows.get(new CategoriaCalendarioId(a.getArgument(0),a.getArgument(1)))));
        when(categories.findByUsuarioIdAndNombreClave(anyLong(),anyString())).thenAnswer(a->rows.values().stream().filter(c->c.getUsuarioId().equals(a.getArgument(0))&&c.getNombreClave().equals(a.getArgument(1))).findFirst());
        when(categories.findByUsuarioIdAndActivaTrueOrderByNombreAsc(anyLong())).thenAnswer(a->rows.values().stream().filter(c->c.getUsuarioId().equals(a.getArgument(0))&&c.isActiva()).toList());
        when(categories.existsByUsuarioId(anyLong())).thenAnswer(a->rows.values().stream().anyMatch(c->c.getUsuarioId().equals(a.getArgument(0))));
        when(categories.saveAndFlush(any())).thenAnswer(a->{CategoriaCalendario c=a.getArgument(0);rows.put(new CategoriaCalendarioId(c.getUsuarioId(),c.getId()),c);return c;});
    }
    @Test void keepsLegacyKeysAndSeparatesUsers() {
        service.importar(1L,List.of(new CategoriaCalendarioRequest("task","Recordatorio","#123ABC")));
        service.importar(2L,List.of(new CategoriaCalendarioRequest("task","Tarea privada","#654321")));
        assertEquals("Recordatorio",service.listar(1L).get(0).name());
        assertEquals("task",service.listar(1L).get(0).id());
        assertEquals("#123abc",service.listar(1L).get(0).color());
        assertEquals("Tarea privada",service.listar(2L).get(0).name());
    }
    @Test void migrationNeverOverwritesServerEdits() {
        var original=new CategoriaCalendarioRequest("custom","Personal","#123abc");
        service.importar(1L,List.of(original));
        service.guardar(1L,new CategoriaCalendarioRequest("custom","Trabajo","#abcdef"));
        service.importar(1L,List.of(original));
        assertEquals("Trabajo",service.listar(1L).get(0).name());
        assertEquals(1,rows.size());
    }
    @Test void deletionReassignsOnlyOwnersEventsAndCannotBeResurrected() {
        service.importar(1L,List.of(new CategoriaCalendarioRequest("one","Uno","#123abc"),new CategoriaCalendarioRequest("two","Dos","#abcdef")));
        service.eliminar(1L,"one","two");
        verify(events).reasignarCategoria(1L,"one","two");
        service.importar(1L,List.of(new CategoriaCalendarioRequest("one","Uno","#123abc")));
        assertEquals(List.of("two"),service.listar(1L).stream().map(CategoriaCalendarioResponse::id).toList());
        assertThrows(ResponseStatusException.class,()->service.eliminar(1L,"two","two"));
    }
    @Test void cannotUseAnotherUsersCategoryOrReplacement() {
        service.importar(1L,List.of(new CategoriaCalendarioRequest("one","Uno","#123abc")));
        service.importar(2L,List.of(new CategoriaCalendarioRequest("secret","Privada","#abcdef")));
        assertThrows(ResponseStatusException.class,()->service.validarParaEvento(1L,"secret","ACTIVIDAD"));
        assertThrows(ResponseStatusException.class,()->service.eliminar(1L,"one","secret"));
        verifyNoInteractions(events);
    }
    @Test void normalizesNamesAndRejectsDuplicates() {
        service.guardar(1L,new CategoriaCalendarioRequest("one","  Personal  ","#123abc"));
        assertThrows(ResponseStatusException.class,()->service.guardar(1L,new CategoriaCalendarioRequest("two","PERSONAL","#abcdef")));
    }
    @Test void restoresRecoveredMetadataWithoutChangingItsKey() {
        rows.put(new CategoriaCalendarioId(1L,"custom"),CategoriaCalendario.builder().usuarioId(1L).id("custom").nombre("Recuperada")
                .nombreClave("~recuperada:custom").color("#16a06a").activa(true).recuperada(true).build());
        service.importar(1L,List.of(new CategoriaCalendarioRequest("custom","Recordatorio","#987654")));
        assertEquals("Recordatorio",service.listar(1L).get(0).name());
        assertFalse(rows.get(new CategoriaCalendarioId(1L,"custom")).isRecuperada());
    }
    @Test void requiresSessionAndValidColor() {
        var controller=new CategoriaCalendarioController(mock(CategoriaCalendarioService.class));
        assertThrows(ResponseStatusException.class,()->controller.listar(new MockHttpServletRequest()));
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(new CategoriaCalendarioRequest("id","Nombre","url(x)")).isEmpty());
        }
    }
}
