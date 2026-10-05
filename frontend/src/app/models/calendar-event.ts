export type CalendarEventType = 'PAGO_TARJETA' | 'ACTIVIDAD';
export type CalendarRecurrence = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface CalendarEvent {
  id: string;
  title: string;
  description: string;
  type: CalendarEventType;
  categoryId?: string;
  date: string;
  time: string;
  accountId: number | null;
  amount: number | null;
  recurrence: CalendarRecurrence;
  reminderMinutes: number;
  notifyEmail?: boolean;
  notifyBrowser?: boolean;
  notifyWhatsapp?: boolean;
  completed: boolean;
  createdAt: string;
}
