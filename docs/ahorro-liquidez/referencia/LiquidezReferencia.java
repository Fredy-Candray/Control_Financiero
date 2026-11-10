package cofi.diseno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Executable specification only: no Spring wiring, SQL or network access. */
public final class LiquidezReferencia {
    private static final BigDecimal CERO = new BigDecimal("0.00");
    public enum TipoCuenta { EFECTIVO, DEBITO, CREDITO }
    public enum EstadoPresupuesto { NORMAL, AVISO, AL_LIMITE, EXCEDIDO }
    public record Cuenta(long id, TipoCuenta tipo, BigDecimal saldo, BigDecimal limite, BigDecimal deudaLegada) {}
    public record Compromiso(BigDecimal monto, BigDecimal pagado, LocalDate vencimiento, boolean cancelado) {}
    /** Already aggregated from the immutable reservation ledger. */
    public record Reserva(long cuentaId, BigDecimal montoNeto) {}
    public record Resumen(BigDecimal dineroEnCuentas, BigDecimal deudaTarjetas,
            BigDecimal fijosPendientes, BigDecimal dineroLibre, BigDecimal reservado,
            BigDecimal sinAsignar, BigDecimal reservasSinCoberturaPorCuenta,
            BigDecimal reservasSinCoberturaGlobal) {}

    public static Resumen resumir(List<Cuenta> cuentas, List<Compromiso> compromisos,
            List<Reserva> reservas, LocalDate horizonte) {
        Objects.requireNonNull(horizonte);
        BigDecimal dinero=CERO, deuda=CERO, fijos=CERO, reservado=CERO, deficitCuenta=CERO;
        Map<Long,Cuenta> porId=new HashMap<>();
        for(Cuenta c:cuentas){
            if(porId.put(c.id(),c)!=null)throw new IllegalArgumentException("Cuenta duplicada");
            Objects.requireNonNull(c.tipo());
            if(c.tipo()!=TipoCuenta.CREDITO)dinero=dinero.add(moneda(c.saldo()));
            else if(c.limite()!=null)deuda=deuda.add(positivo(c.limite()).subtract(moneda(c.saldo())).max(CERO));
            else if(c.deudaLegada()!=null)deuda=deuda.add(positivo(c.deudaLegada()));
            else throw new IllegalArgumentException("Resumen incompleto: falta deuda de una tarjeta");
        }
        for(Compromiso c:compromisos){
            BigDecimal importe=positivo(c.monto()),pagado=positivo(c.pagado());
            if(pagado.compareTo(importe)>0)throw new IllegalArgumentException("Pago superior al compromiso");
            if(!c.cancelado()&&!c.vencimiento().isAfter(horizonte))fijos=fijos.add(importe.subtract(pagado));
        }
        Map<Long,BigDecimal> porCuenta=new HashMap<>();
        for(Reserva r:reservas){
            Cuenta c=porId.get(r.cuentaId());
            if(c==null||c.tipo()==TipoCuenta.CREDITO)throw new IllegalArgumentException("Reserva sin cuenta de dinero válida");
            BigDecimal monto=positivo(r.montoNeto());reservado=reservado.add(monto);
            porCuenta.merge(r.cuentaId(),monto,BigDecimal::add);
        }
        for(var entry:porCuenta.entrySet())deficitCuenta=deficitCuenta.add(entry.getValue().subtract(moneda(porId.get(entry.getKey()).saldo()).max(CERO)).max(CERO));
        BigDecimal libre=dinero.subtract(deuda).subtract(fijos);
        return new Resumen(dinero,deuda,fijos,libre,reservado,libre.subtract(reservado),
                deficitCuenta,reservado.subtract(libre.max(CERO)).max(CERO));
    }

    public static void validarReserva(Resumen resumen, Cuenta cuenta, BigDecimal reservadoEnCuenta,
            BigDecimal reservadoEnMeta, BigDecimal objetivo, BigDecimal nuevoMonto) {
        if(cuenta.tipo()==TipoCuenta.CREDITO)throw new IllegalArgumentException("No se reserva crédito");
        BigDecimal monto=positivo(nuevoMonto);
        if(monto.signum()==0)throw new IllegalArgumentException("Importe cero");
        if(monto.compareTo(moneda(cuenta.saldo()).subtract(positivo(reservadoEnCuenta)))>0)
            throw new IllegalArgumentException("No hay saldo sin reservar en esa cuenta");
        if(monto.compareTo(resumen.sinAsignar())>0)throw new IllegalArgumentException("No hay liquidez sin asignar");
        if(positivo(reservadoEnMeta).add(monto).compareTo(positivo(objetivo))>0)
            throw new IllegalArgumentException("Supera el objetivo");
        // Active status, currency, ownership, idempotency and locking belong to the service.
    }

    public static BigDecimal avance(BigDecimal reservado, BigDecimal objetivo){
        if(positivo(objetivo).signum()==0)throw new IllegalArgumentException("Objetivo cero");
        return positivo(reservado).multiply(BigDecimal.valueOf(100)).divide(objetivo,2,RoundingMode.HALF_UP).min(new BigDecimal("100.00"));
    }
    public static EstadoPresupuesto estadoPresupuesto(BigDecimal gastado, BigDecimal limite, Integer umbral){
        gastado=positivo(gastado);limite=positivo(limite);
        if(limite.signum()==0)throw new IllegalArgumentException("Límite cero");
        if(umbral!=null&&(umbral<1||umbral>99))throw new IllegalArgumentException("Umbral inválido");
        int comparacion=gastado.compareTo(limite);
        if(comparacion>0)return EstadoPresupuesto.EXCEDIDO;
        if(comparacion==0)return EstadoPresupuesto.AL_LIMITE;
        if(umbral!=null&&gastado.multiply(BigDecimal.valueOf(100)).compareTo(limite.multiply(BigDecimal.valueOf(umbral)))>=0)return EstadoPresupuesto.AVISO;
        return EstadoPresupuesto.NORMAL;
    }
    private static BigDecimal moneda(BigDecimal value){return Objects.requireNonNull(value).setScale(2,RoundingMode.UNNECESSARY);}
    private static BigDecimal positivo(BigDecimal value){BigDecimal r=moneda(value);if(r.signum()<0)throw new IllegalArgumentException("Importe negativo");return r;}
}
