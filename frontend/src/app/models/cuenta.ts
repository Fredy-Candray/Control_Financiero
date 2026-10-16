export interface Cuenta {
  id: number;
  nombre: string;
  banco?: string | null;
  ultimosCuatro?: string | null;
  moneda?: string | null;
  tipo: string;
  saldoActual: number;
  limiteCredito?: number;
  activa: boolean;
  fechaCreacion?: string;
}
