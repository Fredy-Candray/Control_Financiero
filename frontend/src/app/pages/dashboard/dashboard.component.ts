import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

import { DashboardService } from '../../services/dashboard.service';
import { ThemeService } from '../../services/theme.service';
import { ResumenDashboard } from '../../models/resumen-dashboard';
import { MovimientoDashboard } from '../../models/movimiento-dashboard';

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

  constructor(private dashboardService: DashboardService, private themeService: ThemeService) {}

  isDark(): boolean {
    return this.themeService.isDark();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  ngOnInit(): void {
    this.dashboardService.obtenerResumen(1).subscribe({
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

    this.dashboardService.obtenerMovimientosRecientes().subscribe({
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

    return this.resumen.efectivo + this.resumen.debito + this.resumen.credito;
  }

  getIngresoMensual(): number {
    if (!this.resumen) {
      return 0;
    }

    return this.resumen.efectivo + this.resumen.debito;
  }

  getGastoMensual(): number {
    if (!this.resumen) {
      return 0;
    }

    return this.resumen.credito + this.resumen.deudaTarjetas;
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

    return this.resumen.efectivo + this.resumen.debito;
  }
}
