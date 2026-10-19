package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.dto.EventoCalendarioRequest;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventoCalendarioOwnershipTest {
    final EventoCalendarioRepository events=mock(EventoCalendarioRepository.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final CuentaRepository accounts=mock(CuentaRepository.class);
    final EventoCalendarioService service=new EventoCalendarioService(events,users,accounts);
    EventoCalendarioRequest request(Long account) {
        return new EventoCalendarioRequest("event-id","Actividad","","ACTIVIDAD",null,LocalDate.now(),LocalTime.NOON,
                account,null,"NONE",0,false,false,true,false,null);
    }
    @Test void cannotOverwriteAnotherUsersActivity() {
        when(users.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).build()));
        when(events.findById("event-id")).thenReturn(Optional.of(EventoCalendario.builder().id("event-id")
                .usuario(Usuario.builder().id(2L).build()).build()));
        assertThrows(ResponseStatusException.class,()->service.guardar(1L,request(null)));
        verify(events,never()).save(any()); verifyNoInteractions(accounts);
    }
    @Test void cannotReferenceAnotherUsersAccount() {
        when(users.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).build()));
        when(events.findById("event-id")).thenReturn(Optional.empty());
        when(accounts.findById(9L)).thenReturn(Optional.of(Cuenta.builder().id(9L).usuario(Usuario.builder().id(2L).build()).build()));
        assertThrows(ResponseStatusException.class,()->service.guardar(1L,request(9L)));
        verify(events,never()).save(any());
    }
}
