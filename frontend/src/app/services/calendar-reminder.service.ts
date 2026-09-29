import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { CalendarEvent } from '../models/calendar-event';
import { CalendarEventsService } from './calendar-events.service';

@Injectable({ providedIn: 'root' })
export class CalendarReminderService {
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(private events: CalendarEventsService, private router: Router) {}

  start(): void {
    if (this.timer) return;
    this.checkReminders();
    this.timer = setInterval(() => this.checkReminders(), 30_000);
  }

  async requestPermission(): Promise<NotificationPermission | 'unsupported'> {
    if (!('Notification' in window)) return 'unsupported';
    return Notification.requestPermission();
  }

  permission(): NotificationPermission | 'unsupported' {
    return 'Notification' in window ? Notification.permission : 'unsupported';
  }

  private checkReminders(): void {
    if (localStorage.getItem('isLoggedIn') !== 'true') return;
    const now = new Date();
    const userId = localStorage.getItem('userId') || '1';
    const seenKey = `controlFinanciero.calendar.notified.${userId}`;
    let notified: string[] = [];
    try { notified = JSON.parse(localStorage.getItem(seenKey) || '[]') as string[]; } catch { notified = []; }

    for (const event of this.events.list()) {
      if (event.completed) continue;
      const occurrence = this.nextOccurrence(event, now);
      if (!occurrence) continue;
      const remindAt = occurrence.getTime() - event.reminderMinutes * 60_000;
      if (now.getTime() < remindAt || now.getTime() > occurrence.getTime() + 10 * 60_000) continue;
      const occurrenceKey = `${event.id}:${occurrence.getTime()}`;
      if (notified.includes(occurrenceKey)) continue;
      notified.push(occurrenceKey);
      this.show(event, occurrence);
    }

    if (notified.length > 600) notified = notified.slice(-600);
    localStorage.setItem(seenKey, JSON.stringify(notified));
  }

  private nextOccurrence(event: CalendarEvent, now: Date): Date | null {
    const [year, month, day] = event.date.split('-').map(Number);
    const [hour, minute] = event.time.split(':').map(Number);
    const base = new Date(year, month - 1, day, hour, minute, 0, 0);
    if (Number.isNaN(base.getTime())) return null;
    if (event.recurrence === 'NONE') return base.getTime() >= now.getTime() - 10 * 60_000 ? base : null;

    let occurrence = new Date(base);
    let guard = 0;
    while (occurrence.getTime() < now.getTime() - 10 * 60_000 && guard++ < 3000) {
      occurrence = this.advance(occurrence, event.recurrence, day);
    }
    return guard >= 3000 ? null : occurrence;
  }

  private advance(date: Date, recurrence: CalendarEvent['recurrence'], anchorDay: number): Date {
    const next = new Date(date);
    if (recurrence === 'DAILY') next.setDate(next.getDate() + 1);
    if (recurrence === 'WEEKLY') next.setDate(next.getDate() + 7);
    if (recurrence === 'MONTHLY') {
      next.setDate(1);
      next.setMonth(next.getMonth() + 1);
      next.setDate(Math.min(anchorDay, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()));
    }
    if (recurrence === 'YEARLY') {
      const month = next.getMonth();
      next.setDate(1);
      next.setFullYear(next.getFullYear() + 1);
      next.setMonth(month);
      next.setDate(Math.min(anchorDay, new Date(next.getFullYear(), month + 1, 0).getDate()));
    }
    return next;
  }

  private show(event: CalendarEvent, occurrence: Date): void {
    if (this.permission() !== 'granted') return;
    const notification = new Notification(event.type === 'PAGO_TARJETA' ? 'Recordatorio de pago' : 'Actividad programada', {
      body: `${event.title} · ${occurrence.toLocaleString('es-SV', { dateStyle: 'medium', timeStyle: 'short' })}`,
      icon: '/favicon.ico',
      tag: `${event.id}-${occurrence.getTime()}`
    });
    notification.onclick = () => {
      window.focus();
      void this.router.navigate(['/calendario']);
      notification.close();
    };
  }
}
