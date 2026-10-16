import { of, throwError } from 'rxjs';
import { CalendarioComponent } from './calendario.component';
import { CalendarEvent } from '../../models/calendar-event';
import { CalendarEventsService } from '../../services/calendar-events.service';
import { CalendarCategoriesService } from '../../services/calendar-categories.service';
import { CalendarReminderService } from '../../services/calendar-reminder.service';
import { HttpClient } from '@angular/common/http';

describe('Selección de actividades del calendario', () => {
  let component: CalendarioComponent;
  let store: jasmine.SpyObj<CalendarEventsService>;
  let events: CalendarEvent[];

  beforeEach(() => {
    events = ['a', 'b', 'c'].map(id => ({
      id, title: id, date: '2026-10-01', time: '10:00', recurrence: 'NONE', completed: false
    } as CalendarEvent));
    store = jasmine.createSpyObj('events', ['list', 'removeRemote']);
    store.list.and.callFake(() => events);
    store.removeRemote.and.callFake(id => {
      events = events.filter(event => event.id !== id);
      return of(undefined);
    });
    component = new CalendarioComponent(store, {} as CalendarCategoriesService, {} as CalendarReminderService, {} as HttpClient);
    component.events = events;
    component.selectedDate = '2026-10-01';
    spyOn(window, 'confirm').and.returnValue(true);
  });

  it('solo habilita selección para más de dos actividades y la limpia al cambiar de día', () => {
    component.events = events.slice(0, 2);
    component.startSelection();
    expect(component.selectionMode).toBeFalse();
    component.events = events;
    component.startSelection();
    component.toggleAllActivities();
    expect(component.selectedActivities.length).toBe(3);
    component.selectDate(new Date(2026, 9, 2));
    expect(component.selectedEventIds.size).toBe(0);
    expect(component.selectionMode).toBeFalse();
  });

  it('elimina únicamente las seleccionadas después de una confirmación', () => {
    component.startSelection();
    component.toggleEventSelection('a');
    component.toggleEventSelection('c');
    component.deleteSelectedActivities();
    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(store.removeRemote.calls.allArgs()).toEqual([['a'], ['c']]);
    expect(component.events.map(event => event.id)).toEqual(['b']);
    expect(component.selectionMode).toBeFalse();
  });

  it('continúa tras un fallo y conserva seleccionadas las actividades que fallaron', () => {
    store.removeRemote.and.callFake(id => {
      if (id === 'b') return throwError(() => new Error('Servidor no disponible'));
      events = events.filter(event => event.id !== id);
      return of(undefined);
    });
    component.startSelection();
    component.toggleAllActivities();
    component.deleteSelectedActivities();
    expect(store.removeRemote).toHaveBeenCalledTimes(3);
    expect(component.events.map(event => event.id)).toEqual(['b']);
    expect([...component.selectedEventIds]).toEqual(['b']);
    expect(component.selectionMode).toBeTrue();
    expect(component.deletingSelected).toBeFalse();
  });

  it('no elimina si se cancela la confirmación', () => {
    (window.confirm as jasmine.Spy).and.returnValue(false);
    component.startSelection();
    component.toggleAllActivities();
    component.deleteSelectedActivities();
    expect(store.removeRemote).not.toHaveBeenCalled();
  });

  it('advierte cuando la selección incluye una actividad recurrente', () => {
    events[0].recurrence = 'DAILY';
    component.startSelection();
    component.toggleEventSelection('a');
    component.deleteSelectedActivities();
    expect(window.confirm).toHaveBeenCalledWith(jasmine.stringContaining('todas sus repeticiones'));
  });

  it('muestra próximas actividades con horas del servidor que incluyen segundos', () => {
    jasmine.clock().install();
    try {
      jasmine.clock().mockDate(new Date(2026, 9, 13, 11));
      component.events = [
        { ...events[0], date: '2026-10-23', time: '09:00:00' },
        { ...events[1], date: '2026-10-22', time: '14:30' },
        { ...events[2], date: '2026-10-09', time: '09:00:00' }
      ];
      expect(component.upcomingEvents.map(event => event.id)).toEqual(['b', 'a']);
      expect(component.eventDateTime(component.events[0]).getDate()).toBe(23);
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
