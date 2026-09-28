import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Cuenta } from '../../models/cuenta';
import { API_BASE_URL } from '../../api.config';

@Component({
  selector: 'app-cuentas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cuentas.component.html',
  styleUrl: './cuentas.component.css'
})
export class CuentasComponent implements OnInit {
  cuentas: Cuenta[] = [];
  movimientos: any[] = [];
  cargando = true;
  error = '';
  success = '';

  nombre = '';
  tipo = 'EFECTIVO';
  saldoActual = 0;
  limiteCredito = 0;
  guardando = false;
  cuentaEditandoId: number | null = null;
  editNombre = '';
  editLimite = 0;
  editDisponible = 0;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargarCuentas();
    this.cargarMovimientos();
  }

  cargarCuentas(): void {
    this.cargando = true;
    const usuarioId = Number(localStorage.getItem('userId') || '1');
    this.http.get<Cuenta[]>(`${API_BASE_URL}/cuentas/usuario/${usuarioId}`).subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
        this.error = '';
      },
      error: () => {
        this.error = 'No se pudieron cargar las cuentas.';
        this.cargando = false;
      }
    });
  }

  onSubmit(): void {
    if (!this.nombre.trim() || this.saldoActual < 0 || (this.tipo === 'CREDITO' && this.limiteCredito <= 0)) return;
    this.guardando = true;
    const usuarioId = Number(localStorage.getItem('userId') || '1');

    const payload: any = {
      nombre: this.nombre,
      tipo: this.tipo,
      saldoActual: this.saldoActual,
      usuarioId
    };

    if (this.tipo === 'CREDITO') {
      payload.limiteCredito = this.limiteCredito;
      payload.saldoActual = this.limiteCredito;
    }

    this.http.post(`${API_BASE_URL}/cuentas`, payload).subscribe({
      next: () => {
        this.guardando = false;
        this.success = 'Cuenta creada correctamente.';
        this.nombre = '';
        this.tipo = 'EFECTIVO';
        this.saldoActual = 0;
        this.limiteCredito = 0;
        this.cargarCuentas();
      },
      error: () => {
        this.guardando = false;
        this.error = 'No se pudo crear la cuenta.';
      }
    });
  }

  cargarMovimientos(): void {
    const usuarioId = Number(localStorage.getItem('userId') || '1');
    this.http.get<any[]>(`${API_BASE_URL}/movimientos/usuario/${usuarioId}`).subscribe({
      next: (data) => this.movimientos = data
    });
  }

  deuda(cuenta: Cuenta): number {
    if (cuenta.limiteCredito != null) return Math.max(0, Number(cuenta.limiteCredito) - Number(cuenta.saldoActual ?? 0));
    const cargos = this.movimientos.filter(m => m.cuentaOrigenId === cuenta.id && m.tipo === 'GASTO')
      .reduce((sum, m) => sum + Number(m.monto), 0);
    const pagos = this.movimientos.filter(m => m.cuentaDestinoId === cuenta.id && ['PAGO_TARJETA', 'TRANSFERENCIA'].includes(m.tipo))
      .reduce((sum, m) => sum + Number(m.monto), 0);
    return Math.max(0, cargos - pagos);
  }

  utilizacion(cuenta: Cuenta): number {
    const limite = Number(cuenta.limiteCredito ?? 0);
    return limite > 0 ? Math.min(100, Math.round((this.deuda(cuenta) / limite) * 100)) : 0;
  }

  etiquetaTipo(tipo: string): string {
    return tipo === 'CREDITO' ? 'Tarjeta de crédito' : tipo === 'DEBITO' ? 'Cuenta débito' : 'Efectivo';
  }

  iniciarEdicion(cuenta: Cuenta): void {
    this.cuentaEditandoId = cuenta.id;
    this.editNombre = cuenta.nombre;
    this.editLimite = Number(cuenta.limiteCredito ?? 0);
    this.editDisponible = Number(cuenta.saldoActual ?? 0);
  }

  cancelarEdicion(): void {
    this.cuentaEditandoId = null;
  }

  guardarTarjeta(): void {
    if (!this.cuentaEditandoId || !this.editNombre.trim() || this.editLimite <= 0 || this.editDisponible < 0 || this.editDisponible > this.editLimite) return;
    const usuarioId = Number(localStorage.getItem('userId') || '1');
    this.guardando = true;
    this.error = '';
    this.http.put(`${API_BASE_URL}/cuentas/${this.cuentaEditandoId}/usuario/${usuarioId}`, {
      nombre: this.editNombre.trim(),
      saldoActual: this.editDisponible,
      limiteCredito: this.editLimite
    }).subscribe({
      next: () => {
        this.guardando = false;
        this.success = 'Tarjeta actualizada correctamente.';
        this.cuentaEditandoId = null;
        this.cargarCuentas();
      },
      error: (err) => {
        this.guardando = false;
        this.error = err?.error?.message || 'No se pudo actualizar la tarjeta.';
      }
    });
  }
}
