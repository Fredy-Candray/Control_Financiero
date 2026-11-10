import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { API_BASE_URL } from '../../api.config';
import { ResumenAhorro, MetaAhorro, FijoAhorro, PresupuestoAhorro, CompromisoAhorro, MovimientoVinculable, ReservaHistorial, PagoHistorial } from '../../models/ahorro-liquidez';

type Vista='resumen'|'metas'|'fijos'|'presupuestos'|'alertas';
type TipoEditor='META'|'RESERVA'|'LIBERACION'|'FIJO'|'PRESUPUESTO'|'PAGO'|'CANCELAR'|'ANULAR'|'AJUSTAR';
interface Editor { tipo:TipoEditor; id?:number; version:number|null; nombre:string; monto:string|number|null; fecha:string; aporte:string|number|null; categoriaId:number|'TODAS'|null; diaPago:number; inicio:string; alertaAnticipada:boolean; umbral:number; cuentaId:number|null; movimientoId:number|null; referenciaCliente:string; nota:string; pagoId?:number; }

@Component({selector:'app-ahorro-liquidez',standalone:true,imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./ahorro-liquidez.component.html',styleUrl:'./ahorro-liquidez.component.css'})
export class AhorroLiquidezComponent implements OnInit {
  private http=inject(HttpClient);private route=inject(ActivatedRoute);private router=inject(Router);private destroy=inject(DestroyRef);
  readonly base=API_BASE_URL+'/ahorro-liquidez';
  readonly tabs:{id:Vista;nombre:string}[]=[{id:'resumen',nombre:'Resumen'},{id:'metas',nombre:'Metas de ahorro'},{id:'fijos',nombre:'Gastos fijos'},{id:'presupuestos',nombre:'Presupuestos'},{id:'alertas',nombre:'Alertas'}];
  mes=this.mesActual();vista:Vista='resumen';datos:ResumenAhorro|null=null;error='';mensaje='';cargando=true;consultando=false;guardando=false;pagina=1;
  editor:Editor|null=null;movimientos:MovimientoVinculable[]=[];cargandoMovimientos=false;
  historialMeta:number|null=null;historial:ReservaHistorial[]=[];historialPagina=0;historialTotal=0;cargandoHistorial=false;
  compromisoHistorial:CompromisoAhorro|null=null;pagos:PagoHistorial[]=[];
  ngOnInit():void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroy)).subscribe(p=>{const vista=p.get('vista');this.vista=this.tabs.some(t=>t.id===vista)?vista as Vista:'resumen';this.pagina=1;});
    timer(0,15000).pipe(takeUntilDestroyed(this.destroy)).subscribe(()=>{if(!document.hidden&&!this.editor&&!this.guardando)this.cargar(false);});
    const focus=()=>{if(!this.editor&&!this.guardando)this.cargar(false);};window.addEventListener('focus',focus);this.destroy.onDestroy(()=>window.removeEventListener('focus',focus));
  }
  private mesActual():string {const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;}
  numero(v:string|number|null|undefined):number {const n=Number(v);return Number.isFinite(n)?n:0;}
  porcentaje(monto:string|number,objetivo:string|number):number {return this.numero(objetivo)>0?Math.round(this.numero(monto)/this.numero(objetivo)*1000)/10:0;}
  barra(monto:string|number,objetivo:string|number):number {return Math.max(0,Math.min(100,this.porcentaje(monto,objetivo)));}
  get sinCobertura():boolean {return !!this.datos&&(this.numero(this.datos.reservasSinCoberturaPorCuenta)>0||this.numero(this.datos.reservasSinCoberturaGlobal)>0);}
  get nuevas():number{return this.datos?.alertas.filter(a=>a.estado==='NUEVA').length||0;}
  get cuentasDinero(){return this.datos?.cuentas.filter(c=>c.tipo!=='CREDITO'&&(this.editor?.tipo==='LIBERACION'||c.activa))||[];}
  get registros():unknown[]{if(!this.datos)return [];return this.vista==='metas'?this.datos.metas:this.vista==='fijos'?this.datos.compromisos:this.vista==='presupuestos'?this.datos.presupuestos:this.vista==='alertas'?this.datos.alertas:[];}
  get paginas():number{return Math.max(1,Math.ceil(this.registros.length/6));}
  get metasPagina(){return this.datos?.metas.slice((this.pagina-1)*6,this.pagina*6)||[];}
  get fijosPagina(){return this.datos?.compromisos.slice((this.pagina-1)*6,this.pagina*6)||[];}
  get presupuestosPagina(){return this.datos?.presupuestos.slice((this.pagina-1)*6,this.pagina*6)||[];}
  get alertasPagina(){return this.datos?.alertas.slice((this.pagina-1)*6,this.pagina*6)||[];}
  get tituloEditor():string {return !this.editor?'':({META:this.editor.id?'Editar meta':'Nueva meta',RESERVA:'Reservar fondos',LIBERACION:'Liberar fondos',FIJO:this.editor.id?'Editar plantilla mensual':'Nuevo gasto fijo',PRESUPUESTO:this.editor.id?'Editar presupuesto':'Nuevo presupuesto',PAGO:'Vincular gasto ya registrado',CANCELAR:'Cancelar compromiso',ANULAR:'Anular vínculo de pago',AJUSTAR:'Ajustar importe acordado'})[this.editor.tipo];}
  seleccionar(vista:Vista):void {if(this.guardando)return;this.editor=null;this.error='';this.pagina=1;void this.router.navigate([],{relativeTo:this.route,queryParams:{vista},queryParamsHandling:'merge'});}
  cambiarMes():void {this.pagina=1;this.cargar(true);}
  cargar(mostrar=true):void {
    if(this.consultando||this.guardando||!/^\d{4}-(0[1-9]|1[0-2])$/.test(this.mes))return;
    this.consultando=true;if(mostrar&&!this.datos)this.cargando=true;
    this.http.get<ResumenAhorro>(this.base+'/resumen',{params:{mes:this.mes}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.datos=r;this.cargando=false;this.consultando=false;this.error='';this.pagina=Math.min(this.pagina,this.paginas);},error:e=>{this.cargando=false;this.consultando=false;this.error=this.errorTexto(e);}});
  }
  private errorTexto(e:HttpErrorResponse):string {return e.status===401?'Tu sesión expiró. Vuelve a iniciar sesión.':e.error?.message||e.error?.detail||(e.status===409?'El registro cambió. Actualiza y abre el formulario de nuevo.':'No se pudo completar la operación. Revisa la conexión local y vuelve a intentar.');}
  private ref():string{return globalThis.crypto?.randomUUID?.()||'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.floor(Math.random()*16);return(c==='x'?r:(r&3)|8).toString(16);});}
  abrir(tipo:TipoEditor,registro?:MetaAhorro|FijoAhorro|PresupuestoAhorro|CompromisoAhorro):void {
    if(this.guardando)return;this.error='';this.mensaje='';
    this.editor={tipo,id:registro?.id,version:registro?.version??null,nombre:registro?.nombre||'',monto:null,fecha:'',aporte:null,categoriaId:tipo==='PRESUPUESTO'?'TODAS':null,diaPago:1,inicio:tipo==='PRESUPUESTO'?this.mes:this.mesActual(),alertaAnticipada:true,umbral:80,cuentaId:null,movimientoId:null,referenciaCliente:this.ref(),nota:''};
    if(registro&&tipo==='META'){const r=registro as MetaAhorro;Object.assign(this.editor,{monto:r.objetivo,fecha:r.fechaObjetivo||'',aporte:r.aporteMensual});}
    if(registro&&tipo==='FIJO'){const r=registro as FijoAhorro;Object.assign(this.editor,{monto:r.monto,categoriaId:r.categoriaId,diaPago:r.diaPago,inicio:r.inicio.slice(0,7)});}
    if(registro&&tipo==='PRESUPUESTO'){const r=registro as PresupuestoAhorro;Object.assign(this.editor,{monto:r.limite,categoriaId:r.pendienteCategoria?null:r.categoriaId??'TODAS',inicio:r.periodo.slice(0,7),alertaAnticipada:r.alertaAnticipada,umbral:r.umbral});}
    if(registro&&(tipo==='PAGO'||tipo==='AJUSTAR'))this.editor.monto=tipo==='PAGO'?(registro as CompromisoAhorro).pendiente:(registro as CompromisoAhorro).monto;
    if(tipo==='PAGO'){this.movimientos=[];this.cargandoMovimientos=true;this.http.get<MovimientoVinculable[]>(this.base+'/movimientos-disponibles').pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.movimientos=r;this.cargandoMovimientos=false;},error:e=>{this.error=this.errorTexto(e);this.cargandoMovimientos=false;}});}
  }
  guardar():void {
    if(!this.editor||this.guardando)return;const e=this.editor;const base={version:e.version,referenciaCliente:e.referenciaCliente};let ruta='',metodo:'post'|'put'|'delete'='post',body:object={};
    if(e.tipo==='META'){ruta='/metas'+(e.id?'/'+e.id:'');metodo=e.id?'put':'post';body={...base,nombre:e.nombre,objetivo:e.monto,fechaObjetivo:e.fecha||null,aporteMensual:e.aporte||null};}
    if(e.tipo==='RESERVA'||e.tipo==='LIBERACION'){ruta=`/metas/${e.id}/${e.tipo==='RESERVA'?'reservas':'liberaciones'}`;body={...base,cuentaId:e.cuentaId,monto:e.monto};}
    if(e.tipo==='FIJO'){ruta='/fijos'+(e.id?'/'+e.id:'');metodo=e.id?'put':'post';body={...base,nombre:e.nombre,monto:e.monto,categoriaId:e.categoriaId,diaPago:e.diaPago,inicio:e.inicio};}
    if(e.tipo==='PRESUPUESTO'){ruta='/presupuestos'+(e.id?'/'+e.id:'');metodo=e.id?'put':'post';body={...base,nombre:e.nombre,mes:e.inicio,categoriaId:e.categoriaId==='TODAS'?null:e.categoriaId,limite:e.monto,alertaAnticipada:e.alertaAnticipada,umbral:e.umbral};}
    if(e.tipo==='PAGO'){ruta=`/compromisos/${e.id}/pagos`;body={...base,movimientoId:e.movimientoId,monto:e.monto};}
    if(e.tipo==='CANCELAR'){ruta=`/compromisos/${e.id}/cancelacion`;body={version:e.version,nota:e.nota};}
    if(e.tipo==='ANULAR'){ruta=`/compromisos/${e.id}/pagos/${e.pagoId}`;metodo='delete';body={version:e.version,nota:e.nota};}
    if(e.tipo==='AJUSTAR'){ruta=`/compromisos/${e.id}`;metodo='put';body={version:e.version,monto:e.monto,nota:e.nota};}
    this.escribir(metodo,ruta,body,'Guardado. Los saldos reales de tus cuentas no se han modificado.');
  }
  private escribir(metodo:string,ruta:string,body:object,mensaje:string):void {
    if(this.guardando)return;this.guardando=true;this.error='';
    this.http.request(metodo,this.base+ruta,{body}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.guardando=false;this.editor=null;this.compromisoHistorial=null;this.mensaje=mensaje;this.cargar(false);},error:e=>{this.guardando=false;this.error=this.errorTexto(e);}});
  }
  archivar(tipo:'metas'|'fijos'|'presupuestos',r:{id:number;version:number;nombre:string}):void {
    if(this.guardando||!confirm(`¿Archivar «${r.nombre}»? ${tipo==='fijos'?'Los compromisos ya generados se conservan.':''}`))return;
    this.escribir('delete',`/${tipo}/${r.id}?version=${r.version}`,{},'Registro archivado. Se conservó su historial.');
  }
  verHistorial(meta:MetaAhorro,pagina=0):void {
    if(this.cargandoHistorial)return;this.historialMeta=meta.id;this.historialPagina=pagina;this.cargandoHistorial=true;
    this.http.get<{total:number;filas:ReservaHistorial[]}>(`${this.base}/metas/${meta.id}/reservas`,{params:{pagina}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>{this.historial=r.filas;this.historialTotal=r.total;this.cargandoHistorial=false;},error:e=>{this.error=this.errorTexto(e);this.cargandoHistorial=false;}});
  }
  paginaHistorial(delta:number):void {const m=this.datos?.metas.find(m=>m.id===this.historialMeta);if(m)this.verHistorial(m,this.historialPagina+delta);}
  verPagos(c:CompromisoAhorro):void {this.compromisoHistorial=c;this.pagos=[];this.http.get<PagoHistorial[]>(`${this.base}/compromisos/${c.id}/pagos`).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>this.pagos=r,error:e=>this.error=this.errorTexto(e)});}
  anular(p:PagoHistorial):void {if(!this.compromisoHistorial)return;this.abrir('ANULAR',this.compromisoHistorial);this.editor!.pagoId=p.id;}
  leer(id:number):void {if(this.guardando)return;this.escribir('patch',`/alertas/${id}/lectura`,{},'Alerta marcada como leída.');}
}
