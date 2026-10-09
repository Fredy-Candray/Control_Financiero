import { Cuenta } from '../../models/cuenta';
import { agruparCategorias, celdaCsv, filtrar, flujoEfectivo, MovimientoReporte, patrimonioEn, sumar } from './reportes-calculos';

describe('Cálculos de reportes financieros', () => {
  const cuentas: Cuenta[] = [
    { id: 1, nombre: 'Banco', tipo: 'DEBITO', saldoActual: 850, activa: true },
    { id: 2, nombre: 'Efectivo', tipo: 'EFECTIVO', saldoActual: 100, activa: true },
    { id: 3, nombre: 'Tarjeta', tipo: 'CREDITO', saldoActual: 900, limiteCredito: 1000, activa: true }
  ];
  const movimiento = (id: number, tipo: string, monto: number, origen: number, destino: number | null = null): MovimientoReporte => ({
    id, tipo, monto, cuentaOrigenId: origen, cuentaDestinoId: destino, fechaMovimiento: '2026-10-08T12:00:00', categoria: 'General'
  });
  const datos = [movimiento(1, 'INGRESO', 1000, 1), movimiento(2, 'TRANSFERENCIA', 100, 1, 2), movimiento(3, 'GASTO', 150, 3), movimiento(4, 'PAGO_TARJETA', 50, 1, 3)];
  it('no cuenta transferencias y pagos de tarjeta como nuevos gastos', () => {
    expect(sumar(datos, 'INGRESO')).toBe(1000);
    expect(sumar(datos, 'GASTO')).toBe(150);
  });
  it('compensa las transferencias internas y registra la salida al pagar crédito', () => {
    expect(flujoEfectivo(datos, cuentas, null)).toEqual({ entradas: 1000, salidas: 50, neto: 950 });
    expect(flujoEfectivo(datos, cuentas, 2)).toEqual({ entradas: 100, salidas: 0, neto: 100 });
    expect(flujoEfectivo(datos, cuentas, 3)).toEqual({ entradas: 0, salidas: 0, neto: 0 });
  });
  it('incluye destinos y el día final sin perder operaciones a medianoche', () => {
    expect(filtrar(datos, { desde: '2026-10-01', hasta: '2026-10-08', cuentaId: 3, tipo: 'TODOS', categoria: '' }).map(m => m.id)).toEqual([4, 3]);
  });
  it('reconstruye patrimonio sin contabilizar límite de crédito como activo', () => {
    expect(patrimonioEn('2026-10-08', cuentas, datos)).toBe(850);
    expect(patrimonioEn('2026-10-07', cuentas, datos)).toBe(0);
  });
  it('suma en centavos y agrupa movimientos sin categoría', () => {
    const lista = [ { ...movimiento(5, 'GASTO', .1, 1), categoria: undefined }, { ...movimiento(6, 'GASTO', .2, 1), categoria: undefined } ];
    expect(sumar(lista, 'GASTO')).toBe(.3);
    expect(agruparCategorias(lista, 'GASTO')).toEqual([{ nombre: 'Sin categoría', monto: .3, porcentaje: 100 }]);
  });
  it('neutraliza fórmulas y escapa comillas en los CSV', () => {
    expect(celdaCsv('=SUM(A1)')).toBe('"\'=SUM(A1)"');
    expect(celdaCsv('Compra "mensual"')).toBe('"Compra ""mensual"""');
    expect(celdaCsv(-100)).toBe('"-100"');
  });
});
