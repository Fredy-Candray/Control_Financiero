import { Component } from '@angular/core';

@Component({
  selector: 'app-inicio',
  standalone: true,
  template: `<div class="home-brand" aria-label="COFI · Control Financiero"><img src="assets/cofi-logo.png" alt="" /><div><h1>COFI</h1><p>Control Financiero</p></div></div>`,
  styles: [`
    :host { position:absolute; inset:0; display:grid; place-items:center; min-height:260px; padding:32px; box-sizing:border-box; }
    .home-brand { display:flex; align-items:center; justify-content:center; gap:clamp(20px,3vw,40px); max-width:100%; }
    img { width:clamp(110px,14vw,200px); height:auto; object-fit:contain; flex-shrink:0; }
    h1 { margin:0; color:var(--text); font-size:clamp(3rem,7vw,6rem); font-weight:800; letter-spacing:-.045em; line-height:1.1; }
    p { margin:12px 0 0; color:var(--accent); font-size:clamp(.7rem,1.2vw,1rem); letter-spacing:.14em; text-transform:uppercase; }
    @media(max-width:480px) { .home-brand { flex-direction:column; text-align:center; gap:20px; } }
  `]
})
export class InicioComponent {}
