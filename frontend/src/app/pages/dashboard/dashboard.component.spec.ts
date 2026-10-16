import { DashboardComponent } from './dashboard.component';
import { DashboardService } from '../../services/dashboard.service';
import { ThemeService } from '../../services/theme.service';
import { ResumenDashboard } from '../../models/resumen-dashboard';
import { TestBed } from '@angular/core/testing';

describe('Período de ingresos y gastos', () => {
  it('suma todo el historial del mes y año elegidos sin incluir transferencias', () => {
    const component = TestBed.runInInjectionContext(() => new DashboardComponent({} as DashboardService, {} as ThemeService));
    component.resumen = { ingresosMes: 999, gastosMes: 999 } as ResumenDashboard;
    component.movimientosDelHistorial = [
      ...Array.from({ length: 6 }, (_, id) => ({ id, tipo: 'INGRESO', monto: 10, fechaMovimiento: '2026-10-01T09:00:00' })),
      { id: 7, tipo: 'GASTO', monto: 25, fechaMovimiento: '2026-10-23T12:00:00' },
      { id: 8, tipo: 'TRANSFERENCIA', monto: 200, fechaMovimiento: '2026-10-23T12:00:00' },
      { id: 9, tipo: 'INGRESO', monto: 100, fechaMovimiento: '2025-10-01T09:00:00' },
      { id: 10, tipo: 'GASTO', monto: 50, fechaMovimiento: '2026-09-01T09:00:00' }
    ];
    component.cambiarMes('2026-10');
    expect(component.getIngresoMensual()).toBe(60);
    expect(component.getGastoMensual()).toBe(25);
    component.cambiarMes('2026-09');
    expect(component.getIngresoMensual()).toBe(0);
    expect(component.getGastoMensual()).toBe(50);
    component.cambiarMes('2025-10');
    expect(component.getIngresoMensual()).toBe(100);
    component.cambiarMes('');
    expect(component.mesSeleccionado).toBe('2025-10');
  });

  it('distingue errores, pausas y revisiones antiguas de una sincronización activa', () => {
    const component = TestBed.runInInjectionContext(() => new DashboardComponent({} as DashboardService, {} as ThemeService));
    component.consultandoSincronizacion = false;
    component.sincronizacion = { configured: true, connected: true, enabled: true, lastSync: new Date().toISOString(), lastError: null };
    expect(component.estadoSincronizacion).toBe('Sincronización activa');
    component.sincronizacion.lastSync = new Date(Date.now() - 240_000).toISOString();
    expect(component.estadoSincronizacion).toBe('Sin revisión reciente');
    component.sincronizacion.lastError = 'Error';
    expect(component.estadoSincronizacion).toBe('Error al revisar los correos');
    component.sincronizacion.lastError = null;
    component.sincronizacion.enabled = false;
    expect(component.estadoSincronizacion).toBe('Sincronización pausada');
    component.errorSincronizacion = true;
    expect(component.estadoSincronizacion).toBe('No se pudo consultar la sincronización');
  });
});
