import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { finalize, shareReplay, tap } from 'rxjs/operators';
import { CalendarCategory } from '../models/calendar-category';
import { readAuthSessionValue } from './auth.service';
import { API_BASE_URL } from '../api.config';

@Injectable({ providedIn: 'root' })
export class CalendarCategoriesService {
  private inFlight: { user: string; request: Observable<CalendarCategory[]> } | null = null;
  constructor(private http: HttpClient) {}
  private key(): string {
    return `controlFinanciero.calendar.categories.${readAuthSessionValue('userId') || '1'}`;
  }
  /** Read-only cache for reminders, never the authoritative category store. */
  list(): CalendarCategory[] {
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(this.key()) || '[]');
      return Array.isArray(parsed) ? parsed.filter((item): item is CalendarCategory =>
        !!item && typeof item === 'object' && typeof item.id === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(item.id) &&
        typeof item.name === 'string' && !!item.name.trim() && item.name.length <= 80 &&
        typeof item.color === 'string' && /^#[0-9a-f]{6}$/i.test(item.color)) : [];
    } catch { return []; }
  }
  synchronize(): Observable<CalendarCategory[]> {
    const user = readAuthSessionValue('userId');
    if (!user) return throwError(() => new Error('Inicia sesión nuevamente.'));
    if (this.inFlight?.user === user) return this.inFlight.request;
    const key = this.key(), marker = `${key}.database.v1`, legacy = this.list();
    const migrated = localStorage.getItem(marker) === 'true';
    const request = (migrated
      ? this.http.get<CalendarCategory[]>(`${API_BASE_URL}/calendario/categorias`)
      : this.http.post<CalendarCategory[]>(`${API_BASE_URL}/calendario/categorias/migracion`, { categories: legacy }))
      .pipe(tap(categories => {
        if (readAuthSessionValue('userId') !== user) return;
        if (!migrated && localStorage.getItem(`${key}.legacy-backup.v1`) === null)
          localStorage.setItem(`${key}.legacy-backup.v1`, localStorage.getItem(key) || JSON.stringify(legacy));
        localStorage.setItem(key, JSON.stringify(categories));
        localStorage.setItem(marker, 'true');
      }), finalize(() => { if (this.inFlight?.user === user) this.inFlight = null; }),
      shareReplay({ bufferSize: 1, refCount: false }));
    this.inFlight = { user, request };
    return request;
  }
  saveRemote(category: CalendarCategory): Observable<CalendarCategory> {
    const key = this.key(), user = readAuthSessionValue('userId');
    return this.http.put<CalendarCategory>(`${API_BASE_URL}/calendario/categorias/${encodeURIComponent(category.id)}`, category)
      .pipe(tap(saved => {
        if (readAuthSessionValue('userId') !== user) return;
        localStorage.setItem(key, JSON.stringify([...this.list().filter(item => item.id !== saved.id), saved]));
      }));
  }
  removeRemote(id: string, replacement: string): Observable<CalendarCategory[]> {
    const key = this.key(), user = readAuthSessionValue('userId');
    return this.http.delete<CalendarCategory[]>(`${API_BASE_URL}/calendario/categorias/${encodeURIComponent(id)}`,
      { params: { replacement } }).pipe(tap(categories => {
        if (readAuthSessionValue('userId') === user) localStorage.setItem(key, JSON.stringify(categories));
      }));
  }
}
