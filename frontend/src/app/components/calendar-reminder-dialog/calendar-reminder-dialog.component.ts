import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CalendarReminderNotice } from '../../services/calendar-reminder.service';

@Component({
  selector: 'app-calendar-reminder-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './calendar-reminder-dialog.component.html',
  styleUrl: './calendar-reminder-dialog.component.css'
})
export class CalendarReminderDialogComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) notice!: CalendarReminderNotice;
  @Input() pending = 0;
  @Output() dismissed = new EventEmitter<void>();
  @Output() viewCalendar = new EventEmitter<void>();
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  private previousOverflow = '';

  ngAfterViewInit(): void {
    this.previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    this.dialog.nativeElement.showModal();
  }

  cancel(event: Event): void { event.preventDefault(); this.dismissed.emit(); }

  ngOnDestroy(): void {
    this.dialog.nativeElement.close();
    document.body.style.overflow = this.previousOverflow;
  }

  dateLabel(): string {
    return this.notice.date.toLocaleDateString('es-SV', { weekday: 'long', day: 'numeric', month: 'long' });
  }
  timeLabel(): string {
    return this.notice.date.toLocaleTimeString('es-SV', { hour: '2-digit', minute: '2-digit' });
  }
}
