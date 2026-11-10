package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.AhorroRequests.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Virtual reservations only. Never updates real account balances. */
@Service @RequiredArgsConstructor
public class AhorroLiquidezService {
    private final JdbcTemplate db;
    private final AhorroEventos eventos;
    private static final ZoneId ZONA=ZoneId.of("America/El_Salvador");
    private static final BigDecimal CERO=new BigDecimal("0.00");
    private static void exigir(boolean ok,String texto){if(!ok)throw new IllegalArgumentException(texto);}
    private static ResponseStatusException noExiste(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro no encontrado.");}
    private static void conflicto(String texto){throw new ResponseStatusException(HttpStatus.CONFLICT,texto);}
    private static BigDecimal n(Object v){return v==null?CERO:new BigDecimal(v.toString());}
    private static long id(Object v){return ((Number)v).longValue();}
    private static boolean si(Object v){return Boolean.TRUE.equals(v)||(v instanceof Number&&((Number)v).intValue()!=0);}
    private static LocalDate fecha(Object v){return LocalDate.parse(v.toString().substring(0,10));}
    private static String referencia(String v){try{return UUID.fromString(v).toString();}catch(Exception e){throw new IllegalArgumentException("Referencia de operación no válida.");}}
    private static void nombre(String v){exigir(v!=null&&!v.isBlank()&&v.trim().length()<=100,"Indica un nombre de hasta 100 caracteres.");}
    private YearMonth mes(String valor){
        YearMonth p;try{p=YearMonth.parse(valor);}catch(Exception e){throw new IllegalArgumentException("Mes no válido.");}
        YearMonth hoy=YearMonth.now(ZONA);exigir(!p.isBefore(hoy.minusMonths(120))&&!p.isAfter(hoy.plusMonths(24)),"El período está fuera del rango permitido.");return p;
    }
    private void propietario(Long usuario){
        var rows=db.queryForList("SELECT id,activo FROM dbo.usuarios WITH(UPDLOCK,HOLDLOCK) WHERE id=?",usuario);
        if(rows.isEmpty()||!si(rows.get(0).get("activo")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicia sesión nuevamente.");
    }
    private Map<String,Object> fila(String tabla,Long usuario,Long registro){
        if(!Set.of("metas_ahorro","gastos_fijos_ahorro","compromisos_ahorro","presupuestos_ahorro","pagos_compromiso_ahorro").contains(tabla))throw new IllegalArgumentException();
        var rows=db.queryForList("SELECT * FROM dbo."+tabla+" WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND usuario_id=?",registro,usuario);
        if(rows.isEmpty())throw noExiste();return rows.get(0);
    }
    private Map<String,Object> anterior(String tabla,Long usuario,String ref){
        if(!Set.of("metas_ahorro","gastos_fijos_ahorro","presupuestos_ahorro","reservas_meta","pagos_compromiso_ahorro").contains(tabla))throw new IllegalArgumentException();
        var rows=db.queryForList("SELECT * FROM dbo."+tabla+" WHERE usuario_id=? AND referencia_cliente=?",usuario,ref);
        return rows.isEmpty()?null:rows.get(0);
    }
    private void version(Map<String,Object> row,Long version){if(version==null||id(row.get("version"))!=version)conflicto("El registro cambió. Actualiza y vuelve a intentar.");}
    private void categoria(Long categoria){if(categoria!=null&&db.queryForObject("SELECT COUNT(*) FROM dbo.categorias WHERE id=? AND activa=1",Integer.class,categoria)!=1)throw new IllegalArgumentException("Categoría no disponible.");}
    private long insertar(String sql,Object...params){return Objects.requireNonNull(db.queryForObject(sql,Long.class,params));}
    private BigDecimal saldoMeta(Long usuario,Long meta){return db.queryForObject("SELECT COALESCE(SUM(CASE tipo WHEN 'RESERVA' THEN monto ELSE -monto END),0) FROM dbo.reservas_meta WHERE usuario_id=? AND meta_id=?",BigDecimal.class,usuario,meta);}
    private List<Map<String,Object>> cuentas(Long usuario){
        return db.queryForList("SELECT c.id,c.nombre,c.tipo,c.saldo_actual saldoActual,c.limite_credito limiteCredito,c.activa,c.moneda,COALESCE(r.reservado,0) reservado FROM dbo.cuentas c WITH(UPDLOCK,HOLDLOCK) LEFT JOIN (SELECT cuenta_id,SUM(CASE tipo WHEN 'RESERVA' THEN monto ELSE -monto END) reservado FROM dbo.reservas_meta WHERE usuario_id=? GROUP BY cuenta_id) r ON r.cuenta_id=c.id WHERE c.usuario_id=? ORDER BY c.id",usuario,usuario);
    }
    private List<Map<String,Object>> compromisos(Long usuario,YearMonth periodo){
        return db.queryForList("SELECT c.id,c.gasto_fijo_id fijoId,f.nombre,c.periodo,c.vencimiento,c.monto_acordado monto,c.estado,c.version,COALESCE(p.pagado,0) pagado,c.monto_acordado-COALESCE(p.pagado,0) pendiente FROM dbo.compromisos_ahorro c JOIN dbo.gastos_fijos_ahorro f ON f.id=c.gasto_fijo_id AND f.usuario_id=c.usuario_id LEFT JOIN (SELECT compromiso_id,SUM(monto) pagado FROM dbo.pagos_compromiso_ahorro WHERE usuario_id=? AND anulada_en IS NULL GROUP BY compromiso_id) p ON p.compromiso_id=c.id WHERE c.usuario_id=? AND c.vencimiento<? ORDER BY c.vencimiento,c.id",usuario,usuario,periodo.plusMonths(1).atDay(1));
    }
    private void generar(Long usuario,YearMonth hasta){
        for(var fijo:db.queryForList("SELECT id,inicio,dia_pago,monto_acordado FROM dbo.gastos_fijos_ahorro WHERE usuario_id=? AND activa=1",usuario)){
            YearMonth inicio=YearMonth.from(fecha(fijo.get("inicio")));
            exigir(!inicio.isBefore(YearMonth.now(ZONA).minusMonths(120)),"Revisa la fecha inicial del gasto fijo.");
            for(YearMonth p=inicio;!p.isAfter(hasta);p=p.plusMonths(1)){
                int dia=Math.min(((Number)fijo.get("dia_pago")).intValue(),p.lengthOfMonth());
                db.update("INSERT dbo.compromisos_ahorro(usuario_id,gasto_fijo_id,periodo,vencimiento,monto_acordado) SELECT ?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.compromisos_ahorro WHERE usuario_id=? AND gasto_fijo_id=? AND periodo=?)",usuario,fijo.get("id"),p.atDay(1),p.atDay(dia),fijo.get("monto_acordado"),usuario,fijo.get("id"),p.atDay(1));
            }
        }
    }
    private AhorroCalculos.Resumen calcular(Long usuario,List<Map<String,Object>> cuentas,List<Map<String,Object>> compromisos,YearMonth periodo){
        List<AhorroCalculos.Cuenta> calculo=new ArrayList<>();List<AhorroCalculos.Reserva> reservas=new ArrayList<>();
        for(var c:cuentas){
            exigir(c.get("moneda")==null||"USD".equals(c.get("moneda")),"Hay una cuenta en otra moneda; no se mezclan saldos sin conversión.");
            var tipo=AhorroCalculos.TipoCuenta.valueOf(c.get("tipo").toString());BigDecimal legado=null;
            if(tipo==AhorroCalculos.TipoCuenta.CREDITO&&c.get("limiteCredito")==null){
                BigDecimal cargos=db.queryForObject("SELECT COALESCE(SUM(monto),0) FROM dbo.movimientos WITH(HOLDLOCK) WHERE usuario_id=? AND cuenta_origen_id=? AND tipo='GASTO'",BigDecimal.class,usuario,c.get("id"));
                BigDecimal pagos=db.queryForObject("SELECT COALESCE(SUM(monto),0) FROM dbo.movimientos WITH(HOLDLOCK) WHERE usuario_id=? AND cuenta_destino_id=? AND tipo IN('PAGO_TARJETA','TRANSFERENCIA')",BigDecimal.class,usuario,c.get("id"));legado=cargos.subtract(pagos).max(CERO);
            }
            calculo.add(new AhorroCalculos.Cuenta(id(c.get("id")),tipo,n(c.get("saldoActual")),c.get("limiteCredito")==null?null:n(c.get("limiteCredito")),legado));
            if(n(c.get("reservado")).signum()>0)reservas.add(new AhorroCalculos.Reserva(id(c.get("id")),n(c.get("reservado"))));
        }
        var fijos=compromisos.stream().map(c->new AhorroCalculos.Compromiso(n(c.get("monto")),n(c.get("pagado")),fecha(c.get("vencimiento")),"CANCELADO".equals(c.get("estado")))).toList();
        return AhorroCalculos.resumir(calculo,fijos,reservas,periodo.atEndOfMonth());
    }
    @Transactional public Map<String,Object> datos(Long usuario,String valor){
        propietario(usuario);YearMonth periodo=mes(valor);generar(usuario,periodo);
        var cuentas=cuentas(usuario);var compromisos=compromisos(usuario,periodo);var resumen=calcular(usuario,cuentas,compromisos,periodo);evaluar(usuario);
        Map<String,Object> r=new LinkedHashMap<>();r.put("mes",periodo.toString());r.put("moneda","USD");r.put("calculadoEn",OffsetDateTime.now(ZONA).toString());
        r.put("dineroEnCuentas",resumen.dineroEnCuentas());r.put("deudaTarjetas",resumen.deudaTarjetas());r.put("gastosFijosPendientes",resumen.fijosPendientes());r.put("dineroLibre",resumen.dineroLibre());r.put("reservadoMetas",resumen.reservado());r.put("sinAsignar",resumen.sinAsignar());
        r.put("reservasSinCoberturaPorCuenta",resumen.reservasSinCoberturaPorCuenta());r.put("reservasSinCoberturaGlobal",resumen.reservasSinCoberturaGlobal());r.put("cuentas",cuentas);
        r.put("metas",db.queryForList("SELECT m.id,m.nombre,m.objetivo,m.fecha_objetivo fechaObjetivo,m.aporte_mensual aporteMensual,m.acumulado_legado_informado acumuladoLegado,m.version,COALESCE(r.reservado,0) reservado FROM dbo.metas_ahorro m LEFT JOIN (SELECT meta_id,SUM(CASE tipo WHEN 'RESERVA' THEN monto ELSE -monto END) reservado FROM dbo.reservas_meta WHERE usuario_id=? GROUP BY meta_id) r ON r.meta_id=m.id WHERE m.usuario_id=? AND m.estado='ACTIVA' ORDER BY m.id",usuario,usuario));
        r.put("fijos",db.queryForList("SELECT id,nombre,monto_acordado monto,categoria_id categoriaId,dia_pago diaPago,inicio,activa,version FROM dbo.gastos_fijos_ahorro WHERE usuario_id=? AND activa=1 ORDER BY id",usuario));
        r.put("compromisos",compromisos);r.put("presupuestos",presupuestos(usuario,periodo));r.put("categorias",db.queryForList("SELECT id,nombre FROM dbo.categorias WHERE activa=1 ORDER BY nombre"));r.put("alertas",alertas(usuario,false));return r;
    }
    @Transactional public Long meta(Long usuario,Long registro,Meta req){
        propietario(usuario);nombre(req.nombre());String ref=referencia(req.referenciaCliente());
        if(registro==null){var previo=anterior("metas_ahorro",usuario,ref);if(previo!=null){exigir(previo.get("nombre").equals(req.nombre().trim())&&n(previo.get("objetivo")).compareTo(req.objetivo())==0,"La referencia pertenece a otra meta.");return id(previo.get("id"));}
            registro=insertar("INSERT dbo.metas_ahorro(usuario_id,nombre,objetivo,fecha_objetivo,aporte_mensual,referencia_cliente) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?)",usuario,req.nombre().trim(),req.objetivo(),req.fechaObjetivo(),req.aporteMensual(),ref);
        }else{var row=fila("metas_ahorro",usuario,registro);version(row,req.version());exigir("ACTIVA".equals(row.get("estado")),"La meta está archivada.");if(saldoMeta(usuario,registro).compareTo(req.objetivo())>0)conflicto("Libera fondos antes de reducir el objetivo.");db.update("UPDATE dbo.metas_ahorro SET nombre=?,objetivo=?,fecha_objetivo=?,aporte_mensual=?,version=version+1,actualizada_en=SYSUTCDATETIME() WHERE id=? AND usuario_id=?",req.nombre().trim(),req.objetivo(),req.fechaObjetivo(),req.aporteMensual(),registro,usuario);}
        eventos.registrar(usuario);return registro;
    }
    @Transactional public void archivarMeta(Long usuario,Long registro,Long v){
        propietario(usuario);var row=fila("metas_ahorro",usuario,registro);if("ARCHIVADA".equals(row.get("estado")))return;version(row,v);
        if(saldoMeta(usuario,registro).signum()!=0)conflicto("Libera las reservas antes de archivar la meta.");
        db.update("UPDATE dbo.metas_ahorro SET estado='ARCHIVADA',version=version+1,actualizada_en=SYSUTCDATETIME() WHERE id=? AND usuario_id=?",registro,usuario);eventos.registrar(usuario);
    }
    @Transactional public Long asignar(Long usuario,Long meta,Reserva req,boolean liberar){
        propietario(usuario);String ref=referencia(req.referenciaCliente()),tipo=liberar?"LIBERACION":"RESERVA";
        var previo=anterior("reservas_meta",usuario,ref);if(previo!=null){exigir(id(previo.get("meta_id"))==meta&&id(previo.get("cuenta_id"))==req.cuentaId()&&tipo.equals(previo.get("tipo"))&&n(previo.get("monto")).compareTo(req.monto())==0,"La referencia pertenece a otra asignación.");return id(previo.get("id"));}
        var objetivo=fila("metas_ahorro",usuario,meta);version(objetivo,req.version());exigir("ACTIVA".equals(objetivo.get("estado")),"La meta está archivada.");
        YearMonth actual=YearMonth.now(ZONA);generar(usuario,actual);var cuentas=cuentas(usuario);var cuenta=cuentas.stream().filter(c->id(c.get("id"))==req.cuentaId()).findFirst().orElseThrow(AhorroLiquidezService::noExiste);
        BigDecimal actualEnCuenta=db.queryForObject("SELECT COALESCE(SUM(CASE tipo WHEN 'RESERVA' THEN monto ELSE -monto END),0) FROM dbo.reservas_meta WHERE usuario_id=? AND meta_id=? AND cuenta_id=?",BigDecimal.class,usuario,meta,req.cuentaId());
        if(liberar)exigir(req.monto().compareTo(actualEnCuenta)<=0,"No puedes liberar más de lo reservado en esa cuenta.");
        else{exigir(si(cuenta.get("activa"))&&!"CREDITO".equals(cuenta.get("tipo")),"Selecciona una cuenta activa de efectivo o débito.");var resumen=calcular(usuario,cuentas,compromisos(usuario,actual),actual);
            AhorroCalculos.validarReserva(resumen,new AhorroCalculos.Cuenta(req.cuentaId(),AhorroCalculos.TipoCuenta.valueOf(cuenta.get("tipo").toString()),n(cuenta.get("saldoActual")),null,null),n(cuenta.get("reservado")),saldoMeta(usuario,meta),n(objetivo.get("objetivo")),req.monto());}
        Long resultado=insertar("INSERT dbo.reservas_meta(usuario_id,meta_id,cuenta_id,tipo,monto,referencia_cliente) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?)",usuario,meta,req.cuentaId(),tipo,req.monto(),ref);
        db.update("UPDATE dbo.metas_ahorro SET version=version+1,actualizada_en=SYSUTCDATETIME() WHERE id=? AND usuario_id=?",meta,usuario);eventos.registrar(usuario);return resultado;
    }
    @Transactional public Map<String,Object> historial(Long usuario,Long meta,int pagina){
        propietario(usuario);fila("metas_ahorro",usuario,meta);exigir(pagina>=0&&pagina<=100000,"Página no válida.");
        return Map.of("total",db.queryForObject("SELECT COUNT(*) FROM dbo.reservas_meta WHERE usuario_id=? AND meta_id=?",Long.class,usuario,meta),"filas",db.queryForList("SELECT r.id,r.tipo,r.monto,CONVERT(varchar(33),r.creada_en,126)+'Z' fecha,c.nombre cuenta FROM dbo.reservas_meta r JOIN dbo.cuentas c ON c.id=r.cuenta_id AND c.usuario_id=r.usuario_id WHERE r.usuario_id=? AND r.meta_id=? ORDER BY r.id DESC OFFSET ? ROWS FETCH NEXT 6 ROWS ONLY",usuario,meta,pagina*6));
    }
    @Transactional public Long fijo(Long usuario,Long registro,Fijo req){
        propietario(usuario);nombre(req.nombre());categoria(req.categoriaId());YearMonth inicio=mes(req.inicio());String ref=referencia(req.referenciaCliente());
        if(registro==null){var previo=anterior("gastos_fijos_ahorro",usuario,ref);if(previo!=null){exigir(previo.get("nombre").equals(req.nombre().trim())&&n(previo.get("monto_acordado")).compareTo(req.monto())==0,"La referencia pertenece a otro gasto fijo.");return id(previo.get("id"));}
            registro=insertar("INSERT dbo.gastos_fijos_ahorro(usuario_id,nombre,monto_acordado,categoria_id,dia_pago,inicio,referencia_cliente) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?)",usuario,req.nombre().trim(),req.monto(),req.categoriaId(),req.diaPago(),inicio.atDay(1),ref);
        }else{var row=fila("gastos_fijos_ahorro",usuario,registro);version(row,req.version());exigir(si(row.get("activa")),"La plantilla está archivada.");db.update("UPDATE dbo.gastos_fijos_ahorro SET nombre=?,monto_acordado=?,categoria_id=?,dia_pago=?,version=version+1 WHERE id=? AND usuario_id=?",req.nombre().trim(),req.monto(),req.categoriaId(),req.diaPago(),registro,usuario);}
        generar(usuario,YearMonth.now(ZONA));eventos.registrar(usuario);return registro;
    }
    @Transactional public void archivarFijo(Long usuario,Long registro,Long v){propietario(usuario);var row=fila("gastos_fijos_ahorro",usuario,registro);if(!si(row.get("activa")))return;version(row,v);db.update("UPDATE dbo.gastos_fijos_ahorro SET activa=0,version=version+1 WHERE id=? AND usuario_id=?",registro,usuario);eventos.registrar(usuario);}
    @Transactional public void cancelarCompromiso(Long usuario,Long registro,Revision req){propietario(usuario);var row=fila("compromisos_ahorro",usuario,registro);if("CANCELADO".equals(row.get("estado")))return;version(row,req.version());db.update("UPDATE dbo.compromisos_ahorro SET estado='CANCELADO',nota=?,version=version+1 WHERE id=? AND usuario_id=?",req.nota().trim(),registro,usuario);eventos.registrar(usuario);}
    @Transactional public void ajustarCompromiso(Long usuario,Long registro,CambioCompromiso req){
        propietario(usuario);var row=fila("compromisos_ahorro",usuario,registro);version(row,req.version());exigir("VIGENTE".equals(row.get("estado")),"El compromiso está cancelado.");
        BigDecimal pagado=db.queryForObject("SELECT COALESCE(SUM(monto),0) FROM dbo.pagos_compromiso_ahorro WHERE usuario_id=? AND compromiso_id=? AND anulada_en IS NULL",BigDecimal.class,usuario,registro);
        exigir(req.monto().compareTo(pagado)>=0,"El importe no puede ser menor al ya vinculado.");db.update("UPDATE dbo.compromisos_ahorro SET monto_acordado=?,nota=?,version=version+1 WHERE id=? AND usuario_id=?",req.monto(),req.nota().trim(),registro,usuario);eventos.registrar(usuario);
    }
    @Transactional public Long aplicarPago(Long usuario,Long compromiso,Pago req){
        propietario(usuario);String ref=referencia(req.referenciaCliente());var previo=anterior("pagos_compromiso_ahorro",usuario,ref);
        if(previo!=null){exigir(id(previo.get("compromiso_id"))==compromiso&&id(previo.get("movimiento_id"))==req.movimientoId()&&n(previo.get("monto")).compareTo(req.monto())==0,"La referencia pertenece a otro pago vinculado.");return id(previo.get("id"));}
        var row=fila("compromisos_ahorro",usuario,compromiso);version(row,req.version());exigir("VIGENTE".equals(row.get("estado")),"El compromiso está cancelado.");
        var movimientos=db.queryForList("SELECT monto,tipo FROM dbo.movimientos WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND usuario_id=?",req.movimientoId(),usuario);if(movimientos.isEmpty())throw noExiste();
        exigir("GASTO".equals(movimientos.get(0).get("tipo")),"Selecciona un gasto ya registrado; no un pago de tarjeta ni una transferencia propia.");
        BigDecimal aplicado=db.queryForObject("SELECT COALESCE(SUM(monto),0) FROM dbo.pagos_compromiso_ahorro WHERE usuario_id=? AND movimiento_id=? AND anulada_en IS NULL",BigDecimal.class,usuario,req.movimientoId());
        BigDecimal pagado=db.queryForObject("SELECT COALESCE(SUM(monto),0) FROM dbo.pagos_compromiso_ahorro WHERE usuario_id=? AND compromiso_id=? AND anulada_en IS NULL",BigDecimal.class,usuario,compromiso);
        exigir(aplicado.add(req.monto()).compareTo(n(movimientos.get(0).get("monto")))<=0,"El gasto no tiene ese importe sin vincular.");exigir(pagado.add(req.monto()).compareTo(n(row.get("monto_acordado")))<=0,"El importe supera lo pendiente del compromiso.");
        Long resultado=insertar("INSERT dbo.pagos_compromiso_ahorro(usuario_id,compromiso_id,movimiento_id,monto,referencia_cliente) OUTPUT INSERTED.id VALUES (?,?,?,?,?)",usuario,compromiso,req.movimientoId(),req.monto(),ref);
        db.update("UPDATE dbo.compromisos_ahorro SET version=version+1 WHERE id=? AND usuario_id=?",compromiso,usuario);eventos.registrar(usuario);return resultado;
    }
    @Transactional public List<Map<String,Object>> pagos(Long usuario,Long compromiso){propietario(usuario);fila("compromisos_ahorro",usuario,compromiso);return db.queryForList("SELECT p.id,p.monto,CONVERT(varchar(33),p.creada_en,126)+'Z' fecha,CASE WHEN p.anulada_en IS NULL THEN NULL ELSE CONVERT(varchar(33),p.anulada_en,126)+'Z' END anuladaEn,m.descripcion,p.movimiento_id movimientoId FROM dbo.pagos_compromiso_ahorro p JOIN dbo.movimientos m ON m.id=p.movimiento_id AND m.usuario_id=p.usuario_id WHERE p.usuario_id=? AND p.compromiso_id=? ORDER BY p.id DESC",usuario,compromiso);}
    @Transactional public void anularPago(Long usuario,Long compromiso,Long pago,Revision req){propietario(usuario);var c=fila("compromisos_ahorro",usuario,compromiso);var p=fila("pagos_compromiso_ahorro",usuario,pago);if(id(p.get("compromiso_id"))!=compromiso)throw noExiste();if(p.get("anulada_en")!=null)return;version(c,req.version());db.update("UPDATE dbo.pagos_compromiso_ahorro SET anulada_en=SYSUTCDATETIME(),motivo_anulacion=? WHERE id=? AND usuario_id=?",req.nota().trim(),pago,usuario);db.update("UPDATE dbo.compromisos_ahorro SET version=version+1 WHERE id=? AND usuario_id=?",compromiso,usuario);eventos.registrar(usuario);}
    @Transactional(readOnly=true) public List<Map<String,Object>> movimientosParaFijos(Long usuario){
        return db.queryForList("SELECT TOP (300) m.id,m.descripcion,m.monto,CONVERT(varchar(33),m.fecha_movimiento,126)+'-06:00' fecha,c.nombre cuenta,m.monto-COALESCE(p.aplicado,0) disponible FROM dbo.movimientos m JOIN dbo.cuentas c ON c.id=m.cuenta_origen_id AND c.usuario_id=m.usuario_id LEFT JOIN (SELECT movimiento_id,SUM(monto) aplicado FROM dbo.pagos_compromiso_ahorro WHERE usuario_id=? AND anulada_en IS NULL GROUP BY movimiento_id) p ON p.movimiento_id=m.id WHERE m.usuario_id=? AND m.tipo='GASTO' AND m.monto>COALESCE(p.aplicado,0) ORDER BY m.fecha_movimiento DESC,m.id DESC",usuario,usuario);
    }
    @Transactional public Long presupuesto(Long usuario,Long registro,Presupuesto req){
        propietario(usuario);nombre(req.nombre());categoria(req.categoriaId());YearMonth p=mes(req.mes());String ref=referencia(req.referenciaCliente());
        if(registro==null){var previo=anterior("presupuestos_ahorro",usuario,ref);if(previo!=null){exigir(previo.get("nombre").equals(req.nombre().trim())&&n(previo.get("limite")).compareTo(req.limite())==0,"La referencia pertenece a otro presupuesto.");return id(previo.get("id"));}}
        if(registro==null)registro=insertar("INSERT dbo.presupuestos_ahorro(usuario_id,nombre,periodo,categoria_id,limite,alerta_anticipada,umbral_anticipado,referencia_cliente) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?)",usuario,req.nombre().trim(),p.atDay(1),req.categoriaId(),req.limite(),req.alertaAnticipada(),req.umbral(),ref);
        else{var row=fila("presupuestos_ahorro",usuario,registro);version(row,req.version());exigir(si(row.get("activo")),"El presupuesto está archivado.");db.update("UPDATE dbo.presupuestos_ahorro SET nombre=?,periodo=?,categoria_id=?,limite=?,alerta_anticipada=?,umbral_anticipado=?,pendiente_categoria=0,version=version+1 WHERE id=? AND usuario_id=?",req.nombre().trim(),p.atDay(1),req.categoriaId(),req.limite(),req.alertaAnticipada(),req.umbral(),registro,usuario);}
        eventos.registrar(usuario);evaluar(usuario);return registro;
    }
    @Transactional public void archivarPresupuesto(Long usuario,Long registro,Long v){propietario(usuario);var row=fila("presupuestos_ahorro",usuario,registro);if(!si(row.get("activo")))return;version(row,v);db.update("UPDATE dbo.presupuestos_ahorro SET activo=0,version=version+1 WHERE id=? AND usuario_id=?",registro,usuario);eventos.registrar(usuario);evaluar(usuario);}
    private Map<Long,BigDecimal> consumo(Long usuario,YearMonth p){
        Map<Long,BigDecimal> result=new HashMap<>();
        for(var r:db.queryForList("SELECT categoria_id categoriaId,SUM(monto) total FROM dbo.movimientos WITH(HOLDLOCK) WHERE usuario_id=? AND tipo='GASTO' AND fecha_movimiento>=? AND fecha_movimiento<? GROUP BY categoria_id",usuario,p.atDay(1).atStartOfDay(),p.plusMonths(1).atDay(1).atStartOfDay()))result.put(r.get("categoriaId")==null?0L:id(r.get("categoriaId")),n(r.get("total")));
        return result;
    }
    private BigDecimal gastado(Map<String,Object> p,Map<Long,BigDecimal> consumo){return p.get("categoriaId")==null?consumo.values().stream().reduce(CERO,BigDecimal::add):consumo.getOrDefault(id(p.get("categoriaId")),CERO);}
    private List<Map<String,Object>> presupuestos(Long usuario,YearMonth periodo){
        var consumo=consumo(usuario,periodo);
        var rows=db.queryForList("SELECT p.id,p.nombre,p.periodo,p.categoria_id categoriaId,c.nombre categoria,p.categoria_legada categoriaLegada,p.pendiente_categoria pendienteCategoria,p.limite,p.alerta_anticipada alertaAnticipada,p.umbral_anticipado umbral,p.version FROM dbo.presupuestos_ahorro p LEFT JOIN dbo.categorias c ON c.id=p.categoria_id WHERE p.usuario_id=? AND p.periodo=? AND p.activo=1 ORDER BY p.id",usuario,periodo.atDay(1));
        for(var p:rows){BigDecimal g=si(p.get("pendienteCategoria"))?CERO:gastado(p,consumo);p.put("gastado",g);p.put("restante",n(p.get("limite")).subtract(g));p.put("estado",si(p.get("pendienteCategoria"))?"POR_CONFIRMAR":AhorroCalculos.estadoPresupuesto(g,n(p.get("limite")),si(p.get("alertaAnticipada"))?((Number)p.get("umbral")).intValue():null).name());}
        return rows;
    }
    private void evaluar(Long usuario){
        var planes=db.queryForList("SELECT id,periodo,categoria_id categoriaId,limite,alerta_anticipada alertaAnticipada,umbral_anticipado umbral,activo,pendiente_categoria pendienteCategoria FROM dbo.presupuestos_ahorro WHERE usuario_id=?",usuario);
        Map<YearMonth,Map<Long,BigDecimal>> cache=new HashMap<>();
        for(var p:planes){
            Long presupuesto=id(p.get("id"));YearMonth periodo=YearMonth.from(fecha(p.get("periodo")));
            if(!si(p.get("activo"))||si(p.get("pendienteCategoria"))){db.update("UPDATE dbo.alertas_ahorro SET estado='RESUELTA',resuelta_en=SYSUTCDATETIME() WHERE usuario_id=? AND presupuesto_id=? AND estado<>'RESUELTA'",usuario,presupuesto);continue;}
            BigDecimal gasto=gastado(p,cache.computeIfAbsent(periodo,key->consumo(usuario,key))),limite=n(p.get("limite"));
            int umbral=((Number)p.get("umbral")).intValue();var estado=AhorroCalculos.estadoPresupuesto(gasto,limite,si(p.get("alertaAnticipada"))?umbral:null);
            Integer objetivo=estado==AhorroCalculos.EstadoPresupuesto.EXCEDIDO?100:(estado==AhorroCalculos.EstadoPresupuesto.AVISO||estado==AhorroCalculos.EstadoPresupuesto.AL_LIMITE)&&si(p.get("alertaAnticipada"))?umbral:null;
            if(objetivo==null)db.update("UPDATE dbo.alertas_ahorro SET estado='RESUELTA',resuelta_en=SYSUTCDATETIME() WHERE usuario_id=? AND presupuesto_id=? AND estado<>'RESUELTA'",usuario,presupuesto);
            else{
                db.update("UPDATE dbo.alertas_ahorro SET estado='RESUELTA',resuelta_en=SYSUTCDATETIME() WHERE usuario_id=? AND presupuesto_id=? AND umbral<>? AND estado<>'RESUELTA'",usuario,presupuesto,objetivo);
                db.update("INSERT dbo.alertas_ahorro(usuario_id,presupuesto_id,periodo,umbral,gastado_detectado,limite_detectado) SELECT ?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.alertas_ahorro WHERE usuario_id=? AND presupuesto_id=? AND periodo=? AND umbral=?)",usuario,presupuesto,periodo.atDay(1),objetivo,gasto,limite,usuario,presupuesto,periodo.atDay(1),objetivo);
            }
        }
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> alertas(Long usuario,boolean nuevas){
        return db.queryForList("SELECT TOP (100) a.id,p.nombre,a.umbral,a.gastado_detectado gastado,a.limite_detectado limite,a.estado,a.periodo,CONVERT(varchar(33),a.creada_en,126)+'Z' fecha FROM dbo.alertas_ahorro a JOIN dbo.presupuestos_ahorro p ON p.id=a.presupuesto_id AND p.usuario_id=a.usuario_id WHERE a.usuario_id=?"+(nuevas?" AND a.estado='NUEVA'":"")+" ORDER BY a.id DESC",usuario);
    }
    @Transactional public void leerAlerta(Long usuario,Long alerta){propietario(usuario);int count=db.update("UPDATE dbo.alertas_ahorro SET leida_en=COALESCE(leida_en,SYSUTCDATETIME()),estado=CASE WHEN estado='NUEVA' THEN 'LEIDA' ELSE estado END WHERE id=? AND usuario_id=?",alerta,usuario);if(count==0)throw noExiste();}
    @Transactional public void procesarEvento(Long evento){
        var rows=db.queryForList("SELECT id,usuario_id FROM dbo.eventos_ahorro WITH(UPDLOCK,READPAST,ROWLOCK) WHERE id=? AND estado IN('PENDIENTE','ERROR') AND disponible_en<=SYSUTCDATETIME()",evento);
        if(rows.isEmpty())return;Long usuario=id(rows.get(0).get("usuario_id"));propietario(usuario);evaluar(usuario);db.update("UPDATE dbo.eventos_ahorro SET estado='PROCESADO',procesado_en=SYSUTCDATETIME(),error_codigo=NULL WHERE id=?",evento);
    }
}
