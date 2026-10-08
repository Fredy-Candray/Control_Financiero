import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router, Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { CuentasComponent } from './pages/cuentas/cuentas.component';
import { MovimientosComponent } from './pages/movimientos/movimientos.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { UsuariosComponent } from './pages/usuarios/usuarios.component';
import { CalendarioComponent } from './pages/calendario/calendario.component';
import { PerfilComponent } from './pages/perfil/perfil.component';
import { AdministrarMenuComponent } from './pages/administrar-menu/administrar-menu.component';
import { HttpClient } from '@angular/common/http';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthService } from './services/auth.service';

const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const isLoggedIn = inject(AuthService).isAuthenticated();

  return isLoggedIn ? true : router.createUrlTree(['/login']);
};

const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  const isLoggedIn = inject(AuthService).isAuthenticated();

  return isLoggedIn ? router.createUrlTree(['/dashboard']) : true;
};

interface MenuResponse {
  secciones: Array<{ titulo: string; opciones: Array<{ etiqueta: string; ruta: string; icono: string }> }>;
  rutasPermitidas: string[];
}

const menuPermissionGuard: CanActivateChildFn = (route) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);

  const requestedRoute = `/${route.routeConfig?.path || ''}`;
  return inject(HttpClient).get<MenuResponse>(`${API_BASE_URL}/auth/me/menu`).pipe(
    map((menu) => {
      return menu.rutasPermitidas?.includes(requestedRoute) ? true : router.createUrlTree(['/dashboard']);
    }),
    catchError((error) => {
      if (error?.status === 401 || error?.status === 403) {
        auth.clearSession();
        return of(router.createUrlTree(['/login']));
      }
      return of(router.createUrlTree(['/dashboard']));
    })
  );
};

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  // Recovery links must remain accessible even if this browser has a stale login marker.
  { path: 'reset-password', component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  {
    path: '',
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'cuentas', component: CuentasComponent },
      { path: 'movimientos', component: MovimientosComponent },
      { path: 'calendario', component: CalendarioComponent },
      { path: 'perfil', component: PerfilComponent },
      { path: 'usuarios', component: UsuariosComponent },
      { path: 'administrar-menu', component: AdministrarMenuComponent }
    ],
    canActivate: [authGuard],
    canActivateChild: [menuPermissionGuard]
  }
];
