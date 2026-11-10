package com.dev.control_financiero.service;

import com.dev.control_financiero.controller.AhorroLiquidezController;
import com.dev.control_financiero.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AhorroApiTest {
    final AhorroLiquidezService service=mock(AhorroLiquidezService.class);
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new AhorroLiquidezController(service,users)).build();
    @Test void requiereSesionSinConsultarDatos()throws Exception{mvc.perform(get("/api/ahorro-liquidez/resumen").param("mes","2026-11")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @Test void noAceptaUsuarioInactivo()throws Exception{var s=new MockHttpSession();s.setAttribute("userId",7L);mvc.perform(get("/api/ahorro-liquidez/resumen").param("mes","2026-11").session(s)).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @Test void usuarioProvieneDeSesionYDecimalesSonExactos()throws Exception{
        var s=new MockHttpSession();s.setAttribute("userId",7L);when(users.existsByIdAndActivoTrue(7L)).thenReturn(true);when(service.datos(7L,"2026-11")).thenReturn(Map.of("dineroLibre",new BigDecimal("0.30")));
        mvc.perform(get("/api/ahorro-liquidez/resumen").param("mes","2026-11").param("usuarioId","99").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.dineroLibre").value("0.30"));verify(service).datos(7L,"2026-11");
    }
}
