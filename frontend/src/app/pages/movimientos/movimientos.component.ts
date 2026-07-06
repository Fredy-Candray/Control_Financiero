import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Cuenta } from '../../models/cuenta';

@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './movimientos.component.html',
  styleUrl: './movimientos.component.css'
})
export class MovimientosComponent implements OnInit {
  movimientos: any[] = [];
  cuentas: Cuenta[] = [];
  cargando = true;
  error = '';
  success = '';

  tipo = 'GASTO';
  monto = 0;
  descripcion = '';
  cuentaOrigenId: number | null = null;
  cuentaDestinoId: number | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargarMovimientos();
    this.cargarCuentas();
  }

  cargarMovimientos(): void {
    this.cargando = true;
    this.http.get<any[]>('http://localhost:8081/api/movimientos').subscribe({
      next: (data) => {
        this.movimientos = [...data].sort((a, b) => {
          const fechaA = new Date(a.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          const fechaB = new Date(b.fechaMovimiento ?? '1970-01-01T00:00:00Z').getTime();
          return fechaB - fechaA;
        });
        this.cargando = false;
        this.error = '';
      },
      error: () => {
        this.error = 'No se pudieron cargar los movimientos.';
        this.cargando = false;
      }
    });
  }

  cargarCuentas(): void {
    this.http.get<Cuenta[]>('http://localhost:8081/api/cuentas').subscribe({
      next: (data) => {
        this.cuentas = data;
      }
    });
  }

  onSubmit(): void {
    const usuarioId = Number(localStorage.getItem('userId') || '1');

    this.http.post('http://localhost:8081/api/movimientos', {
      tipo: this.tipo,
      monto: this.monto,
      descripcion: this.descripcion,
      cuentaOrigenId: this.cuentaOrigenId,
      cuentaDestinoId: this.cuentaDestinoId,
      usuarioId
    }).subscribe({
      next: () => {
        this.success = 'Movimiento registrado correctamente.';
        this.tipo = 'GASTO';
        this.monto = 0;
        this.descripcion = '';
        this.cuentaOrigenId = null;
        this.cuentaDestinoId = null;
        this.cargarMovimientos();
        this.cargarCuentas();
      },
      error: () => {
        this.error = 'No se pudo registrar el movimiento.';
      }
    });
  }
}
