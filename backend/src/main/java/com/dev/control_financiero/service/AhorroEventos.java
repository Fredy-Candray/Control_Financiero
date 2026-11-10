package com.dev.control_financiero.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Called from the original transaction: an event never survives a rolled-back expense. */
@Component @RequiredArgsConstructor
public class AhorroEventos {
    private final JdbcTemplate jdbc;
    public void registrar(Long usuario) {
        jdbc.update("INSERT dbo.eventos_ahorro(usuario_id,tipo) VALUES (?,'CAMBIO_FINANCIERO')",usuario);
    }
}
