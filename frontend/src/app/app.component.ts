import { Component, OnInit } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs/operators';

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
  showUserMenu = false;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.syncUserFromStorage();

    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.showTopbar = event.urlAfterRedirects !== '/login';
        this.syncUserFromStorage();
      });
  }

  private syncUserFromStorage(): void {
    const storedName = localStorage.getItem('username');
    this.username = storedName?.trim() ? storedName : 'Invitado';
  }

  toggleUserMenu(): void {
    this.showUserMenu = !this.showUserMenu;
  }

  logout(): void {
    this.showUserMenu = false;
    localStorage.removeItem('isLoggedIn');
    localStorage.removeItem('username');
    localStorage.removeItem('userId');
    this.username = 'Invitado';
    this.router.navigate(['/login']);
  }
}
