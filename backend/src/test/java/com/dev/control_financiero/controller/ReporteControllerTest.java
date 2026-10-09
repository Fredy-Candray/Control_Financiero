package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.MenuResponse;
import com.dev.control_financiero.dto.UsuarioResponse;
import com.dev.control_financiero.repository.CuentaRepository;
import com.dev.control_financiero.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteControllerTest {
    final ReporteService reportes = mock(ReporteService.class);
    final MovimientoService movimientos = mock(MovimientoService.class);
    final CuentaRepository cuentas = mock(CuentaRepository.class);
    final UsuarioService usuarios = mock(UsuarioService.class);
    final MenuService menus = mock(MenuService.class);
    final ReporteController controller = new ReporteController(reportes, movimientos, cuentas, usuarios, menus);

    @Test void exigeSesion() throws Exception {
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/reportes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reportes, movimientos, cuentas);
    }

    @Test void respetaPermisosDelMenu() throws Exception {
        var usuario = mock(UsuarioResponse.class);
        when(usuario.activo()).thenReturn(true); when(usuario.rol()).thenReturn("Usuario");
        when(usuarios.obtenerUsuarioResponse(7L)).thenReturn(usuario);
        when(menus.obtenerMenu("Usuario")).thenReturn(new MenuResponse(List.of(), List.of("/dashboard")));
        var session = new MockHttpSession(); session.setAttribute("userId", 7L);
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/reportes").session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(reportes, movimientos, cuentas);
    }

    @Test void consultaSoloElUsuarioDeLaSesion() throws Exception {
        var usuario = mock(UsuarioResponse.class);
        when(usuario.activo()).thenReturn(true); when(usuario.rol()).thenReturn("Usuario");
        when(usuarios.obtenerUsuarioResponse(7L)).thenReturn(usuario);
        when(menus.obtenerMenu("Usuario")).thenReturn(new MenuResponse(List.of(), List.of("/reporte")));
        when(cuentas.findByUsuarioId(7L)).thenReturn(List.of());
        when(movimientos.listarPorUsuario(7L)).thenReturn(List.of());
        when(reportes.listar(7L)).thenReturn(List.of());
        var session = new MockHttpSession(); session.setAttribute("userId", 7L);
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/reportes").param("usuarioId", "99").session(session)).andExpect(status().isOk());
        verify(cuentas).findByUsuarioId(7L); verify(movimientos).listarPorUsuario(7L); verify(reportes).listar(7L);
    }
}
