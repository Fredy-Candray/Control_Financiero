import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { API_BASE_URL } from '../../api.config';
import { readAuthSessionValue } from '../../services/auth.service';
import { AlertaAhorro } from '../../models/ahorro-liquidez';

@Component({selector:'app-ahorro-alerts',standalone:true,imports:[CommonModule,RouterLink],template:`
  <section *ngIf="aviso" class="budget-notice" role="status" aria-live="polite">
    <span aria-hidden="true">!</span><div><strong>{{aviso.umbral===100?'Presupuesto superado':'Aviso de presupuesto'}}</strong><p>{{aviso.nombre}} · {{aviso.gastado | currency:'USD'}} de {{aviso.limite | currency:'USD'}}</p><a routerLink="/ahorro-liquidez" [queryParams]="{vista:'alertas'}" (click)="cerrar()">Revisar plan</a></div><button (click)="cerrar()" [disabled]="cerrando" aria-label="Marcar alerta como leída">×</button>
  </section>`,styles:[`:host{display:block}.budget-notice{display:flex;align-items:flex-start;gap:12px;width:min(390px,calc(100vw - 32px));padding:17px;background:var(--surface);color:var(--text);border:1px solid #c99547;border-left:4px solid #c99547;border-radius:12px;box-shadow:var(--shadow-card);pointer-events:auto;box-sizing:border-box;font-size:.8rem}.budget-notice>span{color:#c99547;font-weight:900;font-size:1.25rem}.budget-notice>div{min-width:0;flex:1}.budget-notice p{margin:8px 0;line-height:1.5;overflow-wrap:anywhere}.budget-notice a{color:var(--accent)}button{border:0;background:transparent;color:var(--text);min-width:36px;min-height:36px;font:inherit;font-size:1.2rem;cursor:pointer}a:focus-visible,button:focus-visible{outline:2px solid var(--accent);outline-offset:3px}`]})
export class AhorroAlertsComponent implements OnInit {
  private http=inject(HttpClient);private destroy=inject(DestroyRef);aviso:AlertaAhorro|null=null;cerrando=false;private consultando=false;private identidad='';
  ngOnInit():void {timer(0,15000).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>this.consultar());}
  private consultar():void {
    const user=readAuthSessionValue('userId')||'';
    if(user!==this.identidad||readAuthSessionValue('isLoggedIn')!=='true'){this.aviso=null;this.identidad=user;}
    if(!user||readAuthSessionValue('isLoggedIn')!=='true'||document.hidden||this.consultando||this.cerrando)return;
    this.consultando=true;this.http.get<AlertaAhorro[]>(API_BASE_URL+'/ahorro-liquidez/alertas',{params:{nuevas:true}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.consultando=false;if(readAuthSessionValue('userId')===user&&readAuthSessionValue('isLoggedIn')==='true')this.aviso=r[0]||null;},error:()=>{this.consultando=false;this.aviso=null;}});
  }
  cerrar():void {if(!this.aviso||this.cerrando)return;this.cerrando=true;this.http.patch(API_BASE_URL+`/ahorro-liquidez/alertas/${this.aviso.id}/lectura`,{}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.cerrando=false;this.aviso=null;this.consultar();},error:()=>{this.cerrando=false;}});}
}
