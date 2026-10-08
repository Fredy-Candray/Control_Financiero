import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Cuenta } from '../../models/cuenta';
import { API_BASE_URL } from '../../api.config';
import { readAuthSessionValue } from '../../services/auth.service';

@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './movimientos.component.html',
  styleUrl: './movimientos.component.css'
})
export class MovimientosComponent implements OnInit {
  movimientos: any[] = [];
  cuentas: Cuenta[] = [];
  categorias: { id: number; nombre: string }[] = [];
  cargando = true;
  error = '';
  success = '';
  guardando = false;
  busqueda = '';
  tipoFiltro = 'TODOS';

  tipo = 'GASTO';
  monto = 0;
  descripcion = '';
  cuentaOrigenId: number | null = null;
  cuentaDestinoId: number | null = null;
  categoriaId: number | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargarMovimientos();
    this.cargarCuentas();
    this.cargarCategorias();
  }

  cargarMovimientos(): void {
    this.cargando = true;
    const usuarioId = Number(readAuthSessionValue('userId') || '1');
    this.http.get<any[]>(`${API_BASE_URL}/movimientos/usuario/${usuarioId}`).subscribe({
      next: (data) => {
        this.movimientos = [...data].sort((a, b) => {
          const fechaA = new Date(a.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          const fechaB = new Date(b.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          return fechaB - fechaA;
        });
        this.cargando = false;
        this.error = '';
      },
      error: () => {
        this.error = 'No se pudieron cargar los movimientos.';
        this.cargando = false;
      }
    });
  }

  cargarCuentas(): void {
    const usuarioId = Number(readAuthSessionValue('userId') || '1');
    this.http.get<Cuenta[]>(`${API_BASE_URL}/cuentas/usuario/${usuarioId}`).subscribe({
      next: (data) => {
        this.cuentas = data;
      }
    });
  }

  cargarCategorias(): void {
    this.http.get<{ id: number; nombre: string }[]>(`${API_BASE_URL}/categorias`).subscribe({
      next: (data) => this.categorias = data
    });
  }

  onSubmit(): void {
    if (!this.puedeGuardar) return;
    const usuarioId = Number(readAuthSessionValue('userId') || '1');
    this.error = '';
    this.success = '';
    this.guardando = true;

    this.http.post(`${API_BASE_URL}/movimientos`, {
      tipo: this.tipo,
      monto: this.monto,
      descripcion: this.descripcion,
      cuentaOrigenId: this.cuentaOrigenId,
      cuentaDestinoId: this.cuentaDestinoId,
      categoriaId: this.categoriaId,
      usuarioId
    }).subscribe({
      next: () => {
        this.guardando = false;
        this.success = 'Movimiento registrado correctamente.';
        this.tipo = 'GASTO';
        this.monto = 0;
        this.descripcion = '';
        this.cuentaOrigenId = null;
        this.cuentaDestinoId = null;
        this.categoriaId = null;
        this.cargarMovimientos();
        this.cargarCuentas();
      },
      error: (err) => {
        this.guardando = false;
        console.error('Error POST /api/movimientos', err);
        // Mostrar mensaje devuelto por el servidor si existe, o el body completo
        if (err && err.error) {
          try {
            // err.error puede ser objeto o texto
            this.error = err.error.message || JSON.stringify(err.error);
          } catch (e) {
            this.error = String(err.error);
          }
        } else {
          this.error = err.message || 'No se pudo registrar el movimiento.';
        }
      }
    });
  }

  get puedeGuardar(): boolean {
    if (!this.cuentaOrigenId || this.monto <= 0) return false;
    if ((this.tipo === 'TRANSFERENCIA' || this.tipo === 'PAGO_TARJETA') &&
        (!this.cuentaDestinoId || this.cuentaDestinoId === this.cuentaOrigenId)) return false;
    if (this.tipo === 'PAGO_TARJETA' && !this.cuentas.find(c => c.id === this.cuentaDestinoId && c.tipo === 'CREDITO')) return false;
    return true;
  }

  get cuentasOrigen(): Cuenta[] {
    if (this.tipo !== 'GASTO') return this.cuentas.filter(c => c.tipo !== 'CREDITO');
    return this.cuentas.filter(c => c.tipo !== 'CREDITO' || c.limiteCredito != null);
  }

  get cuentasDestino(): Cuenta[] {
    const candidatas = this.tipo === 'PAGO_TARJETA'
      ? this.cuentas.filter(c => c.tipo === 'CREDITO')
      : this.cuentas;
    return candidatas.filter(c => c.id !== this.cuentaOrigenId);
  }

  get movimientosFiltrados(): any[] {
    const consulta = this.busqueda.trim().toLocaleLowerCase();
    return this.movimientos.filter(m => {
      const coincideTipo = this.tipoFiltro === 'TODOS' || m.tipo === this.tipoFiltro;
      const texto = [m.descripcion, m.tipo, m.cuentaOrigen, m.cuentaDestino, m.categoria].filter(Boolean).join(' ').toLocaleLowerCase();
      return coincideTipo && (!consulta || texto.includes(consulta));
    });
  }

  etiquetaTipo(tipo: string): string {
    return ({ INGRESO: 'Ingreso', GASTO: 'Gasto', TRANSFERENCIA: 'Transferencia', PAGO_TARJETA: 'Pago de tarjeta' } as Record<string, string>)[tipo] ?? tipo;
  }

  esSalida(tipo: string): boolean {
    return tipo === 'GASTO' || tipo === 'PAGO_TARJETA' || tipo === 'TRANSFERENCIA';
  }
}
