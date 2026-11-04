import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { API_BASE_URL } from '../../api.config';
import { readAuthSessionValue } from '../../services/auth.service';
import { Cuenta } from '../../models/cuenta';
import { PrestamoOtorgado } from '../../models/prestamo-otorgado';

interface Aviso { id:number; gmailMessageId:string; comercio:string|null; monto:number|null; fechaCompra:string|null; estado:string; detalle:string; }
interface Revision { id:number; cuentaNombre:string; saldoCofi:number; saldoBanco:number; diferencia:number; fecha:string; nota:string|null; }
@Component({selector:'app-seguimiento',standalone:true,imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./seguimiento.component.html',styleUrl:'./seguimiento.component.css'})
export class SeguimientoComponent implements OnInit {
  tab='pendientes'; cargando=true; busy=''; error=''; mensaje=''; pagina=1;
  avisos:Aviso[]=[]; revisiones:Revision[]=[]; prestamos:PrestamoOtorgado[]=[]; cuentas:Cuenta[]=[]; movimientos:any[]=[];
  avisoId:number|null=null; accion='VINCULAR'; motivo=''; movimientoAviso:number|null=null;
  cuentaRevision:number|null=null; saldoBanco:number|null=null; notaRevision=''; refRevision=this.ref();
  modoPrestamo='INICIAL'; persona=''; movimientoPrestamo:number|null=null; cuentaPrestamo:number|null=null; montoPrestamo:number|null=null; vencimiento=''; notaPrestamo=''; refPrestamo=this.ref();
  abonoId:number|null=null; modoAbono='NUEVO'; movimientoAbono:number|null=null; cuentaAbono:number|null=null; montoAbono:number|null=null; refAbono=this.ref();
  historialId:number|null=null; historial:any[]=[];
  constructor(private http:HttpClient,private route:ActivatedRoute){}
  ngOnInit():void {const t=this.route.snapshot.queryParamMap.get('tab');if(t&&['pendientes','conciliacion','prestamos'].includes(t))this.tab=t;this.cargar();}
  private ref():string{return globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2,16)}`;}
  cargar():void {
    this.cargando=true;this.error='';const user=readAuthSessionValue('userId');
    forkJoin({avisos:this.http.get<Aviso[]>(`${API_BASE_URL}/control-financiero/pendientes`),revisiones:this.http.get<Revision[]>(`${API_BASE_URL}/control-financiero/conciliaciones`),prestamos:this.http.get<PrestamoOtorgado[]>(`${API_BASE_URL}/control-financiero/prestamos`),cuentas:this.http.get<Cuenta[]>(`${API_BASE_URL}/cuentas/usuario/${user}`),movimientos:this.http.get<any[]>(`${API_BASE_URL}/movimientos/usuario/${user}`)}).subscribe({
      next:r=>{this.avisos=r.avisos;this.revisiones=r.revisiones;this.prestamos=r.prestamos;this.cuentas=r.cuentas;this.movimientos=r.movimientos;this.cargando=false;this.pagina=Math.min(this.pagina,this.paginas);},
      error:()=>{this.cargando=false;this.error='No se pudo cargar el control de cuentas. Revisa tu sesión y vuelve a intentar.';}
    });
  }
  cambiarTab(tab:string):void {if(this.busy)return;this.tab=tab;this.pagina=1;this.error='';this.mensaje='';}
  get registros():any[]{return this.tab==='pendientes'?this.avisos:this.tab==='conciliacion'?this.revisiones:this.prestamos;}
  get paginas():number{return Math.max(1,Math.ceil(this.registros.length/6));}
  get filas():any[]{return this.registros.slice((Math.min(this.pagina,this.paginas)-1)*6,Math.min(this.pagina,this.paginas)*6);}
  get cuentasActivas():Cuenta[]{return this.cuentas.filter(c=>c.activa);}
  get cuentasDinero():Cuenta[]{return this.cuentasActivas.filter(c=>c.tipo!=='CREDITO');}
  get cuentaOrigenPrestamo():Cuenta|undefined{return this.cuentas.find(c=>c.id===this.cuentaPrestamo);}
  get gastosDisponibles():any[]{return this.movimientos.filter(m=>m.tipo==='GASTO'&&!m.prestamoOtorgadoId&&this.cuentas.some(c=>c.id===m.cuentaOrigenId&&c.tipo!=='CREDITO'));}
  get ingresosDisponibles():any[]{return this.movimientos.filter(m=>m.tipo==='INGRESO'&&!m.prestamoOtorgadoId);}
  get cuentaComparada():Cuenta|undefined{return this.cuentas.find(c=>c.id===this.cuentaRevision);}
  get diferencia():number|null{return this.saldoBanco==null||!this.cuentaComparada?null:Math.round((this.saldoBanco-Number(this.cuentaComparada.saldoActual))*100)/100;}
  get totalPrestado():number{return this.prestamos.reduce((s,p)=>s+Math.round(Number(p.pendiente)*100),0)/100;}
  get prestamosActivos():number{return this.prestamos.filter(p=>Number(p.pendiente)>0).length;}
  vencido(p:PrestamoOtorgado):boolean {const now=new Date();const hoy=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;return !!p.vencimiento&&p.vencimiento<hoy&&p.pendiente>0;}
  abrirAviso(a:Aviso):void{this.avisoId=a.id;this.movimientoAviso=null;this.motivo='';this.accion='VINCULAR';this.error='';}
  private enviar(path:string,body:unknown,operacion:string,ok:()=>void):void {
    if(this.busy)return;this.busy=operacion;this.error='';this.mensaje='';
    this.http.post(`${API_BASE_URL}/control-financiero/${path}`,body).subscribe({next:()=>{this.busy='';ok();this.cargar();},error:e=>{this.busy='';this.error=typeof e.error?.message==='string'?e.error.message:'No se pudo guardar. Revisa los datos y vuelve a intentar.';}});
  }
  resolver():void {if(this.avisoId==null)return;this.enviar(`pendientes/${this.avisoId}/resolver`,{accion:this.accion,nota:this.motivo,movimientoId:this.accion==='VINCULAR'?this.movimientoAviso:null},'aviso',()=>{this.avisoId=null;this.mensaje='Aviso atendido. No se volvió a descontar ni acreditar dinero.';});}
  conciliar():void {this.enviar('conciliaciones',{cuentaId:this.cuentaRevision,saldoBanco:this.saldoBanco,nota:this.notaRevision,referenciaCliente:this.refRevision},'revision',()=>{this.refRevision=this.ref();this.mensaje='Revisión guardada. El saldo de la cuenta permanece igual.';});}
  prestar():void {this.enviar(this.modoPrestamo==='INICIAL'?'prestamos/pendientes':'prestamos',{persona:this.persona,monto:this.modoPrestamo!=='EXISTENTE'?this.montoPrestamo:null,...(this.modoPrestamo==='INICIAL'?{}:{cuentaId:this.modoPrestamo==='NUEVO'?this.cuentaPrestamo:null,movimientoId:this.modoPrestamo==='EXISTENTE'?this.movimientoPrestamo:null}),vencimiento:this.vencimiento||null,nota:this.notaPrestamo,referenciaCliente:this.refPrestamo},'prestamo',()=>{this.refPrestamo=this.ref();this.persona='';this.movimientoPrestamo=null;this.montoPrestamo=null;this.vencimiento='';this.notaPrestamo='';this.mensaje=this.modoPrestamo==='INICIAL'?'Saldo pendiente registrado sin cambiar ninguna cuenta. Ya puedes registrar devoluciones.':'Préstamo en seguimiento. El capital no se cuenta como gasto.';});}
  abrirAbono(p:PrestamoOtorgado):void{this.abonoId=p.id;this.modoAbono='NUEVO';this.movimientoAbono=null;this.cuentaAbono=null;this.montoAbono=null;this.refAbono=this.ref();this.error='';}
  cobrar():void {if(this.abonoId==null)return;this.enviar(`prestamos/${this.abonoId}/abonos`,{monto:this.modoAbono==='NUEVO'?this.montoAbono:null,cuentaId:this.modoAbono==='NUEVO'?this.cuentaAbono:null,movimientoId:this.modoAbono==='EXISTENTE'?this.movimientoAbono:null,referenciaCliente:this.refAbono},'abono',()=>{this.abonoId=null;this.historialId=null;this.mensaje='Devolución registrada. El capital devuelto no se cuenta como ingreso nuevo.';});}
  verHistorial(p:PrestamoOtorgado):void {if(this.busy)return;this.busy='historial';this.http.get<any[]>(`${API_BASE_URL}/control-financiero/prestamos/${p.id}/movimientos`).subscribe({next:r=>{this.historialId=p.id;this.historial=r;this.busy='';},error:()=>{this.busy='';this.error='No se pudo cargar el historial del préstamo.';}});}
}
