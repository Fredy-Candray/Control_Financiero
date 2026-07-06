export interface Cuenta {
  id: number;
  nombre: string;
  tipo: string;
  saldoActual: number;
  activa: boolean;
  fechaCreacion?: string;
}
