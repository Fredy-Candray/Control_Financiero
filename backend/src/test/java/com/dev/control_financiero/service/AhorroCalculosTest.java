package com.dev.control_financiero.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static com.dev.control_financiero.service.AhorroCalculos.*;

/** Deterministic local checks. No live database, mailbox, API or user data. */
public final class AhorroCalculosTest {
    private static int comprobaciones;
    private static BigDecimal m(String s){return new BigDecimal(s);}
    private static final LocalDate FIN=LocalDate.of(2026,11,30);
    private static Cuenta efectivo(String saldo){return new Cuenta(1,TipoCuenta.EFECTIVO,m(saldo),null,null);}
    private static Cuenta tarjeta(String disponible){return new Cuenta(2,TipoCuenta.CREDITO,m(disponible),m("1500"),null);}
    private static Compromiso fijo(String importe,String pagado){return new Compromiso(m(importe),m(pagado),FIN,false);}
    private static void igual(String esperado,BigDecimal real){comprobaciones++;if(m(esperado).compareTo(real)!=0)throw new AssertionError(esperado+" != "+real);}
    private static void condicion(boolean ok){comprobaciones++;if(!ok)throw new AssertionError();}
    private static void rechaza(Runnable work){comprobaciones++;try{work.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("Debía rechazar");}
    @org.junit.jupiter.api.Test void reglasFinancieras(){
        var resumen=resumir(List.of(efectivo("1800"),tarjeta("1050")),List.of(fijo("300","0")),List.of(new Reserva(1,m("600"))),FIN);
        igual("1800",resumen.dineroEnCuentas());igual("450",resumen.deudaTarjetas());igual("300",resumen.fijosPendientes());igual("1050",resumen.dineroLibre());igual("450",resumen.sinAsignar());
        var antes=resumir(List.of(efectivo("1000"),tarjeta("1500")),List.of(fijo("100","0")),List.of(),FIN);
        var debito=resumir(List.of(efectivo("900"),tarjeta("1500")),List.of(fijo("100","100")),List.of(),FIN);
        var credito=resumir(List.of(efectivo("1000"),tarjeta("1400")),List.of(fijo("100","100")),List.of(),FIN);
        var pagarTarjeta=resumir(List.of(efectivo("900"),tarjeta("1500")),List.of(fijo("100","100")),List.of(),FIN);
        igual("900",antes.dineroLibre());igual("900",debito.dineroLibre());igual("900",credito.dineroLibre());igual("900",pagarTarjeta.dineroLibre());
        igual("60",resumir(List.of(efectivo("1000")),List.of(fijo("100","40")),List.of(),FIN).fijosPendientes());
        var horizonte=resumir(List.of(efectivo("1000")),List.of(new Compromiso(m("100"),m("0"),FIN.minusMonths(1),false),new Compromiso(m("200"),m("0"),FIN.plusDays(1),false),new Compromiso(m("50"),m("0"),FIN,true)),List.of(),FIN);
        igual("100",horizonte.fijosPendientes());
        var deficit=resumir(List.of(efectivo("100"),tarjeta("1200")),List.of(fijo("50","0")),List.of(new Reserva(1,m("200"))),FIN);
        igual("-250",deficit.dineroLibre());igual("-450",deficit.sinAsignar());igual("100",deficit.reservasSinCoberturaPorCuenta());igual("200",deficit.reservasSinCoberturaGlobal());
        igual("50",avance(m("500"),m("1000")));igual("100",avance(m("1200"),m("1000")));
        condicion(estadoPresupuesto(m("79.99"),m("100"),80)==EstadoPresupuesto.NORMAL);
        condicion(estadoPresupuesto(m("80"),m("100"),80)==EstadoPresupuesto.AVISO);
        condicion(estadoPresupuesto(m("100"),m("100"),80)==EstadoPresupuesto.AL_LIMITE);
        condicion(estadoPresupuesto(m("100.01"),m("100"),80)==EstadoPresupuesto.EXCEDIDO);
        condicion(estadoPresupuesto(m("90"),m("100"),null)==EstadoPresupuesto.NORMAL);
        rechaza(()->estadoPresupuesto(m("0"),m("0"),80));rechaza(()->estadoPresupuesto(m("10"),m("100"),100));
        rechaza(()->resumir(List.of(new Cuenta(3,TipoCuenta.CREDITO,m("0"),null,null)),List.of(),List.of(),FIN));
        igual("25",resumir(List.of(new Cuenta(3,TipoCuenta.CREDITO,m("0"),null,m("25"))),List.of(),List.of(),FIN).deudaTarjetas());
        rechaza(()->validarReserva(resumen,efectivo("1800"),m("600"),m("600"),m("1000"),m("451")));
        rechaza(()->validarReserva(resumen,efectivo("700"),m("600"),m("600"),m("1000"),m("101")));
        rechaza(()->validarReserva(resumen,efectivo("1800"),m("600"),m("600"),m("650"),m("51")));
        rechaza(()->validarReserva(resumen,tarjeta("1500"),m("0"),m("0"),m("1000"),m("1")));
        rechaza(()->resumir(List.of(efectivo("0.001")),List.of(),List.of(),FIN));
        validarReserva(resumen,efectivo("1800"),m("600"),m("600"),m("1000"),m("100"));comprobaciones++;
        System.out.println(comprobaciones+" comprobaciones de referencia correctas; sin datos reales ni conexiones.");
    }
}
