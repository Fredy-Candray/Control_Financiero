import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResumenDashboard } from '../models/resumen-dashboard';
import { MovimientoDashboard } from '../models/movimiento-dashboard';
import { API_BASE_URL } from '../api.config';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  // The app may be served by Vite (port 4200), which does not read Angular's
  // proxy.conf.json. Call the local API directly so data loads in that setup.
  private readonly apiUrl = `${API_BASE_URL}/dashboard`;

  constructor(private http: HttpClient) {}

  obtenerResumen(usuarioId: number): Observable<ResumenDashboard> {
    return this.http.get<ResumenDashboard>(
      `${this.apiUrl}/resumen/${usuarioId}`
    );
  }

  obtenerMovimientosRecientes(usuarioId: number): Observable<MovimientoDashboard[]> {
    return this.http.get<MovimientoDashboard[]>(`${API_BASE_URL}/movimientos/usuario/${usuarioId}`);
  }
}
