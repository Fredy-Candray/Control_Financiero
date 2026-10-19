import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { API_BASE_URL } from '../api.config';

export interface EmailMovementNotice { id: number; descripcion: string; monto: number; }
interface NoticePage { cursor: number; movements: EmailMovementNotice[]; }

@Injectable({ providedIn: 'root' })
export class EmailMovementNotificationService {
  private readonly notices = new Subject<EmailMovementNotice>();
  readonly registered$ = this.notices.asObservable();
  private timer: ReturnType<typeof setInterval> | null = null;
  private request: Subscription | null = null;
  private cursor: number | null = null;
  private active = false;
  private busy = false;
  constructor(private http: HttpClient) {}

  start(): void {
    if (this.active) return;
    this.active = true;
    this.poll();
    this.timer = setInterval(() => this.poll(), 10_000);
  }

  stop(): void {
    this.active = false;
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.request?.unsubscribe();
    this.request = null;
    this.busy = false;
    this.cursor = null;
  }

  private poll(): void {
    if (!this.active || this.busy) return;
    this.busy = true;
    const suffix = this.cursor === null ? '' : `?after=${this.cursor}`;
    this.request = this.http.get<NoticePage>(`${API_BASE_URL}/gmail-bac/notifications${suffix}`).subscribe({
      next: page => {
        this.busy = false;
        this.cursor = page.cursor;
        page.movements.forEach(notice => this.notices.next(notice));
        // Drain large batches without skipping movements between pages.
        if (page.movements.length === 50) this.poll();
      },
      error: (error: HttpErrorResponse) => {
        this.busy = false;
        if (error.status === 401 || error.status === 403) this.stop();
        // Preserve the cursor on temporary failures so the next poll catches up.
      }
    });
  }
}
