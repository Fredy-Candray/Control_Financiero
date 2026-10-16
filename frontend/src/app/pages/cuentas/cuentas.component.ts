import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Cuenta } from '../../models/cuenta';
import { API_BASE_URL } from '../../api.config';
import { readAuthSessionValue } from '../../services/auth.service';

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
  editActiva = true;
  banco = '';
  ultimosCuatro = '';
  editBanco = '';
  editUltimosCuatro = '';
  gmail: { configured: boolean; connected: boolean; enabled: boolean; correo: string | null; importSince: string | null; lastSync: string | null; lastError: string | null } | null = null;
  gmailHistory: { id: number; gmailMessageId: string; estado: string; comercio: string | null; monto: number | null; ultimosCuatro: string | null; fechaCompra: string | null; detalle: string }[] = [];
  gmailBusy = false;
  gmailError = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargarCuentas();
    this.cargarMovimientos();
    this.cargarGmail();
    const result = new URLSearchParams(window.location.search).get('gmail');
    if (result === 'connected') this.success = 'Gmail conectado. Activa la importación para comenzar con compras posteriores.';
    if (result === 'error') this.gmailError = 'No se pudo conectar Gmail. Revisa las credenciales y vuelve a autorizar.';
    if (result === 'cancelled') this.gmailError = 'La autorización de Gmail fue cancelada.';
  }

  cargarCuentas(): void {
    this.cargando = true;
    const usuarioId = Number(readAuthSessionValue('userId') || '1');
    this.http.get<Cuenta[]>(`${API_BASE_URL}/cuentas/usuario/${usuarioId}`).subscribe({
      next: (data) => {
        const orden = ['tarjeta economia', 'visa', 'mastercard', 'tarjeta débito', 'pay', 'efectivo'];
        const prioridad = (cuenta: Cuenta): number => {
          const nombre = cuenta.nombre.trim().toLocaleLowerCase('es');
          const posicion = orden.indexOf(nombre);
          return posicion < 0 ? orden.length : posicion;
        };
        this.cuentas = [...data].sort((a, b) => prioridad(a) - prioridad(b));
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
    if (!this.nombre.trim() || this.saldoActual < 0 || (this.tipo === 'CREDITO' && (this.limiteCredito <= 0 || this.saldoActual > this.limiteCredito))) return;
    this.guardando = true;
    const usuarioId = Number(readAuthSessionValue('userId') || '1');

    const payload: any = {
      nombre: this.nombre,
      tipo: this.tipo,
      saldoActual: this.saldoActual,
      usuarioId,
      banco: this.tipo === 'EFECTIVO' ? '' : this.banco,
      ultimosCuatro: this.tipo === 'EFECTIVO' ? '' : this.ultimosCuatro
    };

    if (this.tipo === 'CREDITO') {
      payload.limiteCredito = this.limiteCredito;
    }

    this.http.post(`${API_BASE_URL}/cuentas`, payload).subscribe({
      next: () => {
        this.guardando = false;
        this.success = 'Cuenta creada correctamente.';
        this.nombre = '';
        this.tipo = 'EFECTIVO';
        this.saldoActual = 0;
        this.limiteCredito = 0;
        this.banco = '';
        this.ultimosCuatro = '';
        this.cargarCuentas();
      },
      error: () => {
        this.guardando = false;
        this.error = 'No se pudo crear la cuenta.';
      }
    });
  }

  cargarMovimientos(): void {
    const usuarioId = Number(readAuthSessionValue('userId') || '1');
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

  get cuentaActual(): Cuenta | undefined {
    return this.cuentas.find(cuenta => cuenta.id === this.cuentaEditandoId);
  }

  etiquetaTipo(tipo: string): string {
    return tipo === 'CREDITO' ? 'Tarjeta de crédito' : tipo === 'DEBITO' ? 'Cuenta débito' : 'Efectivo';
  }

  iniciarEdicion(cuenta: Cuenta): void {
    this.cuentaEditandoId = cuenta.id;
    this.editNombre = cuenta.nombre;
    this.editLimite = Number(cuenta.limiteCredito ?? 0);
    this.editDisponible = Number(cuenta.saldoActual ?? 0);
    this.editActiva = cuenta.activa;
    this.editBanco = cuenta.banco || '';
    this.editUltimosCuatro = cuenta.ultimosCuatro || '';
    this.error = '';
    this.success = '';
  }

  cancelarEdicion(): void {
    this.cuentaEditandoId = null;
  }

  guardarCuenta(cuenta: Cuenta): void {
    if (!this.cuentaEditandoId || !this.editNombre.trim() || this.editDisponible < 0) return;
    if (cuenta.tipo === 'CREDITO' && (this.editLimite <= 0 || this.editDisponible > this.editLimite)) return;
    const usuarioId = Number(readAuthSessionValue('userId') || '1');
    this.guardando = true;
    this.error = '';
    const payload: any = {
      nombre: this.editNombre.trim(),
      saldoActual: this.editDisponible,
      activa: this.editActiva,
      banco: this.editBanco,
      ultimosCuatro: this.editUltimosCuatro
    };
    if (cuenta.tipo === 'CREDITO') payload.limiteCredito = this.editLimite;
    this.http.put(`${API_BASE_URL}/cuentas/${this.cuentaEditandoId}/usuario/${usuarioId}`, payload).subscribe({
      next: () => {
        this.guardando = false;
        this.success = 'Cuenta actualizada correctamente.';
        this.cuentaEditandoId = null;
        this.cargarCuentas();
      },
      error: (err) => {
        this.guardando = false;
        this.error = err?.error?.message || 'No se pudo actualizar la cuenta.';
      }
    });
  }

  prepararTarjetaBac(): void {
    this.nombre = 'BAC VISA · 3923';
    this.tipo = 'CREDITO';
    this.limiteCredito = 1500;
    this.saldoActual = 1500;
    this.banco = 'BAC';
    this.ultimosCuatro = '3923';
    document.getElementById('add-account-title')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  get tarjetaBacConfigurada(): boolean { return this.cuentas.some(c => c.banco === 'BAC' && c.ultimosCuatro === '3923'); }

  cargarGmail(): void {
    this.http.get<NonNullable<CuentasComponent['gmail']>>(`${API_BASE_URL}/gmail-bac`).subscribe({ next: data => this.gmail = data, error: () => this.gmailError = 'No se pudo consultar la conexión a Gmail.' });
    this.http.get<CuentasComponent['gmailHistory']>(`${API_BASE_URL}/gmail-bac/history`).subscribe({ next: data => this.gmailHistory = data, error: () => this.gmailError = 'No se pudo cargar el historial de importación.' });
  }

  conectarGmail(): void {
    this.gmailBusy = true;
    this.gmailError = '';
    this.http.post<{ url: string }>(`${API_BASE_URL}/gmail-bac/connect`, {}).subscribe({
      next: data => window.location.assign(data.url),
      error: () => { this.gmailBusy = false; this.gmailError = 'No se pudo iniciar la conexión. Revisa la configuración de Google del servidor.'; }
    });
  }

  cambiarImportacion(): void {
    this.gmailBusy = true;
    this.gmailError = '';
    this.http.put<NonNullable<CuentasComponent['gmail']>>(`${API_BASE_URL}/gmail-bac/enabled`, { enabled: !this.gmail?.enabled }).subscribe({
      next: data => { this.gmail = data; this.gmailBusy = false; this.cargarGmail(); },
      error: () => { this.gmailBusy = false; this.gmailError = 'Configura una tarjeta BAC activa y conecta Gmail antes de iniciar.'; }
    });
  }

  sincronizarGmail(): void {
    this.gmailBusy = true;
    this.gmailError = '';
    this.http.post<NonNullable<CuentasComponent['gmail']>>(`${API_BASE_URL}/gmail-bac/sync`, {}).subscribe({
      next: data => { this.gmail = data; this.gmailBusy = false; this.cargarCuentas(); this.cargarMovimientos(); this.cargarGmail(); },
      error: () => { this.gmailBusy = false; this.gmailError = 'No se pudo sincronizar. Los mensajes se reintentarán automáticamente.'; }
    });
  }

  desconectarGmail(): void {
    this.gmailBusy = true;
    this.gmailError = '';
    this.http.delete<NonNullable<CuentasComponent['gmail']>>(`${API_BASE_URL}/gmail-bac`).subscribe({
      next: data => { this.gmail = data; this.gmailBusy = false; },
      error: () => { this.gmailBusy = false; this.gmailError = 'No se pudo desconectar Gmail.'; }
    });
  }
}
