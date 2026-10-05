import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api.config';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiUrl = `${API_BASE_URL}/auth`;


  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { username, password });
  }

  solicitarRecuperacion(correo: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/password/forgot`, { correo });
  }

  restablecerPassword(token: string, nuevaPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/password/reset`, { token, nuevaPassword });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/logout`, {});
  }
}
