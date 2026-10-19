package com.dev.control_financiero.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RailwayWebControllerTest {
    @Test void servesAngularRoutesWithoutInterceptingApiOrAssets() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new RailwayWebController()).build();
        for(String path:new String[]{"/", "/login", "/dashboard", "/cuentas", "/movimientos", "/perfil", "/reportes"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/assets/missing.png")).andExpect(status().isNotFound());
    }
}
