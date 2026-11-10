package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.AhorroRequests.*;
import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** All fictitious users, accounts, reservations, events and movements roll back. */
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false"})
@ActiveProfiles("migration") @EnabledIfEnvironmentVariable(named="COFI_SQL_INTEGRATION",matches="true") @Transactional
class AhorroLiquidezSqlIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.dev.control_financiero.migration.CloudMigrationRunner migrationRunner;
    @Autowired EntityManager em;@Autowired JdbcTemplate db;@Autowired AhorroLiquidezService service;@Autowired MovimientoService movimientos;
    private static BigDecimal m(String n){return new BigDecimal(n);}private static String ref(){return UUID.randomUUID().toString();}
    private Usuario usuario(){var u=new Usuario();String id=ref();u.setNombre("Prueba ahorro reversible");u.setUsername("ahorro_it_"+id);u.setCorreo(id+"@example.test");u.setPassword("fixture-not-used-for-login");u.setActivo(true);u.setFechaCreacion(LocalDateTime.now());u.setRol(em.createQuery("select r from Rol r",Rol.class).setMaxResults(1).getSingleResult());em.persist(u);return u;}
    private Cuenta cuenta(Usuario u,TipoCuenta tipo,String saldo){var c=Cuenta.builder().usuario(u).nombre("Cuenta de prueba ahorro").tipo(tipo).saldoActual(m(saldo)).limiteCredito(tipo==TipoCuenta.CREDITO?m("1500.00"):null).moneda("USD").activa(true).fechaCreacion(LocalDateTime.now()).build();em.persist(c);return c;}
    private Long movimiento(Long u,Cuenta origen,Cuenta destino,TipoMovimiento tipo,String monto){var r=new CrearMovimientoRequest();r.setUsuarioId(u);r.setCuentaOrigenId(origen.getId());r.setCuentaDestinoId(destino==null?null:destino.getId());r.setTipo(tipo);r.setMonto(m(monto));r.setDescripcion("Fixture ahorro reversible");r.setReferenciaCliente(ref());r.setFechaMovimiento(LocalDateTime.now(ZoneId.of("America/El_Salvador")));Long id=movimientos.crearMovimiento(r).getId();em.flush();return id;}
    private void importe(String esperado,Object real){assertEquals(0,m(esperado).compareTo(new BigDecimal(real.toString())));}
    private long version(String tabla,Long registro){return db.queryForObject("SELECT version FROM dbo."+tabla+" WHERE id=?",Long.class,registro);}
    private long compromiso(Long fijo){return db.queryForObject("SELECT TOP(1) id FROM dbo.compromisos_ahorro WHERE gasto_fijo_id=? ORDER BY periodo",Long.class,fijo);}
    @Test void reservasPagosPresupuestosYPropiedadSinModificarDatosReales(){
        var u=usuario();var otro=usuario();var cash=cuenta(u,TipoCuenta.EFECTIVO,"1000.00");var tarjeta=cuenta(u,TipoCuenta.CREDITO,"1500.00");em.flush();Long user=u.getId();String mes=YearMonth.now(ZoneId.of("America/El_Salvador")).toString();
        Long fijo=service.fijo(user,null,new Fijo("Vivienda de prueba",m("100.00"),null,5,mes,null,ref()));Long comp=compromiso(fijo);
        Long meta=service.meta(user,null,new Meta("Meta de prueba",m("500.00"),null,null,null,ref()));String reservaRef=ref();
        var asignacion=new Reserva(cash.getId(),m("150.00"),0L,reservaRef);Long primera=service.asignar(user,meta,asignacion,false);assertEquals(primera,service.asignar(user,meta,asignacion,false));
        var datos=service.datos(user,mes);importe("1000.00",datos.get("dineroEnCuentas"));importe("900.00",datos.get("dineroLibre"));importe("750.00",datos.get("sinAsignar"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.movimientos WHERE usuario_id=?",Integer.class,user));
        assertThrows(ResponseStatusException.class,()->service.historial(otro.getId(),meta,0));
        assertThrows(IllegalArgumentException.class,()->service.asignar(user,meta,new Reserva(tarjeta.getId(),m("1.00"),1L,ref()),false));
        Long gasto=movimiento(user,cash,null,TipoMovimiento.GASTO,"100.00");
        String pagoRef=ref();Long vinculo=service.aplicarPago(user,comp,new Pago(gasto,m("100.00"),0L,pagoRef));assertEquals(vinculo,service.aplicarPago(user,comp,new Pago(gasto,m("100.00"),0L,pagoRef)));
        importe("900.00",service.datos(user,mes).get("dineroLibre"));importe("900.00",db.queryForObject("SELECT saldo_actual FROM dbo.cuentas WHERE id=?",BigDecimal.class,cash.getId()));
        Long fijo2=service.fijo(user,null,new Fijo("Servicio de prueba",m("100.00"),null,15,mes,null,ref()));Long comp2=compromiso(fijo2);importe("800.00",service.datos(user,mes).get("dineroLibre"));
        Long gastoCredito=movimiento(user,tarjeta,null,TipoMovimiento.GASTO,"100.00");service.aplicarPago(user,comp2,new Pago(gastoCredito,m("100.00"),0L,ref()));importe("800.00",service.datos(user,mes).get("dineroLibre"));
        movimiento(user,cash,tarjeta,TipoMovimiento.PAGO_TARJETA,"100.00");importe("800.00",service.datos(user,mes).get("dineroLibre"));
        Long presupuesto=service.presupuesto(user,null,new Presupuesto("Global de prueba",mes,null,m("200.00"),true,80,null,ref()));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.alertas_ahorro WHERE presupuesto_id=? AND umbral=80",Integer.class,presupuesto));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.alertas_ahorro WHERE presupuesto_id=? AND umbral=100",Integer.class,presupuesto));
        movimiento(user,tarjeta,null,TipoMovimiento.GASTO,"0.01");service.datos(user,mes);service.datos(user,mes);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.alertas_ahorro WHERE presupuesto_id=? AND umbral=100",Integer.class,presupuesto));
        Long alerta=db.queryForObject("SELECT id FROM dbo.alertas_ahorro WHERE presupuesto_id=? AND umbral=100",Long.class,presupuesto);service.leerAlerta(user,alerta);assertTrue(service.alertas(user,true).isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.asignar(user,meta,new Reserva(cash.getId(),m("800.00"),1L,ref()),false));
        service.asignar(user,meta,new Reserva(cash.getId(),m("150.00"),1L,ref()),true);service.archivarMeta(user,meta,2L);importe("0.00",service.datos(user,mes).get("reservadoMetas"));
        importe("800.00",db.queryForObject("SELECT saldo_actual FROM dbo.cuentas WHERE id=?",BigDecimal.class,cash.getId()));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM dbo.eventos_ahorro WHERE usuario_id=?",Integer.class,user)>0);
    }
}
