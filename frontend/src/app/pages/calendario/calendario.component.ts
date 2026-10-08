import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { CalendarEvent, CalendarEventType, CalendarRecurrence } from '../../models/calendar-event';
import { CalendarCategory } from '../../models/calendar-category';
import { CalendarEventsService } from '../../services/calendar-events.service';
import { CalendarCategoriesService } from '../../services/calendar-categories.service';
import { CalendarReminderService } from '../../services/calendar-reminder.service';
import { Cuenta } from '../../models/cuenta';
import { API_BASE_URL } from '../../api.config';
import { readAuthSessionValue } from '../../services/auth.service';


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
  categories: CalendarCategory[] = [];
  accounts: Cuenta[] = [];
  month = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  selectedDate = this.dateKey(new Date());
  modalOpen = false;
  categoryModalOpen = false;
  editingCategoryId: string | null = null;
  categoryError = '';
  categoryDraft = this.emptyCategoryDraft();
  editingId: string | null = null;
  notificationPermission: NotificationPermission | 'unsupported' = 'default';
  notificationMessage = '';
  formError = '';
  saving = false;
  whatsappPhone = '';
  form = this.emptyForm();

  constructor(
    private eventsStore: CalendarEventsService,
    private categoriesStore: CalendarCategoriesService,
    private reminders: CalendarReminderService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.categories = this.categoriesStore.list();
    this.events = this.eventsStore.list();
    let migrated = false;
    this.events = this.events.map(event => {
      if (event.categoryId && this.categories.some(category => category.id === event.categoryId)) return event;
      migrated = true;
      return { ...event, categoryId: this.defaultCategoryId(event.type) };
    });
    if (migrated) this.events.forEach(event => this.eventsStore.save(event));
    this.notificationPermission = this.reminders.permission();
    this.reminders.start();
    this.eventsStore.synchronize().subscribe({
      next: serverEvents => {
        this.events = serverEvents.map(event => ({
          ...event,
          categoryId: event.categoryId && this.categories.some(category => category.id === event.categoryId)
            ? event.categoryId : this.defaultCategoryId(event.type)
        }));
        this.events.filter((event, index) => event.categoryId !== serverEvents[index]?.categoryId)
          .forEach(event => this.eventsStore.saveRemote(event).subscribe({ error: () => undefined }));
      },
      error: error => this.notificationMessage = this.serverErrorMessage(error, 'No se pudo sincronizar el calendario con el servidor. Los avisos por correo y WhatsApp no estarán disponibles.')
    });
    this.http.get<{ telefono?: string }>(`${API_BASE_URL}/auth/me`).subscribe({
      next: user => this.whatsappPhone = user.telefono ?? '',
      error: () => undefined
    });
    const userId = Number(readAuthSessionValue('userId') || '1');
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
  calendarDayEvents(date: Date): CalendarEvent[] { return this.eventsForDate(this.dateKey(date)).slice(0, 2); }
  calendarDayEventCount(date: Date): number { return this.eventsForDate(this.dateKey(date)).length; }
  categoryColor(event: CalendarEvent): string { return this.categoryForEvent(event)?.color ?? '#16a06a'; }
  categoryName(event: CalendarEvent): string { return this.categoryForEvent(event)?.name ?? 'Actividad'; }
  selectedCategoryColor(): string { return this.categories.find(category => category.id === this.form.categoryId)?.color ?? '#16a06a'; }
  isToday(date: Date): boolean { return this.dateKey(date) === this.dateKey(new Date()); }
  isSelected(date: Date): boolean { return this.dateKey(date) === this.selectedDate; }
  isCurrentMonth(date: Date): boolean { return date.getMonth() === this.month.getMonth(); }

  openCreate(date = this.selectedDate): void {
    this.editingId = null;
    this.formError = '';
    this.form = this.emptyForm(date);
    this.modalOpen = true;
  }

  openCategoryManager(): void {
    this.editingCategoryId = null;
    this.categoryDraft = this.emptyCategoryDraft();
    this.categoryError = '';
    this.categoryModalOpen = true;
  }

  closeCategoryManager(): void {
    this.categoryModalOpen = false;
    this.categoryError = '';
  }

  saveCategory(): void {
    const name = this.categoryDraft.name.trim();
    if (!name) { this.categoryError = 'Escribe un nombre para la categoría.'; return; }
    if (this.categories.some(category => category.name.toLocaleLowerCase() === name.toLocaleLowerCase() && category.id !== this.editingCategoryId)) {
      this.categoryError = 'Ya existe una categoría con ese nombre.';
      return;
    }
    this.categoriesStore.save({
      id: this.editingCategoryId ?? this.createId(),
      name,
      color: this.categoryDraft.color
    });
    this.categories = this.categoriesStore.list();
    this.editingCategoryId = null;
    this.categoryDraft = this.emptyCategoryDraft();
    this.categoryError = '';
  }

  editCategory(category: CalendarCategory): void {
    this.editingCategoryId = category.id;
    this.categoryDraft = { name: category.name, color: category.color };
    this.categoryError = '';
  }

  cancelCategoryEdit(): void {
    this.editingCategoryId = null;
    this.categoryDraft = this.emptyCategoryDraft();
    this.categoryError = '';
  }

  deleteCategory(category: CalendarCategory): void {
    if (this.categories.length <= 1) { this.categoryError = 'Debe quedar al menos una categoría.'; return; }
    const replacement = this.categories.find(item => item.id !== category.id)!;
    const affected = this.events.filter(event => event.categoryId === category.id);
    const detail = affected.length ? ` ${affected.length} evento(s) se reasignarán a “${replacement.name}”.` : '';
    if (!window.confirm(`¿Eliminar la categoría “${category.name}”?${detail}`)) return;
    affected.forEach(event => {
      const updated = { ...event, categoryId: replacement.id };
      this.eventsStore.save(updated);
      this.eventsStore.saveRemote(updated).subscribe({ error: () => undefined });
    });
    this.categoriesStore.remove(category.id);
    this.categories = this.categoriesStore.list();
    this.events = this.eventsStore.list();
    if (this.editingCategoryId === category.id) {
      this.editingCategoryId = null;
      this.categoryDraft = this.emptyCategoryDraft();
    }
    this.categoryError = '';
  }

  openEdit(event: CalendarEvent): void {
    this.editingId = event.id;
    this.formError = '';
    this.form = {
      ...event,
      categoryId: event.categoryId ?? this.defaultCategoryId(event.type),
      notifyBrowser: event.notifyBrowser !== false,
      notifyEmail: event.notifyEmail === true,
      notifyWhatsapp: event.notifyWhatsapp === true
    };
    this.modalOpen = true;
  }

  closeModal(): void { this.modalOpen = false; this.formError = ''; }

  saveEvent(): void {
    if (!this.form.title.trim() || !this.form.date || !this.form.time) {
      this.formError = 'Completa el título, la fecha y la hora.';
      return;
    }
    if (!this.form.categoryId || !this.categories.some(category => category.id === this.form.categoryId)) {
      this.formError = 'Selecciona una categoría para el evento.';
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
    if (event.notifyWhatsapp) {
      const phone = this.whatsappPhone.trim();
      const normalizedPhone = phone.replace(/[\s()-]/g, '');
      if (!/^\+[1-9]\d{7,14}$/.test(normalizedPhone) && !/^\d{8}$/.test(normalizedPhone)) {
        this.formError = 'Agrega un teléfono de 8 dígitos (7777-7777) o con código de país (+50370000000) para usar WhatsApp.';
        return;
      }
      this.saving = true;
      this.http.put(`${API_BASE_URL}/auth/me/telefono`, { telefono: phone }).subscribe({
        next: () => this.persistEvent(event),
        error: () => { this.saving = false; this.formError = 'No se pudo guardar el teléfono. Revisa que tenga 8 dígitos o un código de país válido.'; }
      });
    } else {
      this.persistEvent(event);
    }
  }

  private persistEvent(event: CalendarEvent): void {
    this.saving = true;
    this.eventsStore.saveRemote(event).subscribe({
      next: saved => {
        this.saving = false;
        this.events = this.eventsStore.list();
        this.selectedDate = saved.date;
        this.month = new Date(Number(saved.date.slice(0, 4)), Number(saved.date.slice(5, 7)) - 1, 1);
        this.closeModal();
      },
      error: error => {
        this.saving = false;
        this.formError = this.serverErrorMessage(error, 'No se pudo guardar la actividad en el servidor. Inténtalo de nuevo para activar sus recordatorios.');
      }
    });
  }

  private serverErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback;
    if (error.status === 401) return 'Tu sesión del servidor expiró. Vuelve a iniciar sesión y después agenda la actividad otra vez.';
    if (error.status === 0) return 'No se pudo conectar con el servidor. Confirma que Spring Boot siga iniciado y vuelve a intentarlo.';
    const detail = error.error?.message ?? error.error?.detail;
    return typeof detail === 'string' && detail.trim() ? detail : fallback;
  }

  deleteEvent(event: CalendarEvent): void {
    if (!window.confirm(`¿Eliminar “${event.title}”?`)) return;
    this.eventsStore.removeRemote(event.id).subscribe({
      next: () => this.events = this.eventsStore.list(),
      error: () => this.notificationMessage = 'No se pudo eliminar la actividad del servidor.'
    });
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

  eventsForDate(date: string): CalendarEvent[] {
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
      categoryId: this.defaultCategoryId('ACTIVIDAD'),
      date, time: '09:00', accountId: null, amount: null,
      recurrence: 'NONE', reminderMinutes: 30, completed: false,
      notifyBrowser: true, notifyEmail: true, notifyWhatsapp: false, createdAt: ''
    };
  }

  private emptyCategoryDraft(): { name: string; color: string } { return { name: '', color: '#16a06a' }; }

  private defaultCategoryId(type: CalendarEventType): string {
    const preferredId = type === 'PAGO_TARJETA' ? 'payment' : 'meeting';
    return this.categories?.find(category => category.id === preferredId)?.id ?? this.categories?.[0]?.id ?? '';
  }

  private categoryForEvent(event: CalendarEvent): CalendarCategory | undefined {
    return this.categories.find(category => category.id === event.categoryId)
      ?? this.categories.find(category => category.id === this.defaultCategoryId(event.type));
  }

  private createId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}





