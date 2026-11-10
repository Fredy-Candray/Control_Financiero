import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SeguimientoComponent } from './seguimiento.component';

describe('Organización de revisión, comparación y dinero prestado', () => {
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeguimientoComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('muestra avisos sin cargar préstamos ni comparaciones', () => {
    const fixture = TestBed.createComponent(SeguimientoComponent);
    fixture.componentRef.setInput('tab', 'pendientes');
    fixture.componentRef.setInput('embebido', true);
    fixture.detectChanges();
    http.expectOne(r => r.url.endsWith('/control-financiero/pendientes')).flush([]);
    http.expectOne(r => r.url.includes('/movimientos/usuario/')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Avisos por revisar');
    expect(fixture.nativeElement.textContent).toContain('no han generado movimientos automáticos ni cambiado tus saldos');
    expect(fixture.nativeElement.textContent).not.toContain('Registrar dinero prestado');
    http.expectNone(r => r.url.includes('/prestamos') || r.url.includes('/conciliaciones'));
  });

  it('compara cuentas sin pedir avisos, préstamos ni movimientos', () => {
    const fixture = TestBed.createComponent(SeguimientoComponent);
    fixture.componentRef.setInput('tab', 'conciliacion');
    fixture.componentRef.setInput('embebido', true);
    fixture.detectChanges();
    http.expectOne(r => r.url.endsWith('/control-financiero/conciliaciones')).flush([]);
    http.expectOne(r => r.url.includes('/cuentas/usuario/')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Comparar con el banco');
    expect(fixture.nativeElement.textContent).not.toContain('Avisos por revisar');
    http.expectNone(r => r.url.includes('/pendientes') || r.url.includes('/prestamos') || r.url.includes('/movimientos/'));
  });

  it('abre dinero prestado como apartado independiente por defecto', () => {
    const fixture = TestBed.createComponent(SeguimientoComponent);
    fixture.detectChanges();
    http.expectOne(r => r.url.endsWith('/control-financiero/prestamos')).flush([]);
    http.expectOne(r => r.url.includes('/cuentas/usuario/')).flush([]);
    http.expectOne(r => r.url.includes('/movimientos/usuario/')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toBe('Dinero prestado');
    expect(fixture.nativeElement.textContent).toContain('Personas y devoluciones');
    expect(fixture.nativeElement.textContent).not.toContain('Avisos por revisar');
    http.expectNone(r => r.url.includes('/pendientes') || r.url.includes('/conciliaciones'));
  });
});
