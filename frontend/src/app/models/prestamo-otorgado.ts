export interface PrestamoOtorgado {
  id: number; persona: string; monto: number; pendiente: number; fecha: string;
  vencimiento: string | null; nota: string | null; movimientoOrigenId: number | null;
  registroInicial?: boolean;
}
