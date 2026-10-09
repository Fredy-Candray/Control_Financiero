import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../api.config';
import { Cuenta } from '../../models/cuenta';
import { agruparCategorias, celdaCsv, centavos, efectoCuenta, filtrar, FiltrosReporte, flujoEfectivo, MovimientoReporte, patrimonioEn, PlanReporte, sumar } from './reportes-calculos';

@Component({
  selector: 'app-reportes', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './reportes.component.html', styleUrl: './reportes.component.css'
})
export class ReportesComponent implements OnInit {
  cuentas: Cuenta[] = [];
  movimientos: MovimientoReporte[] = [];
  planes: PlanReporte[] = [];
  cargando = true;
  datosDisponibles = false;
  guardando = false;
  descargandoPdf = false;
  errorPdf = '';
  error = '';
  mensaje = '';
  tab = 'resumen';
  pagina = 1;
  agrupacion = 'mes';
  readonly tamanoPagina = 20;
  readonly hoy = this.fechaLocal(new Date());
  filtros: FiltrosReporte = { desde: this.hoy.slice(0, 7) + '-01', hasta: this.hoy, cuentaId: null, tipo: 'TODOS', categoria: '' };
  formulario: PlanReporte | null = null;
  tabs = [
    { id: 'resumen', nombre: 'Resumen' }, { id: 'ingresos', nombre: 'Ingresos' }, { id: 'gastos', nombre: 'Gastos' },
    { id: 'flujo', nombre: 'Flujo de efectivo' }, { id: 'presupuestos', nombre: 'Presupuestos' },
    { id: 'metas', nombre: 'Ahorros y metas' }, { id: 'deudas', nombre: 'Deudas' },
    { id: 'cuentas', nombre: 'Cuentas' }, { id: 'patrimonio', nombre: 'Patrimonio' }, { id: 'comparativos', nombre: 'Comparativos' }
  ];
  constructor(private http: HttpClient) {}
  ngOnInit(): void { this.cargar(); }
  cargar(): void {
    this.cargando = true;
    this.datosDisponibles = false;
    this.error = '';
    this.http.get<{ cuentas: Cuenta[]; movimientos: MovimientoReporte[]; planes: PlanReporte[] }>(`${API_BASE_URL}/reportes`).subscribe({
      next: datos => { this.cuentas = datos.cuentas; this.movimientos = datos.movimientos; this.planes = datos.planes; this.cargando = false; this.datosDisponibles = true; },
      error: err => { this.cargando = false; this.error = err.status === 403 ? 'Tu rol no tiene permiso para consultar reportes.' : 'No se pudieron cargar los reportes. Verifica tu sesión y vuelve a intentar.'; }
    });
  }
  fechaLocal(fecha: Date): string { return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`; }
  periodo(meses: number): void {
    const inicio = new Date(); inicio.setDate(1); inicio.setMonth(inicio.getMonth() - meses + 1);
    this.filtros.desde = this.fechaLocal(inicio); this.filtros.hasta = this.hoy; this.pagina = 1;
  }
  get fechasValidas(): boolean { return !!this.filtros.desde && !!this.filtros.hasta && this.filtros.desde <= this.filtros.hasta && this.filtros.hasta <= this.hoy; }
  get datos(): MovimientoReporte[] { return this.fechasValidas ? filtrar(this.movimientos, this.filtros) : []; }
  get ingresos(): number { return sumar(this.datos, 'INGRESO'); }
  get gastos(): number { return sumar(this.datos, 'GASTO'); }
  get balance(): number { return (centavos(this.ingresos) - centavos(this.gastos)) / 100; }
  get ahorroPorcentaje(): number | null { return this.ingresos ? this.balance / this.ingresos * 100 : null; }
  get categorias(): string[] { return [...new Set(this.movimientos.map(m => m.categoria || 'Sin categoría'))].sort(); }
  get cuentasSeleccionadas(): Cuenta[] { return this.cuentas.filter(c => !this.filtros.cuentaId || c.id === this.filtros.cuentaId); }
  get tarjetas(): Cuenta[] { return this.cuentas.filter(c => c.tipo === 'CREDITO'); }
  deuda(c: Cuenta): number { return Math.max(0, (centavos(c.limiteCredito ?? 0) - centavos(c.saldoActual)) / 100); }
  get deudaTotal(): number { return this.cuentasSeleccionadas.reduce((s, c) => s + (c.tipo === 'CREDITO' ? centavos(this.deuda(c)) : 0), 0) / 100; }
  get patrimonio(): number { return this.cuentasSeleccionadas.reduce((s, c) => s + (c.tipo === 'CREDITO' ? -centavos(this.deuda(c)) : centavos(c.saldoActual)), 0) / 100; }
  get flujo(): { entradas: number; salidas: number; neto: number } { return flujoEfectivo(this.datos, this.cuentas, this.filtros.cuentaId); }
  get categoriasIngreso() { return agruparCategorias(this.datos, 'INGRESO'); }
  get categoriasGasto() { return agruparCategorias(this.datos, 'GASTO'); }
  get anteriores(): MovimientoReporte[] {
    const inicio = new Date(this.filtros.desde + 'T12:00:00');
    const fin = new Date(this.filtros.hasta + 'T12:00:00');
    const dias = Math.round((fin.getTime() - inicio.getTime()) / 86400000) + 1;
    const hasta = new Date(inicio); hasta.setDate(hasta.getDate() - 1);
    const desde = new Date(hasta); desde.setDate(desde.getDate() - dias + 1);
    return this.fechasValidas ? filtrar(this.movimientos, { ...this.filtros, desde: this.fechaLocal(desde), hasta: this.fechaLocal(hasta) }) : [];
  }
  variacion(tipo: string): string {
    const anterior = sumar(this.anteriores, tipo), actual = sumar(this.datos, tipo);
    return anterior ? `${((actual - anterior) / anterior * 100).toFixed(1)} %` : actual ? 'Sin base de comparación' : '0 %';
  }
  anterior(tipo: string): number { return sumar(this.anteriores, tipo); }
  diferencia(tipo: string): number { return (centavos(sumar(this.datos, tipo)) - centavos(this.anterior(tipo))) / 100; }
  get comparaciones() {
    const grupos = new Map<string, { periodo: string; ingresos: number; gastos: number; balance: number }>();
    for (const m of this.meses) {
      const periodo = this.agrupacion === 'anio' ? m.mes.slice(0, 4) : this.agrupacion === 'trimestre' ? `${m.mes.slice(0, 4)} · T${Math.ceil(Number(m.mes.slice(5)) / 3)}` : m.mes;
      const grupo = grupos.get(periodo) ?? { periodo, ingresos: 0, gastos: 0, balance: 0 };
      grupo.ingresos = (centavos(grupo.ingresos) + centavos(m.ingresos)) / 100;
      grupo.gastos = (centavos(grupo.gastos) + centavos(m.gastos)) / 100;
      grupo.balance = (centavos(grupo.ingresos) - centavos(grupo.gastos)) / 100;
      grupos.set(periodo, grupo);
    }
    return [...grupos.values()];
  }
  get meses() {
    if (!this.fechasValidas) return [];
    const resultado: { mes: string; ingresos: number; gastos: number; balance: number; flujo: number; patrimonio: number; deuda: number }[] = [];
    const fecha = new Date(this.filtros.desde.slice(0, 7) + '-01T12:00:00');
    while (this.fechaLocal(fecha).slice(0, 7) <= this.filtros.hasta.slice(0, 7)) {
      const mes = this.fechaLocal(fecha).slice(0, 7);
      const lista = this.datos.filter(m => m.fechaMovimiento?.startsWith(mes));
      const ingresos = sumar(lista, 'INGRESO'), gastos = sumar(lista, 'GASTO');
      const ultimo = this.fechaLocal(new Date(fecha.getFullYear(), fecha.getMonth() + 1, 0));
      const cierre = ultimo > this.filtros.hasta ? this.filtros.hasta : ultimo;
      const deuda = -patrimonioEn(cierre, this.cuentasSeleccionadas.filter(c => c.tipo === 'CREDITO'), this.movimientos);
      resultado.push({ mes, ingresos, gastos, balance: (centavos(ingresos) - centavos(gastos)) / 100, flujo: flujoEfectivo(lista, this.cuentas, this.filtros.cuentaId).neto, patrimonio: patrimonioEn(cierre, this.cuentasSeleccionadas, this.movimientos), deuda: Math.max(0, deuda) });
      fecha.setMonth(fecha.getMonth() + 1);
    }
    return resultado;
  }
  altura(valor: number): number { return Math.abs(valor) / Math.max(1, ...this.meses.flatMap(m => [m.ingresos, m.gastos])) * 100; }
  get presupuestos() {
    return this.planes.filter(p => p.tipo === 'PRESUPUESTO' && !!p.mes && p.mes >= this.filtros.desde.slice(0, 7) && p.mes <= this.filtros.hasta.slice(0, 7) && (!this.filtros.categoria || p.categoria === this.filtros.categoria)).map(p => {
      const gastado = sumar(this.movimientos.filter(m => m.fechaMovimiento?.startsWith(p.mes!) && (m.categoria || 'Sin categoría') === p.categoria), 'GASTO');
      return { plan: p, gastado, porcentaje: gastado / p.monto * 100, restante: (centavos(p.monto) - centavos(gastado)) / 100 };
    });
  }
  get metas() { return this.planes.filter(p => p.tipo === 'META'); }
  avance(p: PlanReporte): number { return Number(p.acumulado ?? 0) / Number(p.monto) * 100; }
  estimacion(p: PlanReporte): string {
    if (Number(p.acumulado ?? 0) >= p.monto) return 'Meta alcanzada';
    if (!p.aporteMensual) return 'Registra un aporte mensual para estimar la fecha';
    const meses = Math.ceil((centavos(p.monto) - centavos(p.acumulado ?? 0)) / centavos(p.aporteMensual));
    const fecha = new Date(); fecha.setDate(1); fecha.setMonth(fecha.getMonth() + meses);
    return `Estimación: ${this.fechaLocal(fecha).slice(0, 7)} (${meses} meses)`;
  }
  detalleDeuda(c: Cuenta): PlanReporte | undefined { return this.planes.find(p => p.tipo === 'DEUDA' && p.cuentaId === c.id); }
  pagos(c: Cuenta): number { return this.datos.filter(m => m.cuentaDestinoId === c.id && (m.tipo === 'PAGO_TARJETA' || m.tipo === 'TRANSFERENCIA')).reduce((s, m) => s + centavos(m.monto), 0) / 100; }
  entradas(c: Cuenta): number { return this.datos.reduce((s, m) => s + Math.max(0, efectoCuenta(m, c.id)), 0) / 100; }
  salidas(c: Cuenta): number { return this.datos.reduce((s, m) => s - Math.min(0, efectoCuenta(m, c.id)), 0) / 100; }
  get elevados(): MovimientoReporte[] { return this.datos.filter(m => m.tipo === 'GASTO').sort((a, b) => b.monto - a.monto).slice(0, 5); }
  get recurrentes() {
    const grupos = new Map<string, { descripcion: string; veces: number; monto: number }>();
    for (const m of this.datos.filter(m => m.tipo === 'GASTO' && m.descripcion?.trim())) {
      const key = `${m.descripcion!.trim().toLocaleLowerCase()}|${centavos(m.monto)}|${m.cuentaOrigenId}`;
      const grupo = grupos.get(key) ?? { descripcion: m.descripcion!, veces: 0, monto: Number(m.monto) };
      grupo.veces++; grupos.set(key, grupo);
    }
    return [...grupos.values()].filter(g => g.veces > 1).sort((a, b) => b.veces - a.veces);
  }
  get listado(): MovimientoReporte[] { return this.datos.filter(m => this.tab === 'ingresos' ? m.tipo === 'INGRESO' : this.tab === 'gastos' ? m.tipo === 'GASTO' : true); }
  get paginas(): number { return Math.max(1, Math.ceil(this.listado.length / this.tamanoPagina)); }
  get filas(): MovimientoReporte[] { return this.listado.slice((this.pagina - 1) * this.tamanoPagina, this.pagina * this.tamanoPagina); }
  etiqueta(tipo: string): string { return ({ INGRESO: 'Ingreso', GASTO: 'Gasto', TRANSFERENCIA: 'Transferencia', PAGO_TARJETA: 'Pago de tarjeta' } as Record<string, string>)[tipo] ?? tipo; }
  nuevo(tipo: PlanReporte['tipo'], plan?: PlanReporte): void {
    this.mensaje = ''; this.error = '';
    this.formulario = plan ? { ...plan } : { tipo, nombre: '', categoria: tipo === 'PRESUPUESTO' ? 'Sin categoría' : null, mes: tipo === 'PRESUPUESTO' ? this.filtros.desde.slice(0, 7) : null, cuentaId: null, monto: 1, acumulado: 0, aporteMensual: 0, tasaInteres: null, fechaObjetivo: null };
    setTimeout(() => document.querySelector('app-reportes .plan-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  }
  guardar(): void {
    if (!this.formulario || this.guardando) return;
    this.guardando = true; this.error = '';
    const plan = { ...this.formulario, fechaObjetivo: this.formulario.fechaObjetivo || null };
    const request = plan.id ? this.http.put<PlanReporte>(`${API_BASE_URL}/reportes/planes/${plan.id}`, plan) : this.http.post<PlanReporte>(`${API_BASE_URL}/reportes/planes`, plan);
    request.subscribe({ next: guardado => {
      this.planes = [...this.planes.filter(p => p.id !== guardado.id), guardado]; this.guardando = false; this.formulario = null; this.mensaje = 'Registro guardado correctamente.';
    }, error: err => { this.guardando = false; this.error = err.error?.detail || err.error?.message || 'No se pudo guardar. Revisa los datos y vuelve a intentar.'; } });
  }
  exportar(): void {
    const filas: unknown[][] = [['Reporte financiero', this.filtros.desde, this.filtros.hasta], ['Vista', this.tabs.find(t => t.id === this.tab)?.nombre], ['Cuenta', this.cuentas.find(c => c.id === this.filtros.cuentaId)?.nombre || 'Todas'], ['Tipo', this.filtros.tipo], ['Categoría', this.filtros.categoria || 'Todas'], ['Moneda', 'USD'], ['Ingresos', this.ingresos], ['Gastos', this.gastos], ['Balance', this.balance], ['Deuda actual', this.deudaTotal], ['Patrimonio actual', this.patrimonio], [], ['Fecha', 'Tipo', 'Descripción', 'Categoría', 'Cuenta origen', 'Cuenta destino', 'Monto USD']];
    for (const m of this.listado) filas.push([m.fechaMovimiento, this.etiqueta(m.tipo), m.descripcion, m.categoria, m.cuentaOrigen, m.cuentaDestino, Number(m.monto).toFixed(2)]);
    filas.push([], ['Mes', 'Ingresos', 'Gastos', 'Balance', 'Flujo neto']);
    for (const mes of this.meses) filas.push([mes.mes, mes.ingresos, mes.gastos, mes.balance, mes.flujo]);
    filas.push([], ['Presupuesto', 'Mes', 'Categoría', 'Planificado', 'Gasto real (mes completo)', 'Restante']);
    for (const p of this.presupuestos) filas.push([p.plan.nombre, p.plan.mes, p.plan.categoria, p.plan.monto, p.gastado, p.restante]);
    filas.push([], ['Meta', 'Objetivo', 'Acumulado declarado', 'Aporte mensual', 'Fecha objetivo']);
    for (const p of this.metas) filas.push([p.nombre, p.monto, p.acumulado, p.aporteMensual, p.fechaObjetivo]);
    filas.push([], ['Cuenta', 'Saldo / crédito disponible actual', 'Deuda actual', 'Pagos de tarjeta del período']);
    for (const c of this.cuentasSeleccionadas) filas.push([c.nombre, c.saldoActual, c.tipo === 'CREDITO' ? this.deuda(c) : '', c.tipo === 'CREDITO' ? this.pagos(c) : '']);
    const blob = new Blob(['\uFEFF' + filas.map(f => f.map(celdaCsv).join(';')).join('\r\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob), enlace = document.createElement('a');
    enlace.href = url; enlace.download = `reporte-${this.filtros.desde}-${this.filtros.hasta}.csv`; enlace.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  async descargarPdf(): Promise<void> {
    if (this.descargandoPdf || !this.datosDisponibles || !this.fechasValidas) return;
    this.descargandoPdf = true;
    this.errorPdf = '';
    try {
      const { crearReportePdf } = await import('./reportes-pdf');
      const dinero = (valor: number) => new Intl.NumberFormat('es-SV', { style: 'currency', currency: 'USD' }).format(Number(valor));
      const secciones: import('./reportes-pdf').SeccionPdf[] = [];
      const tabla = (titulo: string, columnas: string[], filas: string[][], nota?: string) => secciones.push({ titulo, columnas, filas, nota });
      const vista = this.tabs.find(t => t.id === this.tab)?.nombre || 'Resumen';
      tabla('Resumen financiero', ['Ingresos', 'Gastos', 'Balance del período', 'Deuda actual', 'Patrimonio actual'], [[dinero(this.ingresos), dinero(this.gastos), dinero(this.balance), dinero(this.deudaTotal), dinero(this.patrimonio)]], 'Las transferencias y pagos de tarjeta se excluyen del balance. Deuda y patrimonio actuales corresponden al saldo de hoy.');
      if (['resumen', 'ingresos', 'gastos', 'comparativos', 'flujo', 'metas', 'patrimonio', 'deudas'].includes(this.tab)) {
        tabla('Evolución mensual', ['Mes', 'Ingresos', 'Gastos', 'Balance', 'Flujo neto', 'Patrimonio al cierre', 'Deuda al cierre'], this.meses.map(m => [m.mes, dinero(m.ingresos), dinero(m.gastos), dinero(m.balance), dinero(m.flujo), dinero(m.patrimonio), dinero(m.deuda)]), 'Patrimonio y deuda históricos se reconstruyen desde los saldos actuales y el historial completo. El primer y último mes pueden ser parciales.');
      }
      if (['resumen', 'ingresos'].includes(this.tab)) tabla('Ingresos por categoría / fuente', ['Categoría', 'Monto', 'Porcentaje'], this.categoriasIngreso.map(c => [c.nombre, dinero(c.monto), c.porcentaje.toFixed(1) + ' %']));
      if (['resumen', 'gastos'].includes(this.tab)) tabla('Gastos por categoría', ['Categoría', 'Monto', 'Porcentaje'], this.categoriasGasto.map(c => [c.nombre, dinero(c.monto), c.porcentaje.toFixed(1) + ' %']));
      if (this.tab === 'gastos') {
        tabla('Gastos más elevados', ['Descripción', 'Cuenta', 'Monto'], this.elevados.map(m => [m.descripcion || 'Gasto', m.cuentaOrigen || '—', dinero(m.monto)]));
        tabla('Posibles gastos recurrentes', ['Descripción', 'Registros', 'Monto por registro'], this.recurrentes.map(r => [r.descripcion, String(r.veces), dinero(r.monto)]));
      }
      if (this.tab === 'flujo') tabla('Flujo de efectivo', ['Entradas', 'Salidas', 'Flujo neto'], [[dinero(this.flujo.entradas), dinero(this.flujo.salidas), dinero(this.flujo.neto)]], 'Incluye efectivo y débito. Las transferencias entre las cuentas seleccionadas se compensan; las compras a crédito afectan el flujo al pagar la tarjeta.');
      if (this.tab === 'presupuestos') tabla('Presupuestos', ['Nombre', 'Mes', 'Categoría', 'Planificado', 'Gasto real', 'Utilización', 'Restante'], this.presupuestos.map(p => [p.plan.nombre, p.plan.mes || '', p.plan.categoria || '', dinero(p.plan.monto), dinero(p.gastado), p.porcentaje.toFixed(1) + ' %', dinero(p.restante)]), 'El gasto real incluye el mes completo para cada categoría, sin los filtros de cuenta y tipo.');
      if (this.tab === 'metas') tabla('Metas de ahorro', ['Meta', 'Objetivo', 'Acumulado', 'Progreso', 'Aporte mensual', 'Fecha objetivo', 'Estimación'], this.metas.map(p => [p.nombre, dinero(p.monto), dinero(p.acumulado || 0), this.avance(p).toFixed(1) + ' %', dinero(p.aporteMensual || 0), p.fechaObjetivo || 'Sin registrar', this.estimacion(p)]), 'Acumulado declarado manualmente. Las estimaciones suponen aportes constantes y no incluyen rendimientos.');
      if (this.tab === 'cuentas') tabla('Cuentas financieras', ['Cuenta', 'Tipo', 'Saldo / disponible actual', 'Entradas del período', 'Salidas del período'], this.cuentasSeleccionadas.map(c => [c.nombre, c.tipo, dinero(c.saldoActual), dinero(this.entradas(c)), dinero(this.salidas(c))]));
      if (this.tab === 'deudas') tabla('Tarjetas de crédito', ['Tarjeta', 'Límite', 'Disponible', 'Deuda', 'Pagos del período', 'Tasa anual', 'Interés mensual estimado', 'Vencimiento'], this.cuentasSeleccionadas.filter(c => c.tipo === 'CREDITO').map(c => {
        const p = this.detalleDeuda(c);
        return [c.nombre, dinero(c.limiteCredito || 0), dinero(c.saldoActual), dinero(this.deuda(c)), dinero(this.pagos(c)), p?.tasaInteres == null ? 'Sin registrar' : p.tasaInteres + ' %', p?.tasaInteres == null ? 'Sin registrar' : dinero(this.deuda(c) * p.tasaInteres / 1200), p?.fechaObjetivo || 'Sin registrar'];
      }), 'El interés es una estimación simple, no un cargo registrado por el banco.');
      if (this.tab === 'comparativos') {
        tabla('Comparación de períodos', ['Período', 'Ingresos', 'Gastos', 'Balance'], this.comparaciones.map(c => [c.periodo, dinero(c.ingresos), dinero(c.gastos), dinero(c.balance)]));
        tabla('Variación frente al período anterior', ['Indicador', 'Anterior', 'Actual', 'Variación absoluta', 'Variación porcentual'], ['INGRESO', 'GASTO'].map(tipo => [this.etiqueta(tipo), dinero(this.anterior(tipo)), dinero(sumar(this.datos, tipo)), dinero(this.diferencia(tipo)), this.variacion(tipo)]));
      }
      if (['resumen', 'ingresos', 'gastos', 'cuentas', 'flujo'].includes(this.tab)) tabla('Detalle completo de movimientos', ['Fecha', 'Tipo', 'Descripción', 'Categoría', 'Origen', 'Destino', 'Monto'], this.listado.map(m => [m.fechaMovimiento?.replace('T', ' ').slice(0, 16) || '—', this.etiqueta(m.tipo), m.descripcion || '—', m.categoria || 'Sin categoría', m.cuentaOrigen || '—', m.cuentaDestino || '—', dinero(m.monto)]));
      const doc = crearReportePdf(`Reporte: ${vista}`, [`Período: ${this.filtros.desde} a ${this.filtros.hasta} · Moneda: USD`, `Cuenta: ${this.cuentas.find(c => c.id === this.filtros.cuentaId)?.nombre || 'Todas'} · Tipo: ${this.filtros.tipo} · Categoría: ${this.filtros.categoria || 'Todas'}`], secciones);
      doc.save(`reporte-${this.tab}-${this.filtros.desde}-${this.filtros.hasta}.pdf`);
    } catch {
      this.errorPdf = 'No se pudo generar el PDF. Vuelve a intentar la descarga.';
    } finally { this.descargandoPdf = false; }
  }
}
