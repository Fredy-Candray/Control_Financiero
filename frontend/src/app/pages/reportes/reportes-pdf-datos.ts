import { agruparCategorias, centavos, MovimientoReporte, sumar } from './reportes-calculos';
import { Cuenta } from '../../models/cuenta';

export interface MesInforme {
  mes: string; ingresos: number; gastos: number; balance: number; flujo: number; patrimonio: number; deuda: number;
}
export interface ContextoInforme {
  vista: string; desde: string; hasta: string; generado: Date;
  ingresos: number; gastos: number; balance: number; patrimonio: number; deuda: number; dineroDisponible: number;
  flujo: { entradas: number; salidas: number; neto: number };
  movimientos: MovimientoReporte[]; meses: MesInforme[];
  cuentas: Pick<Cuenta, 'id' | 'tipo'>[];
  anterior?: { ingresos: number; gastos: number; desde: string; hasta: string };
}
export const dineroPdf = (valor: number): string => new Intl.NumberFormat('es-SV', { style: 'currency', currency: 'USD' }).format(valor);
export function fechaPdf(fecha: string): string {
  const partes = fecha.slice(0, 10).split('-');
  return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : fecha;
}

// Presentation-only statistics: core balances and cash flow are supplied by the existing report.
export function prepararAnalisisPdf(datos: ContextoInforme) {
  const gastos = datos.movimientos.filter(m => m.tipo === 'GASTO');
  const efectivo = new Set((datos.cuentas ?? []).filter(c => c.tipo === 'EFECTIVO').map(c => c.id));
  const gastoEfectivo = sumar(gastos.filter(m => m.cuentaOrigenId != null && efectivo.has(m.cuentaOrigenId)), 'GASTO');
  const categorias = agruparCategorias(datos.movimientos, 'GASTO');
  const cuentas = new Map<string, number>();
  for (const m of gastos) {
    const cuenta = m.cuentaOrigen || 'Cuenta sin identificar';
    cuentas.set(cuenta, (cuentas.get(cuenta) ?? 0) + centavos(m.monto));
  }
  const medios = [...cuentas].map(([nombre, monto]) => ({ nombre, monto: monto / 100 })).sort((a, b) => b.monto - a.monto);
  const sinCategoria = categorias.find(c => c.nombre === 'Sin categoría');
  const observaciones: string[] = [];
  if (!datos.movimientos.length) observaciones.push('No hay movimientos para los filtros seleccionados. Los saldos actuales no representan actividad de este período.');
  else if (datos.balance < 0) observaciones.push(`Los gastos superaron a los ingresos registrados en ${dineroPdf(-datos.balance)}. El balance del período es negativo.`);
  else if (datos.balance > 0) observaciones.push(`Los ingresos superaron a los gastos registrados en ${dineroPdf(datos.balance)}.`);
  else observaciones.push('Los ingresos y gastos registrados están equilibrados.');
  if (sinCategoria) observaciones.push(`Hay gastos sin categoría por ${dineroPdf(sinCategoria.monto)} (${sinCategoria.porcentaje.toFixed(1)} % del gasto). Clasificarlos facilitará el análisis.`);
  if (categorias[0] && categorias[0].nombre !== 'Sin categoría' && categorias[0].porcentaje >= 50)
    observaciones.push(`${categorias[0].nombre} concentra el ${categorias[0].porcentaje.toFixed(1)} % del gasto registrado (${dineroPdf(categorias[0].monto)}).`);
  if (centavos(datos.flujo.neto) !== centavos(datos.balance)) observaciones.push(`El flujo neto de efectivo es ${dineroPdf(datos.flujo.neto)} y el balance es ${dineroPdf(datos.balance)}. El flujo refleja entradas y salidas de efectivo y débito; el balance incluye compras a crédito y excluye transferencias y pagos de tarjeta.`);
  observaciones.push('Patrimonio actual = saldos de efectivo y débito menos deuda de tarjetas. No equivale a dinero disponible ni al balance del período.');
  const resumen = datos.movimientos.length
    ? `Durante el período se registraron ingresos por ${dineroPdf(datos.ingresos)} y gastos por ${dineroPdf(datos.gastos)}. El balance fue ${datos.balance < 0 ? 'negativo' : datos.balance > 0 ? 'positivo' : 'equilibrado'}${datos.balance ? ` en ${dineroPdf(Math.abs(datos.balance))}` : ''}.`
    : 'No se registraron movimientos para el período y los filtros seleccionados.';
  return {
    resumen, observaciones, categorias, medios, gastoEfectivo,
    cantidad: datos.movimientos.length,
    promedio: gastos.length ? Math.round(centavos(datos.gastos) / gastos.length) / 100 : 0,
    cantidadGastos: gastos.length,
    mayores: [...datos.movimientos].sort((a, b) => Number(b.monto) - Number(a.monto)).slice(0, 5),
    // A single month uses a compact table, not a misleading time-series chart.
    mostrarEvolucion: datos.meses.length > 1 && new Set(datos.movimientos.map(m => m.fechaMovimiento?.slice(0, 7)).filter(Boolean)).size > 1
  };
}
