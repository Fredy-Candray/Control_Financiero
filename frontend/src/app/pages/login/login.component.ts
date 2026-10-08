import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  username = '';
  password = '';
  correoRecuperacion = '';
  confirmarPassword = '';
  recoveryMode: 'login' | 'forgot' | 'reset' = 'login';
  recoveryToken = '';
  recoveryMessage = '';
  recoveryError = '';
  recoveryBusy = false;
  mostrarNuevaPassword = false;
  mostrarConfirmarPassword = false;
  mostrarPassword = false;
  rememberMe = false;
  error = '';
  private errorTimer?: number;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private themeService: ThemeService
  ) {
    this.themeService.initTheme();
    this.route.queryParamMap.subscribe(params => {
      const token = params.get('token');
      this.recoveryToken = token || '';
      this.recoveryMode = this.router.url.startsWith('/reset-password') ? 'reset' : 'login';
      this.clearRecoveryNotice();
      if (this.recoveryMode === 'reset' && !this.recoveryToken) {
        this.recoveryError = 'El enlace de recuperación no es válido. Solicita uno nuevo.';
      }
    });
    if (sessionStorage.getItem('controlFinanciero.sessionExpired') === 'true') {
      sessionStorage.removeItem('controlFinanciero.sessionExpired');
      this.showError('Tu sesión expiró. Inicia sesión nuevamente para continuar.');
    }
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  isDarkTheme(): boolean {
    return this.themeService.isDark();
  }

  abrirRecuperacion(): void {
    this.recoveryMode = 'forgot';
    this.clearRecoveryNotice();
  }

  volverAlLogin(): void {
    if (this.router.url.startsWith('/reset-password')) {
      this.authService.clearSession();
    }
    this.recoveryMode = 'login';
    this.clearRecoveryNotice();
    void this.router.navigate(['/login'], { replaceUrl: true });
  }

  solicitarRecuperacion(): void {
    this.clearRecoveryNotice();
    if (!this.correoRecuperacion.trim()) {
      this.recoveryError = 'Escribe el correo asociado a tu cuenta.';
      return;
    }
    this.recoveryBusy = true;
    this.authService.solicitarRecuperacion(this.correoRecuperacion.trim()).subscribe({
      next: response => {
        this.recoveryMessage = response.message;
        this.recoveryBusy = false;
      },
      error: () => {
        this.recoveryError = 'No se pudo procesar la solicitud. Inténtalo nuevamente.';
        this.recoveryBusy = false;
      }
    });
  }

  restablecerPassword(): void {
    this.clearRecoveryNotice();
    if (this.password.length < 8) {
      this.recoveryError = 'La contraseña debe tener al menos 8 caracteres.';
      return;
    }
    if (this.password !== this.confirmarPassword) {
      this.recoveryError = 'Las contraseñas no coinciden.';
      return;
    }
    this.recoveryBusy = true;
    this.authService.restablecerPassword(this.recoveryToken, this.password).subscribe({
      next: response => {
        this.recoveryMessage = response.message;
        this.recoveryBusy = false;
      },
      error: err => {
        this.recoveryError = err?.error?.detail || err?.error?.message || 'El enlace ya venció o no es válido. Solicita uno nuevo.';
        this.recoveryBusy = false;
      }
    });
  }

  private clearRecoveryNotice(): void {
    this.recoveryMessage = '';
    this.recoveryError = '';
  }

  onSubmit(): void {
    this.clearError();
    this.authService.login(this.username, this.password, this.rememberMe).subscribe({
      next: (response) => {
        if (response?.success) {
          this.authService.saveSession(response, this.username, this.rememberMe);
          this.router.navigate(['/dashboard']);
        } else {
          this.showError('Usuario o contraseña incorrecta');
        }
      },
      error: (error) => {
        this.showError(error?.status === 0
          ? 'No se pudo conectar con el servidor. Inténtalo de nuevo.'
          : 'Usuario o contraseña incorrecta');
      }
    });
  }

  clearError(): void {
    this.error = '';
    if (this.errorTimer !== undefined) window.clearTimeout(this.errorTimer);
    this.errorTimer = undefined;
  }

  private showError(message: string): void {
    this.error = message;
    if (this.errorTimer !== undefined) window.clearTimeout(this.errorTimer);
    this.errorTimer = window.setTimeout(() => this.clearError(), 6000);
  }
}
