// Uses only synthetic data. Run from frontend: node scripts/verify-reportes-pdf.cjs
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const ts = require('typescript');
const root = path.resolve(__dirname, '..');
const build = path.join(root, 'tmp/pdfs/modules');
const output = path.resolve(root, '../output/pdf');
fs.mkdirSync(build, { recursive: true }); fs.mkdirSync(output, { recursive: true });
for (const name of ['reportes-calculos', 'reportes-pdf-datos', 'reportes-pdf']) {
  const source = fs.readFileSync(path.join(root, 'src/app/pages/reportes', name + '.ts'), 'utf8');
  fs.writeFileSync(path.join(build, name + '.js'), ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
}
const { crearReportePdf } = require(path.join(build, 'reportes-pdf.js'));
const logo = 'data:image/png;base64,' + fs.readFileSync(path.join(root, 'src/assets/cofi-logo.png')).toString('base64');
const { prepararAnalisisPdf, dineroPdf, fechaPdf } = require(path.join(build, 'reportes-pdf-datos.js'));
const { sumar, filtrar, flujoEfectivo } = require(path.join(build, 'reportes-calculos.js'));
const accounts = [{ id: 1, tipo: 'DEBITO', nombre: 'Cuenta de prueba', saldoActual: 1800 }, { id: 2, tipo: 'CREDITO', nombre: 'Tarjeta de prueba', saldoActual: 700, limiteCredito: 1000 }, { id: 3, tipo: 'EFECTIVO', nombre: 'Efectivo', saldoActual: 100 }];
const move = (id, tipo = 'GASTO', month = '2026-10') => ({ id, tipo, monto: (id % 9 + 1) * 7.15, fechaMovimiento: `${month}-${String(id % 27 + 1).padStart(2, '0')}T12:30:00`, cuentaOrigenId: tipo === 'TRANSFERENCIA' ? 1 : 2, cuentaDestinoId: tipo === 'TRANSFERENCIA' ? 2 : null, cuentaOrigen: 'Tarjeta de prueba', cuentaDestino: tipo === 'TRANSFERENCIA' ? 'Cuenta de destino' : undefined, categoria: id % 5 ? `Categoría ${id % 7}` : undefined, descripcion: `REF${String(id).padStart(4, '0')} Compra de prueba con descripción extensa para comprobar ajuste de líneas y legibilidad en el detalle del estado financiero.` });
const scenarios = {
  'sin-movimientos': [], 'un-movimiento': [move(1)],
  'categorias': Array.from({ length: 20 }, (_, i) => move(i + 1, i % 6 ? 'GASTO' : 'INGRESO')),
  'varios-meses': Array.from({ length: 42 }, (_, i) => move(i + 1, i % 4 ? 'GASTO' : 'INGRESO', `2026-${String(5 + i % 6).padStart(2, '0')}`)),
  'muchos-movimientos': Array.from({ length: 350 }, (_, i) => move(i + 1, i % 13 === 0 ? 'TRANSFERENCIA' : i % 7 === 0 ? 'INGRESO' : 'GASTO'))
};
for (const [name, movements] of Object.entries(scenarios)) {
  for (const movement of movements) {
    if (movement.tipo === 'GASTO' && movement.id % 5 === 0) {
      movement.cuentaOrigenId = 3; movement.cuentaOrigen = 'Efectivo';
    }
  }
  const months = name === 'varios-meses' ? ['05', '06', '07', '08', '09', '10'] : ['10'];
  const contexto = {
    vista: 'resumen', desde: `2026-${months[0]}-01`, hasta: '2026-10-31', generado: new Date('2026-10-15T18:00:00Z'),
    ingresos: sumar(movements, 'INGRESO'), gastos: sumar(movements, 'GASTO'), patrimonio: 1600, deuda: 300, dineroDisponible: 1900,
    flujo: flujoEfectivo(movements, accounts, null), movimientos: movements, cuentas: accounts,
    meses: months.map((month, i) => {
      const lista = movements.filter(m => m.fechaMovimiento.startsWith(`2026-${month}`));
      const ingresos = sumar(lista, 'INGRESO'), gastos = sumar(lista, 'GASTO');
      return { mes: `2026-${month}`, ingresos, gastos, balance: Math.round((ingresos - gastos) * 100) / 100, flujo: flujoEfectivo(lista, accounts, null).neto, patrimonio: 1200 + i * 60, deuda: 400 - i * 20 };
    })
  };
  contexto.balance = Math.round((contexto.ingresos - contexto.gastos) * 100) / 100;
  const a = prepararAnalisisPdf(contexto);
  assert.equal(Math.round(a.categorias.reduce((s, c) => s + c.monto, 0) * 100), Math.round(contexto.gastos * 100));
  assert.equal(a.cantidad, movements.length);
  assert.equal(a.mostrarEvolucion, name === 'varios-meses');
  const sections = [
    { titulo: 'Evolución mensual', columnas: ['Mes', 'Ingresos', 'Gastos', 'Balance', 'Flujo neto', 'Patrimonio al cierre', 'Deuda al cierre'], filas: contexto.meses.map(m => [m.mes, ...['ingresos', 'gastos', 'balance', 'flujo', 'patrimonio', 'deuda'].map(k => dineroPdf(m[k]))]) },
    { titulo: 'Gastos por categoría', columnas: ['Categoría', 'Monto', 'Porcentaje'], filas: a.categorias.map(c => [c.nombre, dineroPdf(c.monto), c.porcentaje.toFixed(1) + ' %']) },
    { titulo: 'Detalle completo de movimientos', columnas: ['Fecha', 'Tipo', 'Descripción', 'Categoría', 'Origen', 'Destino', 'Monto'], filas: movements.map(m => [fechaPdf(m.fechaMovimiento), m.tipo, m.descripcion, m.categoria || 'Sin categoría', m.cuentaOrigen, m.cuentaDestino || '-', dineroPdf(m.monto)]) }
  ];
  const pdf = crearReportePdf('Informe financiero: Resumen', ['Cuenta: Todas | Tipo: TODOS | Categoría: Todas | Datos simulados'], sections, contexto, logo);
  assert(Math.abs(pdf.internal.pageSize.getWidth() - 215.9) < .01);
  assert(Math.abs(pdf.internal.pageSize.getHeight() - 279.4) < .01);
  const bytes = Buffer.from(pdf.output('arraybuffer'));
  assert.equal(bytes.subarray(0, 4).toString(), '%PDF');
  fs.writeFileSync(path.join(output, `cofi-demo-${name}.pdf`), bytes);
  console.log(`${name}: ${movements.length} movimientos, ${pdf.getNumberOfPages()} páginas, ingresos=${contexto.ingresos}, gastos=${contexto.gastos}`);
}
// Filtered exports must use the same existing financial helpers and exact selected rows.
const filtered = filtrar(scenarios['categorias'], { desde: '2026-10-01', hasta: '2026-10-31', cuentaId: 2, tipo: 'GASTO', categoria: 'Categoría 1' });
assert(filtered.every(m => m.tipo === 'GASTO' && m.categoria === 'Categoría 1'));
assert(filtered.length > 0);
assert.equal(prepararAnalisisPdf({ movimientos: [], meses: [], ingresos: 0, gastos: 0, balance: 0, flujo: { neto: 0 } }).promedio, 0);
// Comparison with a zero base must never produce Infinity or a fabricated percentage.
const comparable = {
  vista: 'comparativos', desde: '2026-10-01', hasta: '2026-10-15', generado: new Date('2026-10-15T18:00:00Z'),
  ingresos: 0, gastos: 14.3, balance: -14.3, patrimonio: 1600, deuda: 300, dineroDisponible: 1900,
  flujo: { entradas: 0, salidas: 0, neto: 0 }, movimientos: [move(1)], meses: [], cuentas: accounts,
  anterior: { ingresos: 0, gastos: 10, desde: '2026-09-16', hasta: '2026-09-30' }
};
assert.equal(prepararAnalisisPdf(comparable).promedio, 14.3);
const cashScenario = { ...comparable, ingresos: 20, gastos: 16, balance: 4, movimientos: [
  { ...move(1), monto: 10, cuentaOrigenId: 2 },
  { ...move(2), monto: 6, cuentaOrigenId: 3 },
  { ...move(3, 'TRANSFERENCIA'), monto: 100, cuentaOrigenId: 3, cuentaDestinoId: 1 },
  { ...move(4, 'INGRESO'), monto: 20, cuentaOrigenId: 3 }
] };
assert.equal(prepararAnalisisPdf(cashScenario).gastoEfectivo, 6);
assert.equal(sumar(cashScenario.movimientos, 'GASTO'), 16);
assert.equal(sumar(cashScenario.movimientos, 'INGRESO'), 20);
assert.equal(prepararAnalisisPdf({ ...cashScenario, movimientos: cashScenario.movimientos.filter(m => m.cuentaOrigenId === 2) }).gastoEfectivo, 0);
const comparePdf = crearReportePdf('Comparación de registros', ['Datos simulados'], [], comparable);
assert(!comparePdf.output().includes('Infinity'));
// All specialized report tables keep their columns, labels and wrapped long text on Letter.
for (const vista of ['ingresos', 'gastos', 'flujo', 'presupuestos', 'metas', 'deudas', 'cuentas', 'patrimonio', 'comparativos']) {
  const specialized = crearReportePdf(`Informe financiero: ${vista}`, ['Datos simulados'], [{
    titulo: 'Datos de la vista seleccionada', columnas: ['Nombre', 'Mes', 'Categoría', 'Planificado', 'Gasto real', 'Utilización', 'Restante'],
    filas: [['Plan de prueba', '2026-10', 'Servicios', '$100.00', '$25.00', '25.0 %', '$75.00']]
  }], { ...comparable, vista });
  assert(specialized.getNumberOfPages() > 0);
}
console.log('Verificación de Carta, datos, filtros, transferencias y cinco escenarios: correcta.');
