import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { EmailMovementNotificationService } from './email-movement-notification.service';
import { API_BASE_URL } from '../api.config';

describe('Email movement notifications', () => {
  let service: EmailMovementNotificationService;
  let http: HttpTestingController;
  const url = `${API_BASE_URL}/gmail-bac/notifications`;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(EmailMovementNotificationService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { service.stop(); http.verify(); });

  it('establishes a baseline and emits every newly imported movement only once', fakeAsync(() => {
    const ids: number[] = [];
    service.registered$.subscribe(n => ids.push(n.id));
    service.start();
    service.start();
    http.expectOne(url).flush({ cursor: 100, movements: [] });
    tick(10_000);
    http.expectOne(`${url}?after=100`).flush({ cursor: 103, movements: [101,102,103].map(id => ({id, monto: 5, descripcion: 'Compra'})) });
    expect(ids).toEqual([101,102,103]);
    tick(10_000);
    http.expectOne(`${url}?after=103`).flush({ cursor: 103, movements: [] });
    expect(ids.length).toBe(3);
    service.stop();
  }));

  it('keeps its cursor on network failure and stops polling after logout', fakeAsync(() => {
    const ids: number[] = [];
    service.registered$.subscribe(n => ids.push(n.id));
    service.start();
    http.expectOne(url).flush({ cursor: 100, movements: [] });
    tick(10_000);
    http.expectOne(`${url}?after=100`).error(new ProgressEvent('error'));
    tick(10_000);
    http.expectOne(`${url}?after=100`).flush({ cursor: 100, movements: [] });
    service.stop();
    tick(20_000);
    http.expectNone(`${url}?after=100`);
    service.start();
    http.expectOne(url).flush({ cursor: 200, movements: [] });
    expect(ids).toEqual([]);
    service.stop();
  }));

  it('drains full pages and cancels a pending request when the session ends', fakeAsync(() => {
    const ids: number[] = [];
    service.registered$.subscribe(n => ids.push(n.id));
    service.start();
    http.expectOne(url).flush({ cursor: 0, movements: [] });
    tick(10_000);
    http.expectOne(`${url}?after=0`).flush({ cursor: 50, movements: Array.from({length: 50}, (_, i) => ({id: i + 1, monto: 1, descripcion: 'Compra'})) });
    http.expectOne(`${url}?after=50`).flush({ cursor: 51, movements: [{id: 51, monto: 1, descripcion: 'Compra'}] });
    expect(ids.length).toBe(51);
    tick(10_000);
    const pending = http.expectOne(`${url}?after=51`);
    service.stop();
    expect(pending.cancelled).toBeTrue();
  }));
});
