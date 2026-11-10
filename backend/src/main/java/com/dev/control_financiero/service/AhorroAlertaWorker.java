package com.dev.control_financiero.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class AhorroAlertaWorker {
    private final JdbcTemplate jdbc;
    private final AhorroLiquidezService service;
    @Scheduled(fixedDelayString="${app.ahorro.poll-ms:5000}",initialDelay=10000)
    public void procesar(){
        for(Long id:jdbc.queryForList("SELECT TOP (20) id FROM dbo.eventos_ahorro WHERE estado IN('PENDIENTE','ERROR') AND disponible_en<=SYSUTCDATETIME() AND intentos<10 ORDER BY id",Long.class)){
            try{service.procesarEvento(id);}
            catch(Exception ex){jdbc.update("UPDATE dbo.eventos_ahorro SET estado='ERROR',intentos=intentos+1,disponible_en=DATEADD(second,60,SYSUTCDATETIME()),error_codigo=? WHERE id=?",ex.getClass().getSimpleName(),id);log.warn("No se pudo recalcular ahorro para evento {}: {}",id,ex.getClass().getSimpleName());}
        }
    }
}
