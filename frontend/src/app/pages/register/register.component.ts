import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../api.config';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  nombre = '';
  correo = '';
  username = '';
  password = '';
  mostrarPassword = false;
  error = '';
  success = '';

  constructor(private http: HttpClient, private router: Router) {}

  onSubmit(): void {
    this.http.post(`${API_BASE_URL}/auth/registro`, {
      nombre: this.nombre,
      correo: this.correo,
      username: this.username,
      password: this.password
    }).subscribe({
      next: () => {
        this.success = 'Usuario creado correctamente';
        setTimeout(() => this.router.navigate(['/login']), 1000);
      },
      error: () => {
        this.error = 'No se pudo crear el usuario';
      }
    });
  }
}
