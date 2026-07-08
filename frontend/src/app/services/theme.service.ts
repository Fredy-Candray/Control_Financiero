import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private storageKey = 'theme'; // 'light' | 'dark'

  constructor() {}

  initTheme(): void {
    const saved = localStorage.getItem(this.storageKey);
    if (saved) {
      this.apply(saved === 'dark');
      return;
    }

    // Default prefer-color-scheme check
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    this.apply(prefersDark);
  }

  toggle(): void {
    const isDark = document.body.classList.contains('dark-theme');
    this.apply(!isDark);
  }

  setDark(dark: boolean): void {
    this.apply(dark);
  }

  isDark(): boolean {
    return document.body.classList.contains('dark-theme');
  }

  private apply(dark: boolean): void {
    if (dark) {
      document.body.classList.add('dark-theme');
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem(this.storageKey, 'dark');
    } else {
      document.body.classList.remove('dark-theme');
      document.documentElement.setAttribute('data-theme', 'light');
      localStorage.setItem(this.storageKey, 'light');
    }
  }
}
