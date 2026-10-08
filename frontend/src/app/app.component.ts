import { Component, OnInit } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs/operators';
import { ThemeService } from './services/theme.service';
import { CalendarReminderService } from './services/calendar-reminder.service';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from './api.config';
import { AuthService } from './services/auth.service';

interface PerfilBadge {
  username: string;
  fotoPerfil: string | null;
}

interface MenuOption {
  etiqueta: string;
  ruta: string;
  icono: 'dashboard' | 'cuentas' | 'movimientos' | 'calendario' | 'usuarios' | 'configuracion' | 'reporte';
}

interface MenuSection {
  titulo: string;
  opciones: MenuOption[];
}

interface MenuResponse {
  secciones: MenuSection[];
  rutasPermitidas: string[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  title = 'control-financiero-ui';
  username = 'Invitado';
  userRole = 'Usuario';
  showTopbar = true;
  showSidebar = true;
  showUserMenu = false;
  currentProfile: PerfilBadge | null = null;
  menuSections: MenuSection[] = [];
  isAuthenticated = false;
  private roleLookupInProgress = false;
  private sessionValidationStarted = false;
  sidebarCollapsed = false;
  sidebarOpen = false; // mobile overlay open state
  calendarReminder: { title: string; date: Date; payment: boolean } | null = null;
  private reminderToastTimer: ReturnType<typeof setTimeout> | null = null;
  constructor(private router: Router, private themeService: ThemeService, private calendarReminders: CalendarReminderService, private http: HttpClient, private authService: AuthService) {
    this.calendarReminders.due$.subscribe(reminder => {
      this.calendarReminder = reminder;
      if (this.reminderToastTimer) clearTimeout(this.reminderToastTimer);
      this.reminderToastTimer = setTimeout(() => this.calendarReminder = null, 20_000);
    });
  }

  ngOnInit(): void {
    // initialize theme early
    this.themeService.initTheme();
    this.calendarReminders.start();
    this.updateLayoutState(this.router.url);

    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updateLayoutState(event.urlAfterRedirects);
      });
  }

  private syncUserFromStorage(): void {
    const storedName = this.authService.getValue('username');
    this.username = storedName?.trim() ? storedName : 'Invitado';
    this.isAuthenticated = this.authService.isAuthenticated();
    this.userRole = this.authService.getValue('userRole') || 'Usuario';
    if (this.isAuthenticated && !this.sessionValidationStarted && !this.roleLookupInProgress) {
      this.sessionValidationStarted = true;
      this.roleLookupInProgress = true;
      this.http.get<{ id: number; username: string; rol: string }>(`${API_BASE_URL}/auth/me`).subscribe({
        next: (user) => {
          this.roleLookupInProgress = false;
          this.userRole = user.rol || 'Usuario';
          if (user.username?.trim()) this.username = user.username;
          const persistent = localStorage.getItem('isLoggedIn') === 'true';
          const storage = persistent ? localStorage : sessionStorage;
          if (user.id) storage.setItem('userId', String(user.id));
          storage.setItem('username', this.username);
          storage.setItem('userRole', this.userRole);
          this.loadCurrentProfile();
          this.loadMenu();
        },
        error: () => {
          this.roleLookupInProgress = false;
          this.sessionValidationStarted = false;
          sessionStorage.setItem('controlFinanciero.sessionExpired', 'true');
          this.authService.clearSession();
          this.isAuthenticated = false;
          this.router.navigateByUrl('/login', { replaceUrl: true });
        }
      });
    }
  }

  private updateLayoutState(url: string): void {
    const normalizedUrl = url.split('?')[0].split('#')[0];
    const isPasswordResetPage = normalizedUrl === '/reset-password';
    if (isPasswordResetPage) {
      // A reset link is public; a stale client-side session must not redirect it to login.
      this.isAuthenticated = false;
    } else {
      this.syncUserFromStorage();
    }

    const isPublicPage = normalizedUrl === '/login' || normalizedUrl === '/register'
      || normalizedUrl === '/reset-password' || normalizedUrl === '/';

    this.showTopbar = this.isAuthenticated && !isPublicPage;
    this.showSidebar = this.isAuthenticated && !isPublicPage;
    if (!this.showSidebar) {
      this.sidebarCollapsed = false;
    }
  }

  toggleUserMenu(): void {
    this.showUserMenu = !this.showUserMenu;
  }

  openProfile(): void {
    this.showUserMenu = false;
    this.sidebarOpen = false;
    try { document.body.style.overflow = ''; } catch { }
    void this.router.navigateByUrl('/perfil');
  }

  closeUserMenu(): void {
    this.showUserMenu = false;
    this.sidebarOpen = false;
    try { document.body.style.overflow = ''; } catch { }
  }

  private loadCurrentProfile(): void {
    this.http.get<PerfilBadge>(`${API_BASE_URL}/auth/me/profile`).subscribe({
      next: profile => {
        this.currentProfile = profile;
        if (profile.username?.trim()) this.username = profile.username;
      }
    });
  }

  private loadMenu(): void {
    this.http.get<MenuResponse>(`${API_BASE_URL}/auth/me/menu`).subscribe({
      next: response => { this.menuSections = response.secciones || []; },
      error: () => { this.menuSections = []; }
    });
  }

  closeCalendarReminder(): void {
    this.calendarReminder = null;
    if (this.reminderToastTimer) clearTimeout(this.reminderToastTimer);
  }

  openCalendarReminder(): void {
    this.closeCalendarReminder();
    void this.router.navigate(['/calendario']);
  }

  calendarReminderTime(date: Date): string {
    return date.toLocaleTimeString('es-SV', { hour: '2-digit', minute: '2-digit' });
  }

  // Theme helpers for template
  isDark(): boolean {
    return this.themeService.isDark();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  // detect desktop by width; keep simple to avoid injecting Window in constructor
  isDesktop(): boolean {
    try {
      return window.matchMedia('(min-width: 801px)').matches;
    } catch {
      return true;
    }
  }

  // Mobile drawer controls
  toggleSidebarOverlay(): void {
        this.sidebarOpen = !this.sidebarOpen;
        // when opening the drawer ensure the sidebar is expanded (show full labels)
        if (this.sidebarOpen) {
          this.sidebarCollapsed = false;
          try { document.body.style.overflow = 'hidden'; } catch { }
        } else {
          try { document.body.style.overflow = ''; } catch { }
        }
  }

  closeSidebarOverlay(): void {
    this.sidebarOpen = false;
    try { document.body.style.overflow = ''; } catch { }
  }

  onToggleButtonClick(event: Event): void {
    this.toggleSidebar();
    const el = event.currentTarget as HTMLElement | null;
    if (el && typeof el.blur === 'function') {
      el.blur();
    }
  }

  logout(): void {
    this.showUserMenu = false;
    this.authService.logout().subscribe({ error: () => undefined });
    this.authService.clearSession();
    sessionStorage.clear();
    this.username = 'Invitado';
    this.currentProfile = null;
    this.isAuthenticated = false;
    this.sessionValidationStarted = false;
    this.showTopbar = false;
    this.showSidebar = false;
    this.router.navigateByUrl('/login', { replaceUrl: true });
  }
}
