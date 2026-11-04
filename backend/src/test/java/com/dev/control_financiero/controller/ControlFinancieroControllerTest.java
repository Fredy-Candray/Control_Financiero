package com.dev.control_financiero.controller;
import com.dev.control_financiero.service.ControlFinancieroService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class ControlFinancieroControllerTest {
    final ControlFinancieroService service=mock(ControlFinancieroService.class);
    final ControlFinancieroController controller=new ControlFinancieroController(service);
    @Test void noAnonymousAccess() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        for(String path:new String[]{"pendientes","conciliaciones","prestamos"})mvc.perform(get("/api/control-financiero/"+path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void ownerAlwaysComesFromSession() throws Exception {
        var session=new MockHttpSession();session.setAttribute("userId",1L);
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/control-financiero/prestamos?usuarioId=999").session(session)).andExpect(status().isOk());
        verify(service).prestamos(1L);verify(service,never()).prestamos(999L);
    }
    @Test void rejectsInvalidMoneyAndMissingReviewReason() throws Exception {
        var session=new MockHttpSession();session.setAttribute("userId",1L);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/control-financiero/prestamos").session(session).contentType("application/json")
            .content("{\"persona\":\"Ana\",\"monto\":-1,\"cuentaId\":2,\"referenciaCliente\":\"test\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/control-financiero/pendientes/5/resolver").session(session).contentType("application/json")
            .content("{\"accion\":\"IGNORAR\",\"nota\":\"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void businessValidationReturnsUsefulMessage() throws Exception {
        var session=new MockHttpSession();session.setAttribute("userId",1L);
        when(service.prestar(eq(1L),anyString(),any(),any(),any(),any(),any(),any())).thenThrow(new IllegalArgumentException("Saldo insuficiente."));
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/api/control-financiero/prestamos").session(session).contentType("application/json")
            .content("{\"persona\":\"Ana\",\"monto\":700,\"cuentaId\":2,\"referenciaCliente\":\"test\"}"))
            .andExpect(status().isBadRequest()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Saldo insuficiente."));
    }
}
