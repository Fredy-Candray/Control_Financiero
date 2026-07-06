import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResumenDashboard } from '../models/resumen-dashboard';
import { MovimientoDashboard } from '../models/movimiento-dashboard';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  private apiUrl = 'http://localhost:8081/api/dashboard';
  private movimientosUrl = 'http://localhost:8081/api/movimientos';

  constructor(private http: HttpClient) {}

  obtenerResumen(usuarioId: number): Observable<ResumenDashboard> {
    return this.http.get<ResumenDashboard>(
      `${this.apiUrl}/resumen/${usuarioId}`
    );
  }

  obtenerMovimientosRecientes(): Observable<MovimientoDashboard[]> {
    return this.http.get<MovimientoDashboard[]>(this.movimientosUrl);
  }
}