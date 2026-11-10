import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AhorroLiquidezComponent } from './ahorro-liquidez.component';
import { ResumenAhorro } from '../../models/ahorro-liquidez';

describe('Ahorro y Liquidez conectado a la API',()=>{
  let http:HttpTestingController;
  const datos:ResumenAhorro={mes:'2026-11',moneda:'USD',calculadoEn:'2026-11-09T12:00:00-06:00',dineroEnCuentas:'1000.00',deudaTarjetas:'100.00',gastosFijosPendientes:'200.00',dineroLibre:'700.00',reservadoMetas:'300.00',sinAsignar:'400.00',reservasSinCoberturaGlobal:'0.00',reservasSinCoberturaPorCuenta:'0.00',cuentas:[{id:1,nombre:'Cuenta ficticia',tipo:'DEBITO',saldoActual:'1000.00',reservado:'300.00',activa:true}],metas:[{id:5,nombre:'Meta ficticia',objetivo:'500.00',reservado:'300.00',acumuladoLegado:null,fechaObjetivo:null,aporteMensual:null,version:2}],fijos:[],compromisos:[],presupuestos:[],alertas:[],categorias:[]};
  beforeEach(async()=>{await TestBed.configureTestingModule({imports:[AhorroLiquidezComponent],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting()]}).compileComponents();http=TestBed.inject(HttpTestingController);spyOnProperty(document,'hidden','get').and.returnValue(false);});
  afterEach(()=>http.verify());
  it('muestra el resumen calculado por el servidor, separado de las reservas',fakeAsync(()=>{
    const fixture=TestBed.createComponent(AhorroLiquidezComponent);fixture.detectChanges();tick(0);http.expectOne(r=>r.url.endsWith('/resumen')).flush(structuredClone(datos));fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Dinero Libre');expect(fixture.nativeElement.textContent).toContain('$700.00');expect(fixture.nativeElement.textContent).toContain('$400.00');fixture.destroy();
  }));
  it('reserva mediante el libro de metas sin llamar a APIs de movimiento o saldo',fakeAsync(()=>{
    const fixture=TestBed.createComponent(AhorroLiquidezComponent);fixture.detectChanges();tick(0);http.expectOne(r=>r.url.endsWith('/resumen')).flush(structuredClone(datos));
    const c=fixture.componentInstance;c.abrir('RESERVA',datos.metas[0]);c.editor!.cuentaId=1;c.editor!.monto=50;c.guardar();
    const req=http.expectOne(r=>r.url.endsWith('/metas/5/reservas'));expect(req.request.body.version).toBe(2);expect(req.request.body.monto).toBe(50);expect(req.request.body.referenciaCliente).toMatch(/^[a-f0-9-]{36}$/i);req.flush({id:1});http.expectOne(r=>r.url.endsWith('/resumen')).flush(structuredClone(datos));
    http.expectNone(r=>r.method!=='GET'&&(r.url.includes('/movimientos')||r.url.includes('/cuentas')));fixture.destroy();
  }));
  it('conserva el formulario y la referencia cuando el servidor rechaza una versión obsoleta',fakeAsync(()=>{
    const fixture=TestBed.createComponent(AhorroLiquidezComponent);fixture.detectChanges();tick(0);http.expectOne(r=>r.url.endsWith('/resumen')).flush(structuredClone(datos));const c=fixture.componentInstance;c.abrir('RESERVA',datos.metas[0]);c.editor!.cuentaId=1;c.editor!.monto=25;const ref=c.editor!.referenciaCliente;c.guardar();http.expectOne(r=>r.url.endsWith('/metas/5/reservas')).flush({message:'Actualiza el registro antes de reservar.'},{status:409,statusText:'Conflict'});
    expect(c.editor?.monto).toBe(25);expect(c.editor?.referenciaCliente).toBe(ref);expect(c.error).toContain('Actualiza');expect(c.guardando).toBeFalse();fixture.destroy();
  }));
});
