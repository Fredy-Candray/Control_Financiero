import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

interface InstallPrompt extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

@Component({
  selector: 'app-install-app',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button *ngIf="!installed" type="button" class="install-launcher" (click)="open()" aria-haspopup="dialog">↓ Instalar COFI</button>
    <div *ngIf="showHelp" class="install-backdrop" (click)="close()">
      <section class="install-dialog" role="dialog" aria-modal="true" aria-labelledby="install-title" (click)="$event.stopPropagation()">
        <button #closeControl type="button" class="install-close" (click)="close()" aria-label="Cerrar instrucciones">×</button>
        <img src="assets/icons/cofi-192.png" width="64" height="64" alt="Logo COFI">
        <h2 id="install-title">COFI en tu teléfono</h2>
        <p>Abre COFI desde su icono y usa tus mismas cuentas. Necesitas internet.</p>
        <ng-container *ngIf="ios; else otherDevices">
          <ol><li>Abre esta dirección en Safari.</li><li>Toca Compartir y luego <strong>Agregar a inicio</strong>.</li><li>Activa <strong>Abrir como app web</strong> si aparece y toca Agregar.</li></ol>
        </ng-container>
        <ng-template #otherDevices>
          <button *ngIf="promptEvent" type="button" class="install-confirm" (click)="install()">Instalar aplicación</button>
          <p>En Android, abre el menú ⋮ de Chrome y elige <strong>Instalar aplicación</strong> o <strong>Agregar a la pantalla principal</strong>. En una computadora, utiliza la opción de instalar del navegador.</p>
        </ng-template>
        <p class="install-note">La app usa el mismo sistema; no crea otra cuenta ni copia tus saldos.</p>
      </section>
    </div>`,
  styles: [`
    .install-launcher{position:fixed;right:16px;bottom:max(16px,env(safe-area-inset-bottom));z-index:90;padding:10px 16px;border:1px solid var(--card-border);border-radius:24px;background:var(--surface);color:var(--text);box-shadow:var(--shadow-card);font:600 14px system-ui;cursor:pointer}
    .install-backdrop{position:fixed;inset:0;z-index:2100;display:grid;place-items:center;padding:20px;background:#0009}
    .install-dialog{position:relative;max-width:420px;max-height:85dvh;overflow-y:auto;padding:28px;border:1px solid var(--card-border);border-radius:18px;background:var(--surface);color:var(--text);box-shadow:0 16px 60px #0004;line-height:1.6}
    .install-dialog h2{margin:12px 0}.install-dialog img{display:block;border-radius:14px}.install-dialog ol{padding-left:22px}.install-close{position:absolute;top:8px;right:12px;border:0;background:none;color:var(--text);font-size:28px;cursor:pointer}.install-confirm{padding:12px 16px;border:0;border-radius:10px;background:var(--accent);color:#fff;cursor:pointer}.install-note{font-size:13px;color:var(--muted)}
    @media(display-mode:standalone){.install-launcher{display:none}}
  `]
})
export class InstallAppComponent {
  installed = window.matchMedia('(display-mode: standalone)').matches || !!(navigator as Navigator & { standalone?: boolean }).standalone;
  ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  showHelp = false;
  promptEvent: InstallPrompt | null = null;
  private previousFocus: HTMLElement | null = null;

  @HostListener('window:beforeinstallprompt', ['$event'])
  capturePrompt(event: Event): void { event.preventDefault(); this.promptEvent = event as InstallPrompt; }
  @HostListener('window:appinstalled')
  appInstalled(): void { this.installed = true; this.promptEvent = null; this.close(); }
  @HostListener('document:keydown', ['$event'])
  keyboard(event: KeyboardEvent): void {
    if (!this.showHelp) return;
    if (event.key === 'Escape') this.close();
    if (event.key === 'Tab') {
      const controls = Array.from(document.querySelectorAll<HTMLElement>('.install-dialog button'));
      const first = controls[0], last = controls[controls.length - 1];
      if ((event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
        event.preventDefault(); (event.shiftKey ? last : first)?.focus();
      }
    }
  }
  open(): void {
    this.previousFocus = document.activeElement as HTMLElement;
    this.showHelp = true;
    setTimeout(() => document.querySelector<HTMLButtonElement>('.install-close')?.focus());
  }
  close(): void { this.showHelp = false; this.previousFocus?.focus(); }
  async install(): Promise<void> {
    const prompt = this.promptEvent;
    if (!prompt) return;
    this.promptEvent = null;
    try { await prompt.prompt(); if ((await prompt.userChoice).outcome === 'accepted') this.close(); }
    catch { /* Keep the manual installation instructions available. */ }
  }
}
