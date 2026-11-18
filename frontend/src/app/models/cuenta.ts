export interface Cuenta {
  fechaCorte?: string | null;
  fechaPago?: string | null;
  pendienteCiclo?: number | null;
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
