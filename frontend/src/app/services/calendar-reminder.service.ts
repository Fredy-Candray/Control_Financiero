import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { CalendarEvent } from '../models/calendar-event';
import { CalendarEventsService } from './calendar-events.service';
import { readAuthSessionValue } from './auth.service';

@Injectable({ providedIn: 'root' })
export class CalendarReminderService {
  private timer: ReturnType<typeof setInterval> | null = null;
  private readonly dueSubject = new Subject<{ title: string; date: Date; payment: boolean }>();
  readonly due$ = this.dueSubject.asObservable();
  private readonly onResume = () => this.checkRemindersSafely();

  constructor(private events: CalendarEventsService, private router: Router) {}

  start(): void {
    if (this.timer) return;
    // Reminder storage may contain legacy or partially written events. It must
    // never prevent the Angular application itself from rendering on refresh.
    this.checkRemindersSafely();
    this.timer = setInterval(() => this.checkRemindersSafely(), 30_000);
    window.addEventListener('focus', this.onResume);
    document.addEventListener('visibilitychange', this.onResume);
  }

  private checkRemindersSafely(): void {
    try {
      this.checkReminders();
    } catch (error) {
      console.warn('No se pudieron comprobar los recordatorios guardados.', error);
    }
  }

  async requestPermission(): Promise<NotificationPermission | 'unsupported'> {
    if (!('Notification' in window)) return 'unsupported';
    return Notification.requestPermission();
  }

  permission(): NotificationPermission | 'unsupported' {
    return 'Notification' in window ? Notification.permission : 'unsupported';
  }

  private checkReminders(): void {
    if (readAuthSessionValue('isLoggedIn') !== 'true') return;
    const now = new Date();
    const userId = readAuthSessionValue('userId') || '1';
    // Versioned so reminders that an older build marked as delivered despite
    // Windows hiding its notification can be surfaced by the in-app alert.
    const seenKey = `controlFinanciero.calendar.notified.v2.${userId}`;
    let notified: string[] = [];
    try { notified = JSON.parse(localStorage.getItem(seenKey) || '[]') as string[]; } catch { notified = []; }

    for (const event of this.events.list()) {
      if (!event || typeof event !== 'object' || !event.id || !event.date || !event.time || !Number.isFinite(event.reminderMinutes)) continue;
      if (event.completed) continue;
      if (event.notifyBrowser === false) continue;
      const occurrence = this.nextOccurrence(event, now);
      if (!occurrence) continue;
      const remindAt = occurrence.getTime() - event.reminderMinutes * 60_000;
      if (now.getTime() < remindAt || now.getTime() > occurrence.getTime() + 10 * 60_000) continue;
      const occurrenceKey = `${event.id}:${occurrence.getTime()}`;
      if (notified.includes(occurrenceKey)) continue;
      // Always show an in-app alert. The OS notification is an additional
      // channel and may be hidden by Windows focus/notification settings.
      this.dueSubject.next({ title: event.title, date: occurrence, payment: event.type === 'PAGO_TARJETA' });
      if (this.permission() === 'granted') this.show(event, occurrence);
      notified.push(occurrenceKey);
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

  private show(event: CalendarEvent, occurrence: Date): boolean {
    try {
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
      return true;
    } catch (error) {
      console.warn('No se pudo mostrar un recordatorio del calendario.', error);
      return false;
    }
  }
}
