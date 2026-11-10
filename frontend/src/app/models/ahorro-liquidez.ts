export type Importe = string;
export interface CuentaAhorro { id:number; nombre:string; tipo:string; saldoActual:Importe; reservado:Importe; activa:boolean; }
export interface MetaAhorro { id:number; nombre:string; objetivo:Importe; reservado:Importe; acumuladoLegado:Importe|null; fechaObjetivo:string|null; aporteMensual:Importe|null; version:number; }
export interface FijoAhorro { id:number; nombre:string; monto:Importe; categoriaId:number|null; diaPago:number; inicio:string; version:number; }
export interface CompromisoAhorro { id:number; fijoId:number; nombre:string; monto:Importe; pagado:Importe; pendiente:Importe; estado:string; vencimiento:string; periodo:string; version:number; }
export interface PresupuestoAhorro { id:number; nombre:string; periodo:string; categoriaId:number|null; categoria:string|null; categoriaLegada:string|null; pendienteCategoria:boolean; limite:Importe; gastado:Importe; restante:Importe; alertaAnticipada:boolean; umbral:number; version:number; estado:string; }
export interface AlertaAhorro { id:number; nombre:string; umbral:number; gastado:Importe; limite:Importe; estado:string; periodo:string; fecha:string; }
export interface MovimientoVinculable { id:number; descripcion:string; monto:Importe; disponible:Importe; fecha:string; cuenta:string; }
export interface ReservaHistorial { id:number; tipo:string; monto:Importe; cuenta:string; fecha:string; }
export interface PagoHistorial { id:number; monto:Importe; fecha:string; anuladaEn:string|null; descripcion:string; movimientoId:number; }
export interface ResumenAhorro {
  mes:string; moneda:string; calculadoEn:string;
  dineroEnCuentas:Importe; deudaTarjetas:Importe; gastosFijosPendientes:Importe; dineroLibre:Importe; reservadoMetas:Importe; sinAsignar:Importe;
  reservasSinCoberturaPorCuenta:Importe; reservasSinCoberturaGlobal:Importe;
  cuentas:CuentaAhorro[]; metas:MetaAhorro[]; fijos:FijoAhorro[]; compromisos:CompromisoAhorro[]; presupuestos:PresupuestoAhorro[]; alertas:AlertaAhorro[]; categorias:{id:number;nombre:string}[];
}
