import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

import { DashboardService } from '../../services/dashboard.service';
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

  themeToggle = false;

  resumen?: ResumenDashboard;
  movimientos: MovimientoDashboard[] = [];
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
        const ordenados = [...data].sort((a, b) => {
          const fechaA = new Date(a.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          const fechaB = new Date(b.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          return fechaB - fechaA;
        });
        this.movimientos = ordenados.slice(0, 5);
      },
      error: (err) => {
        console.error('Error obteniendo movimientos', err);
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

    if (this.resumen.ingresosMes !== undefined) return this.resumen.ingresosMes;
    return this.movimientos
      .filter((movimiento) => movimiento.tipo === 'INGRESO' && this.esMesActual(movimiento.fechaMovimiento))
      .reduce((total, movimiento) => total + Number(movimiento.monto), 0);
  }

  getGastoMensual(): number {
    if (!this.resumen) {
      return 0;
    }

    if (this.resumen.gastosMes !== undefined) return this.resumen.gastosMes;
    return this.movimientos
      .filter((movimiento) => movimiento.tipo === 'GASTO' && this.esMesActual(movimiento.fechaMovimiento))
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

  private esMesActual(fecha?: string): boolean {
    if (!fecha) return false;
    const date = new Date(fecha);
    const now = new Date();
    return date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
  }

  get fechaHoy(): string {
    return new Intl.DateTimeFormat('es-SV', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
  }
}
