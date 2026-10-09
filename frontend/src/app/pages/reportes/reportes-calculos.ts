import { Cuenta } from '../../models/cuenta';
import { MovimientoDashboard } from '../../models/movimiento-dashboard';

export interface MovimientoReporte extends MovimientoDashboard {
  cuentaOrigenId: number | null;
  cuentaDestinoId: number | null;
}
export interface PlanReporte {
  id?: number;
  tipo: 'PRESUPUESTO' | 'META' | 'DEUDA';
  nombre: string;
  categoria: string | null;
  mes: string | null;
  cuentaId: number | null;
  monto: number;
  acumulado: number | null;
  aporteMensual: number | null;
  tasaInteres: number | null;
  fechaObjetivo: string | null;
}
export interface FiltrosReporte { desde: string; hasta: string; cuentaId: number | null; tipo: string; categoria: string; }
export const centavos = (valor: number): number => Math.round(Number(valor) * 100);
export const sumar = (lista: MovimientoReporte[], tipo: string): number => lista.filter(m => m.tipo === tipo).reduce((s, m) => s + centavos(m.monto), 0) / 100;

export function filtrar(movimientos: MovimientoReporte[], filtros: FiltrosReporte): MovimientoReporte[] {
  return movimientos.filter(m => {
    const fecha = m.fechaMovimiento?.slice(0, 10) ?? '';
    return fecha >= filtros.desde && fecha <= filtros.hasta
      && (!filtros.cuentaId || m.cuentaOrigenId === filtros.cuentaId || m.cuentaDestinoId === filtros.cuentaId)
      && (filtros.tipo === 'TODOS' || m.tipo === filtros.tipo)
      && (!filtros.categoria || (m.categoria || 'Sin categoría') === filtros.categoria);
  }).sort((a, b) => (b.fechaMovimiento ?? '').localeCompare(a.fechaMovimiento ?? '') || b.id - a.id);
}

export function efectoCuenta(m: MovimientoReporte, id: number): number {
  let efecto = 0;
  if (m.cuentaOrigenId === id) efecto += m.tipo === 'INGRESO' ? centavos(m.monto) : -centavos(m.monto);
  if (m.cuentaDestinoId === id) efecto += centavos(m.monto);
  return efecto;
}

export function flujoEfectivo(movimientos: MovimientoReporte[], cuentas: Cuenta[], cuentaId: number | null): { entradas: number; salidas: number; neto: number } {
  const ids = cuentas.filter(c => c.tipo !== 'CREDITO' && (!cuentaId || c.id === cuentaId)).map(c => c.id);
  let entradas = 0, salidas = 0;
  for (const m of movimientos) {
    const efecto = ids.reduce((s, id) => s + efectoCuenta(m, id), 0);
    if (efecto > 0) entradas += efecto;
    else salidas -= efecto;
  }
  return { entradas: entradas / 100, salidas: salidas / 100, neto: (entradas - salidas) / 100 };
}

export function patrimonioEn(fecha: string, cuentas: Cuenta[], movimientos: MovimientoReporte[]): number {
  return cuentas.filter(c => !c.fechaCreacion || c.fechaCreacion.slice(0, 10) <= fecha).reduce((total, c) => {
    const posteriores = movimientos.filter(m => (m.fechaMovimiento?.slice(0, 10) ?? '') > fecha);
    const saldo = centavos(c.saldoActual) - posteriores.reduce((s, m) => s + efectoCuenta(m, c.id), 0);
    return total + saldo - (c.tipo === 'CREDITO' ? centavos(c.limiteCredito ?? 0) : 0);
  }, 0) / 100;
}

export function agruparCategorias(movimientos: MovimientoReporte[], tipo: string): { nombre: string; monto: number; porcentaje: number }[] {
  const grupos = new Map<string, number>();
  for (const m of movimientos.filter(m => m.tipo === tipo)) {
    const nombre = m.categoria || 'Sin categoría';
    grupos.set(nombre, (grupos.get(nombre) ?? 0) + centavos(m.monto));
  }
  const total = [...grupos.values()].reduce((s, v) => s + v, 0);
  return [...grupos.entries()].map(([nombre, valor]) => ({ nombre, monto: valor / 100, porcentaje: total ? valor / total * 100 : 0 })).sort((a, b) => b.monto - a.monto);
}

export function celdaCsv(valor: unknown): string {
  let texto = String(valor ?? '');
  // Las descripciones de los usuarios deben exportarse como texto, nunca como fórmulas.
  if (typeof valor === 'string' && /^[\s]*[=+@-]/.test(texto)) texto = "'" + texto;
  return '"' + texto.replace(/"/g, '""') + '"';
}
