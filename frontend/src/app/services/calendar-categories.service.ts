import { Injectable } from '@angular/core';
import { CalendarCategory } from '../models/calendar-category';
import { readAuthSessionValue } from './auth.service';

@Injectable({ providedIn: 'root' })
export class CalendarCategoriesService {
  private readonly defaults: CalendarCategory[] = [
    { id: 'meeting', name: 'Reunión', color: '#16a06a' },
    { id: 'payment', name: 'Pago de tarjeta', color: '#ef3340' },
    { id: 'task', name: 'Tarea', color: '#f0c419' },
    { id: 'event', name: 'Evento', color: '#f28c28' }
  ];

  private key(): string {
    return `controlFinanciero.calendar.categories.${readAuthSessionValue('userId') || '1'}`;
  }

  list(): CalendarCategory[] {
    try {
      const stored = localStorage.getItem(this.key());
      if (stored) {
        const parsed: unknown = JSON.parse(stored);
        if (Array.isArray(parsed)) {
          const categories = parsed.filter((category): category is CalendarCategory =>
            !!category && typeof category === 'object' &&
            typeof category.id === 'string' && typeof category.name === 'string' && typeof category.color === 'string'
          );
          const migrationKey = `${this.key()}.v2`;
          if (localStorage.getItem(migrationKey)) return categories;
          const legacyIds = new Set(['internal', 'external', 'critical']);
          const custom = categories.filter(category => !legacyIds.has(category.id) && !this.defaults.some(item => item.id === category.id));
          const migrated = [...this.defaults.map(category => ({ ...category })), ...custom];
          this.write(migrated);
          localStorage.setItem(migrationKey, 'true');
          return migrated;
        }
      }
    } catch { /* Restore defaults if stored categories are malformed. */ }
    const initial = this.defaults.map(category => ({ ...category }));
    this.write(initial);
    localStorage.setItem(`${this.key()}.v2`, 'true');
    return initial;
  }

  save(category: CalendarCategory): void {
    const categories = this.list();
    const index = categories.findIndex(item => item.id === category.id);
    if (index < 0) categories.push(category);
    else categories[index] = category;
    this.write(categories);
  }

  remove(id: string): void {
    this.write(this.list().filter(category => category.id !== id));
  }

  private write(categories: CalendarCategory[]): void {
    localStorage.setItem(this.key(), JSON.stringify(categories));
  }
}
