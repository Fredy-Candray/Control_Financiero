import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CalendarEvent, CalendarEventType, CalendarRecurrence } from '../../models/calendar-event';
import { CalendarEventsService } from '../../services/calendar-events.service';
import { CalendarReminderService } from '../../services/calendar-reminder.service';
import { Cuenta } from '../../models/cuenta';
import { API_BASE_URL } from '../../api.config';


@Component({
  selector: 'app-calendario',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './calendario.component.html',
  styleUrl: './calendario.component.css'
})
export class CalendarioComponent implements OnInit {
  readonly weekDays = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  readonly reminderOptions = [
    { value: 0, label: 'A la hora del evento' },
    { value: 10, label: '10 minutos antes' },
    { value: 30, label: '30 minutos antes' },
    { value: 60, label: '1 hora antes' },
    { value: 1440, label: '1 día antes' }
  ];
  readonly recurrenceOptions: { value: CalendarRecurrence; label: string }[] = [
    { value: 'NONE', label: 'No se repite' },
    { value: 'DAILY', label: 'Cada día' },
    { value: 'WEEKLY', label: 'Cada semana' },
    { value: 'MONTHLY', label: 'Cada mes' },
    { value: 'YEARLY', label: 'Cada año' }
  ];

  events: CalendarEvent[] = [];
  accounts: Cuenta[] = [];
  month = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  selectedDate = this.dateKey(new Date());
  modalOpen = false;
  editingId: string | null = null;
  notificationPermission: NotificationPermission | 'unsupported' = 'default';
  notificationMessage = '';
  formError = '';
  saving = false;
  form = this.emptyForm();

  constructor(
    private eventsStore: CalendarEventsService,
    private reminders: CalendarReminderService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.events = this.eventsStore.list();
    this.notificationPermission = this.reminders.permission();
    this.reminders.start();
    const userId = Number(localStorage.getItem('userId') || '1');
    this.http.get<Cuenta[]>(`${API_BASE_URL}/cuentas/usuario/${userId}`).subscribe({
      next: accounts => this.accounts = accounts.filter(account => account.activa),
      error: () => this.accounts = []
    });
  }

  get monthLabel(): string {
    return this.month.toLocaleDateString('es-SV', { month: 'long', year: 'numeric' });
  }

  get selectedDateLabel(): string {
    return new Date(`${this.selectedDate}T00:00:00`).toLocaleDateString('es-SV', { weekday: 'long', day: 'numeric', month: 'long' });
  }

  calendarDateLabel(date: Date): string { return date.toLocaleDateString('es-SV', { dateStyle: 'full' }); }
  upcomingDay(event: CalendarEvent): string { return this.eventDateTime(event).toLocaleDateString('es-SV', { day: '2-digit' }); }
  upcomingMonth(event: CalendarEvent): string { return this.eventDateTime(event).toLocaleDateString('es-SV', { month: 'short' }); }

  get calendarDays(): Date[] {
    const first = new Date(this.month.getFullYear(), this.month.getMonth(), 1);
    const offset = (first.getDay() + 6) % 7;
    const days = new Date(this.month.getFullYear(), this.month.getMonth() + 1, 0).getDate();
    const cells = Math.ceil((offset + days) / 7) * 7;
    return Array.from({ length: cells }, (_, index) => new Date(first.getFullYear(), first.getMonth(), index - offset + 1));
  }

  get selectedEvents(): CalendarEvent[] {
    return this.eventsForDate(this.selectedDate).sort((a, b) => a.time.localeCompare(b.time));
  }

  get upcomingEvents(): CalendarEvent[] {
    const now = new Date();
    return this.events.filter(event => !event.completed)
      .map(event => ({ event, occurrence: this.nextOccurrence(event, now) }))
      .filter(item => item.occurrence !== null)
      .sort((a, b) => a.occurrence!.getTime() - b.occurrence!.getTime())
      .slice(0, 5)
      .map(item => item.event);
  }

  previousMonth(): void { this.month = new Date(this.month.getFullYear(), this.month.getMonth() - 1, 1); }
  nextMonth(): void { this.month = new Date(this.month.getFullYear(), this.month.getMonth() + 1, 1); }
  goToToday(): void { const today = new Date(); this.month = new Date(today.getFullYear(), today.getMonth(), 1); this.selectedDate = this.dateKey(today); }
  selectDate(date: Date): void { this.selectedDate = this.dateKey(date); }

  hasEvent(date: Date): boolean { return this.eventsForDate(this.dateKey(date)).length > 0; }
  hasPayment(date: Date): boolean { return this.eventsForDate(this.dateKey(date)).some(event => event.type === 'PAGO_TARJETA'); }
  isToday(date: Date): boolean { return this.dateKey(date) === this.dateKey(new Date()); }
  isSelected(date: Date): boolean { return this.dateKey(date) === this.selectedDate; }
  isCurrentMonth(date: Date): boolean { return date.getMonth() === this.month.getMonth(); }

  openCreate(date = this.selectedDate): void {
    this.editingId = null;
    this.formError = '';
    this.form = this.emptyForm(date);
    this.modalOpen = true;
  }

  openEdit(event: CalendarEvent): void {
    this.editingId = event.id;
    this.formError = '';
    this.form = { ...event };
    this.modalOpen = true;
  }

  closeModal(): void { this.modalOpen = false; this.formError = ''; }

  saveEvent(): void {
    if (!this.form.title.trim() || !this.form.date || !this.form.time) {
      this.formError = 'Completa el título, la fecha y la hora.';
      return;
    }
    if (this.form.type === 'PAGO_TARJETA' && !this.form.accountId) {
      this.formError = 'Selecciona la tarjeta que debes pagar.';
      return;
    }
    if (this.form.type === 'PAGO_TARJETA' && this.form.amount !== null && this.form.amount < 0) {
      this.formError = 'El monto debe ser mayor o igual a cero.';
      return;
    }

    const current = this.editingId ? this.events.find(event => event.id === this.editingId) : undefined;
    const event: CalendarEvent = {
      ...this.form,
      id: this.editingId ?? this.createId(),
      title: this.form.title.trim(),
      description: this.form.description.trim(),
      amount: this.form.type === 'PAGO_TARJETA' && this.form.amount !== null ? Number(this.form.amount) : null,
      accountId: this.form.type === 'PAGO_TARJETA' ? Number(this.form.accountId) : null,
      completed: current?.completed ?? false,
      createdAt: current?.createdAt ?? new Date().toISOString()
    };
    this.eventsStore.save(event);
    this.events = this.eventsStore.list();
    this.selectedDate = event.date;
    this.month = new Date(Number(event.date.slice(0, 4)), Number(event.date.slice(5, 7)) - 1, 1);
    this.closeModal();
  }

  deleteEvent(event: CalendarEvent): void {
    if (!window.confirm(`¿Eliminar “${event.title}”?`)) return;
    this.eventsStore.remove(event.id);
    this.events = this.eventsStore.list();
  }

  async enableNotifications(): Promise<void> {
    this.notificationMessage = '';
    this.notificationPermission = await this.reminders.requestPermission();
    if (this.notificationPermission === 'granted') this.notificationMessage = 'Notificaciones activadas. Recibirás los recordatorios mientras el sistema esté abierto.';
    else if (this.notificationPermission === 'unsupported') this.notificationMessage = 'Este navegador no admite notificaciones del sistema; los recordatorios seguirán en el calendario.';
    else if (this.notificationPermission === 'denied') this.notificationMessage = 'El navegador bloqueó las notificaciones. Puedes habilitarlas desde los permisos del sitio.';
    else this.notificationMessage = 'No se concedió el permiso para mostrar notificaciones.';
  }

  permissionLabel(): string {
    if (this.notificationPermission === 'granted') return 'Notificaciones activas';
    if (this.notificationPermission === 'denied') return 'Notificaciones bloqueadas';
    if (this.notificationPermission === 'unsupported') return 'Notificaciones no disponibles';
    return 'Activar notificaciones';
  }

  accountName(id: number | null): string {
    return this.accounts.find(account => account.id === id)?.nombre ?? 'Tarjeta';
  }

  eventDateTime(event: CalendarEvent): Date {
    return this.nextOccurrence(event, new Date()) ?? this.toDateTime(event);
  }

  recurrenceLabel(recurrence: CalendarRecurrence): string {
    return this.recurrenceOptions.find(option => option.value === recurrence)?.label ?? 'No se repite';
  }

  reminderLabel(minutes: number): string {
    return this.reminderOptions.find(option => option.value === minutes)?.label ?? `${minutes} minutos antes`;
  }

  private eventsForDate(date: string): CalendarEvent[] {
    const selected = new Date(`${date}T00:00:00`);
    const selectedDay = this.dayNumber(selected);
    return this.events.filter(event => {
      if (event.completed) return false;
      if (event.recurrence === 'NONE') return event.date === date;
      const base = new Date(`${event.date}T00:00:00`);
      if (selectedDay < this.dayNumber(base)) return false;
      const day = selected.getDate();
      if (event.recurrence === 'DAILY') return true;
      if (event.recurrence === 'WEEKLY') return (selectedDay - this.dayNumber(base)) % 7 === 0;
      if (event.recurrence === 'MONTHLY') return day === Math.min(base.getDate(), new Date(selected.getFullYear(), selected.getMonth() + 1, 0).getDate());
      return selected.getMonth() === base.getMonth() && day === Math.min(base.getDate(), new Date(selected.getFullYear(), selected.getMonth() + 1, 0).getDate());
    });
  }

  private nextOccurrence(event: CalendarEvent, now: Date): Date | null {
    const first = this.toDateTime(event);
    if (event.recurrence === 'NONE') return first.getTime() >= now.getTime() ? first : null;
    let occurrence = new Date(first);
    let guard = 0;
    while (occurrence.getTime() < now.getTime() && guard++ < 3000) {
      if (event.recurrence === 'DAILY') occurrence.setDate(occurrence.getDate() + 1);
      else if (event.recurrence === 'WEEKLY') occurrence.setDate(occurrence.getDate() + 7);
      else if (event.recurrence === 'MONTHLY') occurrence = this.addMonthOnDay(occurrence, first.getDate());
      else occurrence = this.addYearOnDay(occurrence, first.getMonth(), first.getDate());
    }
    return guard >= 3000 ? null : occurrence;
  }

  private addMonthOnDay(date: Date, day: number): Date {
    const next = new Date(date.getFullYear(), date.getMonth() + 1, 1, date.getHours(), date.getMinutes());
    next.setDate(Math.min(day, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()));
    return next;
  }

  private addYearOnDay(date: Date, month: number, day: number): Date {
    const next = new Date(date.getFullYear() + 1, month, 1, date.getHours(), date.getMinutes());
    next.setDate(Math.min(day, new Date(next.getFullYear(), month + 1, 0).getDate()));
    return next;
  }

  private toDateTime(event: CalendarEvent): Date {
    return new Date(`${event.date}T${event.time}:00`);
  }

  private dayNumber(date: Date): number {
    return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000);
  }

  private dateKey(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private emptyForm(date = this.selectedDate): CalendarEvent {
    return {
      id: '', title: '', description: '', type: 'ACTIVIDAD' as CalendarEventType,
      date, time: '09:00', accountId: null, amount: null,
      recurrence: 'NONE', reminderMinutes: 30, completed: false, createdAt: ''
    };
  }

  private createId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}





