import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { CuentasComponent } from './pages/cuentas/cuentas.component';
import { MovimientosComponent } from './pages/movimientos/movimientos.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  {
    path: '',
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'cuentas', component: CuentasComponent },
      { path: 'movimientos', component: MovimientosComponent }
    ],
    canActivate: [
      () => {
        const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
        return isLoggedIn ? true : ['/login'];
      }
    ]
  }
];
