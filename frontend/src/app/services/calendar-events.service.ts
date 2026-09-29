import { Injectable } from '@angular/core';
import { CalendarEvent } from '../models/calendar-event';

@Injectable({ providedIn: 'root' })
export class CalendarEventsService {
  private key(): string {
    const userId = localStorage.getItem('userId') || '1';
    return `controlFinanciero.calendar.events.${userId}`;
  }

  list(): CalendarEvent[] {
    try {
      const stored = localStorage.getItem(this.key());
      return stored ? JSON.parse(stored) as CalendarEvent[] : [];
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

  remove(id: string): void {
    localStorage.setItem(this.key(), JSON.stringify(this.list().filter(event => event.id !== id)));
  }
}
