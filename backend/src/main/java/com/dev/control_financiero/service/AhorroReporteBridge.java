package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.PlanReporte;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Read-only compatibility for existing reports/PDF; no second editable savings balance. */
@Component @RequiredArgsConstructor
public class AhorroReporteBridge {
    private final JdbcTemplate db;
    public List<PlanReporte> planes(Long usuario){
        List<PlanReporte> result=new ArrayList<>();
        result.addAll(db.query("SELECT m.id,m.nombre,m.objetivo,m.fecha_objetivo,m.aporte_mensual,COALESCE(r.reservado,0) reservado FROM dbo.metas_ahorro m LEFT JOIN (SELECT meta_id,SUM(CASE tipo WHEN 'RESERVA' THEN monto ELSE -monto END) reservado FROM dbo.reservas_meta WHERE usuario_id=? GROUP BY meta_id) r ON r.meta_id=m.id WHERE m.usuario_id=? AND m.estado='ACTIVA'",(rs,i)->{var p=new PlanReporte();p.setId(-rs.getLong("id")*2);p.setUsuarioId(usuario);p.setTipo("META");p.setNombre(rs.getString("nombre"));p.setMonto(rs.getBigDecimal("objetivo"));p.setAcumulado(rs.getBigDecimal("reservado"));p.setAporteMensual(rs.getBigDecimal("aporte_mensual"));var d=rs.getDate("fecha_objetivo");p.setFechaObjetivo(d==null?null:d.toLocalDate());return p;},usuario,usuario));
        result.addAll(db.query("SELECT p.id,p.nombre,p.periodo,p.limite,c.nombre categoria FROM dbo.presupuestos_ahorro p JOIN dbo.categorias c ON c.id=p.categoria_id WHERE p.usuario_id=? AND p.activo=1 AND p.pendiente_categoria=0",(rs,i)->{var p=new PlanReporte();p.setId(-rs.getLong("id")*2-1);p.setUsuarioId(usuario);p.setTipo("PRESUPUESTO");p.setNombre(rs.getString("nombre"));p.setMonto(rs.getBigDecimal("limite"));p.setMes(rs.getDate("periodo").toLocalDate().toString().substring(0,7));p.setCategoria(rs.getString("categoria"));return p;},usuario));
        // Global budgets are also read-only; existing Reportes aggregation understands this sentinel.
        result.addAll(db.query("SELECT id,nombre,periodo,limite FROM dbo.presupuestos_ahorro WHERE usuario_id=? AND activo=1 AND pendiente_categoria=0 AND categoria_id IS NULL",(rs,i)->{var p=new PlanReporte();p.setId(-rs.getLong("id")*2-1);p.setUsuarioId(usuario);p.setTipo("PRESUPUESTO");p.setNombre(rs.getString("nombre"));p.setMonto(rs.getBigDecimal("limite"));p.setMes(rs.getDate("periodo").toLocalDate().toString().substring(0,7));p.setCategoria("Todas las categorías");p.setPresupuestoGlobal(true);return p;},usuario));return result;
    }
}
