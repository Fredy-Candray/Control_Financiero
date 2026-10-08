import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../api.config';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  nombre = '';
  correo = '';
  telefono = '';
  username = '';
  password = '';
  mostrarPassword = false;
  error = '';
  success = '';
  submitted = false;
  private errorTimer?: number;

  constructor(private http: HttpClient, private router: Router) {}

  onSubmit(): void {
    this.clearError();
    this.submitted = true;
    if (!this.formularioValido()) return;
    this.http.post(`${API_BASE_URL}/auth/registro`, {
      nombre: this.nombre,
      correo: this.correo,
      telefono: this.telefono,
      username: this.username,
      password: this.password
    }).subscribe({
      next: () => {
        this.success = 'Usuario creado correctamente';
        setTimeout(() => this.router.navigate(['/login']), 1000);
      },
      error: () => {
        this.showError('No se pudo crear el usuario');
      }
    });
  }

  formatearTelefono(): void {
    const digits = this.telefono.replace(/\D/g, '').slice(0, 8);
    this.telefono = digits.length > 4 ? `${digits.slice(0, 4)}-${digits.slice(4)}` : digits;
  }

  correoValido(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.correo.trim());
  }

  telefonoValido(): boolean {
    return /^\d{4}-\d{4}$/.test(this.telefono);
  }

  nombreValido(): boolean {
    return /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+(?:[\s'-]+[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)+$/.test(this.nombre.trim());
  }

  formularioValido(): boolean {
    return this.nombreValido()
      && this.correoValido()
      && this.telefonoValido()
      && !!this.username.trim()
      && this.password.length >= 8;
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
