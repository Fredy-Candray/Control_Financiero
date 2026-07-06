import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Cuenta } from '../../models/cuenta';

@Component({
  selector: 'app-cuentas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cuentas.component.html',
  styleUrl: './cuentas.component.css'
})
export class CuentasComponent implements OnInit {
  cuentas: Cuenta[] = [];
  cargando = true;
  error = '';
  success = '';

  nombre = '';
  tipo = 'EFECTIVO';
  saldoActual = 0;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargarCuentas();
  }

  cargarCuentas(): void {
    this.cargando = true;
    this.http.get<Cuenta[]>('http://localhost:8081/api/cuentas').subscribe({
      next: (data) => {
        this.cuentas = data;
        this.cargando = false;
        this.error = '';
      },
      error: () => {
        this.error = 'No se pudieron cargar las cuentas.';
        this.cargando = false;
      }
    });
  }

  onSubmit(): void {
    const usuarioId = Number(localStorage.getItem('userId') || '1');

    this.http.post('http://localhost:8081/api/cuentas', {
      nombre: this.nombre,
      tipo: this.tipo,
      saldoActual: this.saldoActual,
      usuarioId
    }).subscribe({
      next: () => {
        this.success = 'Cuenta creada correctamente.';
        this.nombre = '';
        this.tipo = 'EFECTIVO';
        this.saldoActual = 0;
        this.cargarCuentas();
      },
      error: () => {
        this.error = 'No se pudo crear la cuenta.';
      }
    });
  }
}
