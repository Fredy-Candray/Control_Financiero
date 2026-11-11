import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

describe('Contraseñas de COFI', () => {
  let http: HttpTestingController;
  let router: { url: string; navigate: jasmine.Spy; navigateByUrl: jasmine.Spy };
  beforeEach(() => {
    localStorage.clear(); sessionStorage.clear();
    router = { url: '/login', navigate: jasmine.createSpy(), navigateByUrl: jasmine.createSpy() };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), AuthService,
      { provide: Router, useValue: router },
      { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      { provide: ThemeService, useValue: { initTheme: () => undefined } }
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); localStorage.clear(); sessionStorage.clear(); });
  const componente = () => new LoginComponent(TestBed.inject(Router), TestBed.inject(ActivatedRoute), TestBed.inject(AuthService), TestBed.inject(ThemeService));
  it('el login con clave temporal dirige al cambio obligatorio', () => {
    const c = componente(); c.username = 'prueba'; c.password = 'cofi'; c.onSubmit();
    http.expectOne(r => r.url.endsWith('/auth/login')).flush({ success: true, userId: 2, username: 'prueba', rol: 'Usuario', cambioPasswordPendiente: true });
    expect(router.navigate).toHaveBeenCalledWith(['/cambiar-password']);
    expect(TestBed.inject(AuthService).getValue('cambioPasswordPendiente')).toBe('true');
  });
  it('rechaza siete caracteres y exige confirmación antes de guardar la personal', () => {
    router.url = '/cambiar-password'; const c = componente();
    c.password = 'Clave12'; c.confirmarPassword = 'Clave12'; c.restablecerPassword();
    http.expectNone(r => r.method === 'POST');
    c.password = 'Clave123'; c.confirmarPassword = 'Otra1234'; c.restablecerPassword();
    http.expectNone(r => r.method === 'POST');
    c.confirmarPassword = 'Clave123'; c.restablecerPassword();
    const req = http.expectOne(r => r.url.endsWith('/auth/password/inicial'));
    expect(req.request.body).toEqual({ nuevaPassword: 'Clave123', confirmarPassword: 'Clave123' });
    req.flush({ message: 'Inicia sesión con tu nueva contraseña.' });
    expect(c.recoveryMode).toBe('login'); expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
  });
  it('conserva la recuperación por correo independiente del restablecimiento administrativo', () => {
    const c = componente(); c.abrirRecuperacion(); c.correoRecuperacion = 'prueba@example.test'; c.solicitarRecuperacion();
    const req = http.expectOne(r => r.url.endsWith('/auth/password/forgot'));
    expect(req.request.body).toEqual({ correo: 'prueba@example.test' });
    req.flush({ message: 'Si el correo está registrado, recibirás instrucciones.' });
    expect(c.recoveryMessage).toContain('instrucciones');
  });
});
