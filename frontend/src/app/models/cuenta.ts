export interface Cuenta {
  id: number;
  nombre: string;
  tipo: string;
  saldoActual: number;
  limiteCredito?: number;
  activa: boolean;
  fechaCreacion?: string;
}
