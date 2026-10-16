import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer, of } from 'rxjs';
import { catchError, exhaustMap } from 'rxjs/operators';
import { CommonModule } from '@angular/common';

import { DashboardService, EstadoSincronizacion } from '../../services/dashboard.service';
import { ThemeService } from '../../services/theme.service';
import { ResumenDashboard } from '../../models/resumen-dashboard';
import { MovimientoDashboard } from '../../models/movimiento-dashboard';
import { readAuthSessionValue } from '../../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  sincronizacion: EstadoSincronizacion | null = null;
  consultandoSincronizacion = true;
  errorSincronizacion = false;

  get estadoSincronizacion(): string {
    if (this.consultandoSincronizacion) return 'Consultando estado…';
    if (this.errorSincronizacion) return 'No se pudo consultar la sincronización';
    const estado = this.sincronizacion;
    if (!estado?.configured) return 'Sincronización sin configurar';
    if (!estado.connected) return 'Gmail desconectado';
    if (estado.lastError) return 'Error al revisar los correos';
    if (!estado.enabled) return 'Sincronización pausada';
    if (!estado.lastSync) return 'Esperando la primera revisión';
    if (Date.now() - new Date(estado.lastSync).getTime() > 180_000) return 'Sin revisión reciente';
    return 'Sincronización activa';
  }

  themeToggle = false;

  resumen?: ResumenDashboard;
  movimientos: MovimientoDashboard[] = [];
  movimientosDelHistorial: MovimientoDashboard[] = [];
  mesSeleccionado = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
  cargandoMovimientos = true;
  errorMovimientos = '';
  loading = true;
  error = '';
  nombreUsuario = readAuthSessionValue('username') || 'Bienvenido';

  constructor(private dashboardService: DashboardService, private themeService: ThemeService) {}

  isDark(): boolean {
    return this.themeService.isDark();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  ngOnInit(): void {
    timer(0, 60_000).pipe(
      exhaustMap(() => this.dashboardService.obtenerEstadoSincronizacion().pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(estado => {
      this.consultandoSincronizacion = false;
      this.errorSincronizacion = estado === null;
      if (estado) this.sincronizacion = estado;
    });
    this.loading = true;
    const usuarioId = Number(readAuthSessionValue('userId') || '1');

    this.dashboardService.obtenerResumen(usuarioId).subscribe({
      next: (data) => {
        this.resumen = data;
        this.loading = false;
        this.error = '';
      },
      error: (err) => {
        console.error('Error obteniendo resumen', err);
        this.loading = false;
        this.error = 'No se pudo cargar el resumen financiero.';
      }
    });

    this.dashboardService.obtenerMovimientosRecientes(usuarioId).subscribe({
      next: (data) => {
        this.movimientosDelHistorial = data;
        this.cargandoMovimientos = false;
        const ordenados = [...data].sort((a, b) => {
          const fechaA = new Date(a.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          const fechaB = new Date(b.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          return fechaB - fechaA;
        });
        this.movimientos = ordenados.slice(0, 5);
      },
      error: (err) => {
        console.error('Error obteniendo movimientos', err);
        this.cargandoMovimientos = false;
        this.errorMovimientos = 'No se pudieron cargar los ingresos y gastos del período. Recarga para intentarlo otra vez.';
      }
    });
  }

  getBarHeight(value: number): number {
    const values = this.resumen
      ? [
          this.resumen.efectivo,
          this.resumen.debito,
          this.resumen.credito,
          this.resumen.deudaTarjetas,
          this.getTotalSaldo()
        ]
      : [1000];
    const max = Math.max(...values.map((item) => Math.abs(item)), 1);
    return Math.max(8, Math.min(100, (Math.abs(value) / max) * 100));
  }

  getTotalSaldo(): number {
    if (!this.resumen) {
      return 0;
    }

    return this.resumen.efectivo + this.resumen.debito;
  }

  getIngresoMensual(): number {
    if (!this.resumen) {
      return 0;
    }

    return this.movimientosDelHistorial
      .filter((movimiento) => movimiento.tipo === 'INGRESO' && this.esMesSeleccionado(movimiento.fechaMovimiento))
      .reduce((total, movimiento) => total + Number(movimiento.monto), 0);
  }

  getGastoMensual(): number {
    if (!this.resumen) {
      return 0;
    }

    return this.movimientosDelHistorial
      .filter((movimiento) => movimiento.tipo === 'GASTO' && this.esMesSeleccionado(movimiento.fechaMovimiento))
      .reduce((total, movimiento) => total + Number(movimiento.monto), 0);
  }

  getRate(value: number, total: number): number {
    if (total <= 0) {
      return 0;
    }

    return Math.round((Math.abs(value) / Math.abs(total)) * 100);
  }

  getPatrimonioTotal(): number {
    if (!this.resumen) {
      return 0;
    }

    return this.resumen.patrimonioDisponible ?? (this.resumen.efectivo + this.resumen.debito - this.resumen.deudaTarjetas);
  }

  private esMesSeleccionado(fecha?: string): boolean {
    if (!fecha) return false;
    return fecha.slice(0, 7) === this.mesSeleccionado;
  }

  cambiarMes(valor: string): void {
    if (/^\d{4}-(0[1-9]|1[0-2])$/.test(valor)) this.mesSeleccionado = valor;
  }

  get periodoLabel(): string {
    return new Date(`${this.mesSeleccionado}-01T12:00:00`).toLocaleDateString('es-SV', { month: 'long', year: 'numeric' });
  }

  get fechaHoy(): string {
    return new Intl.DateTimeFormat('es-SV', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
  }
}
