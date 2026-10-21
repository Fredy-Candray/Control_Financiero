import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));

// A network-only worker supplies an offline notice without caching financial data.
if ('serviceWorker' in navigator && window.isSecureContext) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/cofi-sw.js', { updateViaCache: 'none' })
      .catch(() => { /* Online access remains available without installation support. */ });
  });
}
