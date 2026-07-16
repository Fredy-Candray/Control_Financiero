import { Component, OnInit } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs/operators';
import { ThemeService } from './services/theme.service';

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
  showTopbar = true;
  showSidebar = true;
  showUserMenu = false;
  isAuthenticated = false;

  constructor(private router: Router, private themeService: ThemeService) {}

  ngOnInit(): void {
    // initialize theme early
    this.themeService.initTheme();
    this.updateLayoutState(this.router.url);

    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updateLayoutState(event.urlAfterRedirects);
      });
  }

  private syncUserFromStorage(): void {
    const storedName = localStorage.getItem('username');
    this.username = storedName?.trim() ? storedName : 'Invitado';
    this.isAuthenticated = localStorage.getItem('isLoggedIn') === 'true';
  }

  private updateLayoutState(url: string): void {
    this.syncUserFromStorage();

    const normalizedUrl = url.split('?')[0].split('#')[0];
    const isPublicPage = normalizedUrl === '/login' || normalizedUrl === '/register' || normalizedUrl === '/';

    this.showTopbar = this.isAuthenticated && !isPublicPage;
    this.showSidebar = this.isAuthenticated && !isPublicPage;
  }

  toggleUserMenu(): void {
    this.showUserMenu = !this.showUserMenu;
  }

  // Theme helpers for template
  isDark(): boolean {
    return this.themeService.isDark();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  logout(): void {
    this.showUserMenu = false;
    localStorage.removeItem('isLoggedIn');
    localStorage.removeItem('username');
    localStorage.removeItem('userId');
    sessionStorage.clear();
    this.username = 'Invitado';
    this.isAuthenticated = false;
    this.showTopbar = false;
    this.showSidebar = false;
    this.router.navigateByUrl('/login', { replaceUrl: true });
  }
}
