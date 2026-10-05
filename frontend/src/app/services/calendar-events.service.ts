import { Injectable } from '@angular/core';
import { CalendarEvent } from '../models/calendar-event';
import { HttpClient } from '@angular/common/http';
import { Observable, from, of } from 'rxjs';
import { concatMap, switchMap, tap, toArray } from 'rxjs/operators';
import { API_BASE_URL } from '../api.config';

@Injectable({ providedIn: 'root' })
export class CalendarEventsService {
  constructor(private readonly http: HttpClient) {}

  private key(): string {
    const userId = localStorage.getItem('userId') || '1';
    return `controlFinanciero.calendar.events.${userId}`;
  }

  list(): CalendarEvent[] {
    try {
      const stored = localStorage.getItem(this.key());
      if (!stored) return [];
      const parsed: unknown = JSON.parse(stored);
      // Keep stale or malformed browser data from crashing the calendar route.
      return Array.isArray(parsed)
        ? parsed.filter((event): event is CalendarEvent => !!event && typeof event === 'object' && !Array.isArray(event))
        : [];
    } catch {
      return [];
    }
  }

  save(event: CalendarEvent): void {
    const events = this.list();
    const index = events.findIndex(item => item.id === event.id);
    if (index === -1) events.push(event);
    else events[index] = event;
    localStorage.setItem(this.key(), JSON.stringify(events));
  }

  synchronize(): Observable<CalendarEvent[]> {
    return this.http.get<CalendarEvent[]>(`${API_BASE_URL}/calendario/eventos`).pipe(
      switchMap(serverEvents => {
        const localEvents = this.list();
        if (serverEvents.length || !localEvents.length) {
          this.replace(serverEvents);
          return of(serverEvents);
        }
        return from(localEvents).pipe(
          concatMap(event => this.saveRemote(event)),
          toArray(),
          tap(events => this.replace(events))
        );
      })
    );
  }

  saveRemote(event: CalendarEvent): Observable<CalendarEvent> {
    return this.http.put<CalendarEvent>(`${API_BASE_URL}/calendario/eventos/${encodeURIComponent(event.id)}`, event)
      .pipe(tap(saved => this.save(saved)));
  }

  removeRemote(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/calendario/eventos/${encodeURIComponent(id)}`)
      .pipe(tap(() => this.remove(id)));
  }

  private replace(events: CalendarEvent[]): void {
    localStorage.setItem(this.key(), JSON.stringify(events));
  }

  remove(id: string): void {
    localStorage.setItem(this.key(), JSON.stringify(this.list().filter(event => event.id !== id)));
  }
}
