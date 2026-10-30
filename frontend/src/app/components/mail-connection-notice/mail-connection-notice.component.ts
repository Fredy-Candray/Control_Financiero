import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { API_BASE_URL } from '../../api.config';

@Component({
  selector: 'app-mail-connection-notice', standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './mail-connection-notice.component.html',
  styleUrl: './mail-connection-notice.component.css'
})
export class MailConnectionNoticeComponent implements OnInit, OnDestroy {
  readerNeedsReconnect = false;
  senderNeedsReconnect = false;
  private timer: ReturnType<typeof setInterval> | null = null;
  private request: Subscription | null = null;
  private busy = false;
  private active = true;
  private readonly resume = () => { if (document.visibilityState === 'visible') this.poll(); };
  constructor(private http: HttpClient) {}
  ngOnInit(): void {
    this.poll();
    this.timer = setInterval(() => this.poll(), 60_000);
    window.addEventListener('focus', this.resume);
    document.addEventListener('visibilitychange', this.resume);
  }
  private poll(): void {
    if(!this.active || this.busy) return;
    this.busy = true;
    this.request = forkJoin({
      reader: this.http.get<{ reauthorizationRequired: boolean }>(`${API_BASE_URL}/gmail-bac`)
        .pipe(catchError(error => this.failed(error))),
      sender: this.http.get<{ state: string }>(`${API_BASE_URL}/notificaciones/correo/estado`)
        .pipe(catchError(error => this.failed(error)))
    }).subscribe(result => {
      this.busy = false;
      if (!this.active) return;
      if (result.reader) this.readerNeedsReconnect = result.reader.reauthorizationRequired === true;
      if (result.sender) this.senderNeedsReconnect = result.sender.state === 'REAUTH_REQUIRED';
    });
  }
  private failed(error: HttpErrorResponse) {
    // Network interruptions never become a permission-expired warning.
    if (error.status === 401 || error.status === 403) {
      this.active = false;
      this.readerNeedsReconnect = false;
      this.senderNeedsReconnect = false;
      if (this.timer) clearInterval(this.timer);
    }
    return of(null);
  }
  ngOnDestroy(): void {
    this.active = false;
    if (this.timer) clearInterval(this.timer);
    this.request?.unsubscribe();
    window.removeEventListener('focus', this.resume);
    document.removeEventListener('visibilitychange', this.resume);
  }
}
