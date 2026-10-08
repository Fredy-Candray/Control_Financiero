import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api.config';

export type AuthSessionKey = 'isLoggedIn' | 'username' | 'userId' | 'userRole';

export function readAuthSessionValue(key: AuthSessionKey): string | null {
  return sessionStorage.getItem(key) ?? localStorage.getItem(key);
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiUrl = `${API_BASE_URL}/auth`;


  constructor(private http: HttpClient) {}

  login(username: string, password: string, rememberMe: boolean): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { username, password, rememberMe });
  }

  isAuthenticated(): boolean {
    return this.getValue('isLoggedIn') === 'true';
  }

  getValue(key: AuthSessionKey): string | null {
    return readAuthSessionValue(key);
  }

  saveSession(response: { username?: string; userId?: number; rol?: string }, fallbackUsername: string, rememberMe: boolean): void {
    this.clearSession();
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('isLoggedIn', 'true');
    storage.setItem('username', response.username || fallbackUsername);
    storage.setItem('userId', response.userId?.toString() || '1');
    storage.setItem('userRole', response.rol || 'Usuario');
  }

  clearSession(): void {
    for (const key of ['isLoggedIn', 'username', 'userId', 'userRole']) {
      localStorage.removeItem(key);
      sessionStorage.removeItem(key);
    }
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
