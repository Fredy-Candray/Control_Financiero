import { HttpClient } from '@angular/common/http';
import { ReportesComponent } from './reportes.component';

describe('Dinero disponible en reportes', () => {
  it('suma efectivo, PAY y débito sin incluir crédito ni restar deuda', () => {
    const component = new ReportesComponent({} as HttpClient);
    component.cuentas = [
      { id: 1, nombre: 'Efectivo', tipo: 'EFECTIVO', saldoActual: 4, activa: true },
      { id: 2, nombre: 'PAY', tipo: 'DEBITO', saldoActual: 1000, activa: true },
      { id: 3, nombre: 'Débito', tipo: 'DEBITO', saldoActual: 10, activa: true },
      { id: 4, nombre: 'Crédito', tipo: 'CREDITO', saldoActual: 600, limiteCredito: 1000, activa: true }
    ];
    expect(component.dineroDisponible).toBe(1014);
    expect(component.deudaTotal).toBe(400);
    expect(component.patrimonio).toBe(614);
    component.cuentas[3].saldoActual = 300;
    expect(component.dineroDisponible).toBe(1014);
    component.filtros.cuentaId = 2;
    expect(component.dineroDisponible).toBe(1000);
    component.filtros.cuentaId = 4;
    expect(component.dineroDisponible).toBe(0);
  });
  it('suma los saldos en centavos y soporta cuentas vacías', () => {
    const component = new ReportesComponent({} as HttpClient);
    expect(component.dineroDisponible).toBe(0);
    component.cuentas = [
      { id: 1, nombre: 'Efectivo', tipo: 'EFECTIVO', saldoActual: .1, activa: true },
      { id: 2, nombre: 'PAY', tipo: 'DEBITO', saldoActual: .2, activa: true }
    ];
    expect(component.dineroDisponible).toBe(.3);
  });
});
