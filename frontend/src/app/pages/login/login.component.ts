import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';

  constructor(
    private router: Router,
    private authService: AuthService
  ) {}

  onSubmit(): void {
    this.authService.login(this.username, this.password).subscribe({
      next: (response) => {
        if (response?.success) {
          localStorage.setItem('isLoggedIn', 'true');
          localStorage.setItem('username', response.username || this.username);
          localStorage.setItem('userId', response.userId?.toString() || '1');
          this.router.navigate(['/dashboard']);
        } else {
          this.error = 'Usuario o contraseña incorrectos';
        }
      },
      error: () => {
        this.error = 'No se pudo iniciar sesión. Verifica tus credenciales.';
      }
    });
  }
}
