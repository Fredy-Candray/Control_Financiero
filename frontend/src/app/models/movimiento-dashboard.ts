export interface MovimientoDashboard {
  id: number;
  tipo: string;
  monto: number;
  descripcion?: string;
  fechaMovimiento?: string;
  cuentaOrigen?: string;
  cuentaDestino?: string;
  categoria?: string;
}
