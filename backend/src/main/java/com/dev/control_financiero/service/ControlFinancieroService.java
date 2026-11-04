package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.dto.MovimientoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class ControlFinancieroService {
    private final BankEmailImportRepository avisos;
    private final MovimientoRepository movimientos;
    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final PrestamoOtorgadoRepository prestamos;
    private final ConciliacionSaldoRepository conciliaciones;
    private static final ZoneId ZONE=ZoneId.of("America/El_Salvador");
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro no encontrado.");}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalArgumentException(text);}
    private static void money(BigDecimal amount){require(amount!=null&&amount.signum()>0&&amount.scale()<=2&&amount.precision()<=18,"Indica un monto positivo con hasta dos decimales.");}
    private Cuenta cuenta(Long user,Long id) {
        if(id==null)throw new IllegalArgumentException("Selecciona una cuenta.");
        var c=cuentas.findLockedById(id).orElseThrow(ControlFinancieroService::missing);
        if(!c.getUsuario().getId().equals(user))throw missing();
        require(Boolean.TRUE.equals(c.getActiva()),"La cuenta está inactiva.");return c;
    }
    @Transactional(readOnly=true) public List<BankEmailImport> pendientes(Long user){return avisos.findByUsuarioIdAndEstadoInOrderByIdDesc(user,List.of("PENDIENTE","ERROR"));}
    @Transactional public BankEmailImport resolver(Long user,Long id,String accion,String nota,Long movimientoId) {
        var a=avisos.findByIdAndUsuarioId(id,user).orElseThrow(ControlFinancieroService::missing);
        require(List.of("VINCULAR","IGNORAR").contains(accion),"Acción no válida.");
        require(nota!=null&&!nota.isBlank()&&nota.length()<=255,"Indica por qué resuelves este aviso.");
        if(!List.of("PENDIENTE","ERROR").contains(a.getEstado())) {
            boolean same=a.getRevisionNota()!=null&&a.getRevisionNota().equals(nota.trim())
                && ("IGNORAR".equals(accion)?"IGNORADO".equals(a.getEstado()):"IMPORTADO".equals(a.getEstado())&&Objects.equals(a.getMovimientoId(),movimientoId));
            if(same)return a;
            throw new ResponseStatusException(HttpStatus.CONFLICT,"El aviso ya fue atendido. Actualiza la lista.");
        }
        if("VINCULAR".equals(accion)) {
            if(movimientoId==null)throw new IllegalArgumentException("Selecciona el movimiento ya registrado.");
            var m=movimientos.findByIdAndUsuario_Id(movimientoId,user).orElseThrow(ControlFinancieroService::missing);
            require(a.getMonto()==null||a.getMonto().compareTo(m.getMonto())==0,"El monto del aviso no coincide con el movimiento.");
            a.setMovimientoId(m.getId());a.setEstado("IMPORTADO");
        } else {require(movimientoId==null,"No selecciones movimiento para descartar un aviso.");a.setEstado("IGNORADO");}
        a.setRevisionNota(nota.trim());a.setRevisadoEn(Instant.now());return avisos.save(a);
    }
    @Transactional(readOnly=true) public List<ConciliacionSaldo> conciliaciones(Long user){return conciliaciones.findTop50ByUsuarioIdOrderByIdDesc(user);}
    @Transactional public ConciliacionSaldo conciliar(Long user,Long cuentaId,BigDecimal banco,String nota,String ref) {
        require(banco!=null&&banco.scale()<=2&&banco.precision()<=18,"Indica un saldo válido con hasta dos decimales.");
        usuarios.findLockedById(user).orElseThrow(ControlFinancieroService::missing);
        var prior=conciliaciones.findByUsuarioIdAndReferenciaCliente(user,ref);
        if(prior.isPresent()){require(prior.get().getCuentaId().equals(cuentaId)&&prior.get().getSaldoBanco().compareTo(banco)==0,"La referencia ya pertenece a otra revisión.");return prior.get();}
        var account=cuenta(user,cuentaId);
        var row=new ConciliacionSaldo();row.setUsuarioId(user);row.setCuentaId(cuentaId);row.setCuentaNombre(account.getNombre());
        row.setSaldoCofi(account.getSaldoActual());row.setSaldoBanco(banco);row.setDiferencia(banco.subtract(account.getSaldoActual()));
        row.setNota(nota);row.setFecha(Instant.now());row.setReferenciaCliente(ref);
        return conciliaciones.save(row); // Observation only: never adjusts accounts or creates movements.
    }
    @Transactional(readOnly=true) public List<PrestamoOtorgado> prestamos(Long user){return prestamos.findByUsuarioIdOrderByFechaDesc(user);}
    @Transactional public PrestamoOtorgado registrarPendiente(Long user,String persona,BigDecimal monto,LocalDate vencimiento,String nota,String ref) {
        require(persona!=null&&!persona.isBlank()&&persona.trim().length()<=120,"Indica quién te debe el dinero.");money(monto);
        usuarios.findLockedById(user).orElseThrow(ControlFinancieroService::missing);
        var prior=prestamos.findByUsuarioIdAndReferenciaCliente(user,ref);
        if(prior.isPresent()) {
            var old=prior.get();require(old.isRegistroInicial()&&old.getPersona().equals(persona.trim())&&old.getMonto().compareTo(monto)==0,"La referencia ya pertenece a otro préstamo.");return old;
        }
        var loan=new PrestamoOtorgado();loan.setUsuarioId(user);loan.setPersona(persona.trim());loan.setMonto(monto);loan.setPendiente(monto);
        loan.setFecha(LocalDateTime.now(ZONE));loan.setVencimiento(vencimiento);loan.setNota(nota);loan.setReferenciaCliente(ref);loan.setRegistroInicial(true);
        return prestamos.save(loan); // Opening receivable only; no account, expense, income or past transaction is created.
    }
    @Transactional(readOnly=true) public List<MovimientoResponse> historialPrestamo(Long user,Long id) {
        if(prestamos.findByIdAndUsuarioId(id,user).isEmpty())throw missing();
        return movimientos.findByUsuarioId(user).stream().filter(m->Objects.equals(m.getPrestamoOtorgadoId(),id))
            .sorted(Comparator.comparing(Movimiento::getFechaMovimiento).reversed()).map(MovimientoResponse::from).toList();
    }
    @Transactional public PrestamoOtorgado prestar(Long user,String persona,BigDecimal monto,Long cuentaId,Long movimientoId,LocalDate vencimiento,String nota,String ref) {
        require(persona!=null&&!persona.isBlank()&&persona.trim().length()<=120,"Indica a quién prestaste el dinero.");
        var owner=usuarios.findLockedById(user).orElseThrow(ControlFinancieroService::missing);
        var prior=prestamos.findByUsuarioIdAndReferenciaCliente(user,ref);
        if(prior.isPresent()) {
            var old=prior.get();require(!old.isRegistroInicial()&&old.getPersona().equals(persona.trim())&&(monto==null||old.getMonto().compareTo(monto)==0)
                && (movimientoId==null||Objects.equals(old.getMovimientoOrigenId(),movimientoId)),"La referencia ya pertenece a otro préstamo.");return old;
        }
        Movimiento movement;LocalDateTime fecha;
        if(movimientoId!=null) {
            require(cuentaId==null,"Elige un movimiento existente o una nueva salida, no ambos.");
            movement=movimientos.findByIdAndUsuario_Id(movimientoId,user).orElseThrow(ControlFinancieroService::missing);
            require(movement.getTipo()==TipoMovimiento.GASTO&&movement.getPrestamoOtorgadoId()==null,"Este movimiento no es un gasto disponible para vincular.");
            require(movement.getCuentaOrigen()!=null&&movement.getCuentaOrigen().getTipo()!=TipoCuenta.CREDITO,"El préstamo debe salir de efectivo o débito.");
            if(monto!=null)require(monto.compareTo(movement.getMonto())==0,"El monto debe coincidir con el movimiento.");
            monto=movement.getMonto();fecha=movement.getFechaMovimiento();
        } else {
            money(monto);var account=cuenta(user,cuentaId);
            require(account.getTipo()!=TipoCuenta.CREDITO,"Usa una cuenta de efectivo o débito.");
            require(account.getSaldoActual().compareTo(monto)>=0,"Saldo insuficiente.");
            fecha=LocalDateTime.now(ZONE);
            movement=Movimiento.builder().usuario(owner).tipo(TipoMovimiento.PRESTAMO_OTORGADO).monto(monto).cuentaOrigen(account)
                .fechaMovimiento(fecha).destinatarioExterno(persona.trim()).referenciaCliente(ref)
                .descripcion(("Pay".equalsIgnoreCase(account.getNombre())?"PAY a ":"Préstamo a ")+persona.trim()).build();
            account.setSaldoActual(account.getSaldoActual().subtract(monto));cuentas.save(account);
        }
        money(monto);require(vencimiento==null||!vencimiento.isBefore(fecha.toLocalDate()),"La fecha de devolución no puede ser anterior al préstamo.");
        var loan=new PrestamoOtorgado();loan.setUsuarioId(user);loan.setPersona(persona.trim());loan.setMonto(monto);loan.setPendiente(monto);
        loan.setFecha(fecha);loan.setVencimiento(vencimiento);loan.setNota(nota);loan.setReferenciaCliente(ref);loan=prestamos.save(loan);
        movement.setTipo(TipoMovimiento.PRESTAMO_OTORGADO);movement.setPrestamoOtorgadoId(loan.getId());
        movement=movimientos.save(movement);loan.setMovimientoOrigenId(movement.getId());return prestamos.save(loan);
    }
    @Transactional public PrestamoOtorgado cobrar(Long user,Long id,BigDecimal monto,Long cuentaId,Long movimientoId,String ref) {
        var owner=usuarios.findLockedById(user).orElseThrow(ControlFinancieroService::missing);
        var loan=prestamos.findByIdAndUsuarioId(id,user).orElseThrow(ControlFinancieroService::missing);
        var prior=movimientos.findByUsuario_IdAndReferenciaCliente(user,ref);
        if(prior.isPresent()) {
            var old=prior.get();require(old.getTipo()==TipoMovimiento.COBRO_PRESTAMO&&Objects.equals(old.getPrestamoOtorgadoId(),id)
                && (monto==null||old.getMonto().compareTo(monto)==0),"La referencia ya pertenece a otro movimiento.");return loan;
        }
        Movimiento movement;
        if(movimientoId!=null) {
            require(cuentaId==null,"Elige una entrada existente o una cuenta para recibir, no ambas.");
            movement=movimientos.findByIdAndUsuario_Id(movimientoId,user).orElseThrow(ControlFinancieroService::missing);
            if(movement.getTipo()==TipoMovimiento.COBRO_PRESTAMO&&Objects.equals(movement.getPrestamoOtorgadoId(),id))return loan;
            require(movement.getTipo()==TipoMovimiento.INGRESO&&movement.getPrestamoOtorgadoId()==null,"Selecciona un ingreso todavía no vinculado.");
            require(movement.getCuentaOrigen()!=null&&movement.getCuentaOrigen().getTipo()!=TipoCuenta.CREDITO,"La devolución debe entrar a efectivo o débito.");
            require(!movement.getFechaMovimiento().isBefore(loan.getFecha()),"La devolución no puede ser anterior al préstamo.");
            if(monto!=null)require(monto.compareTo(movement.getMonto())==0,"El monto debe coincidir con el ingreso.");
            monto=movement.getMonto();
        } else {
            money(monto);var account=cuenta(user,cuentaId);
            require(account.getTipo()!=TipoCuenta.CREDITO,"Usa una cuenta de efectivo o débito.");
            movement=Movimiento.builder().usuario(owner).cuentaOrigen(account).monto(monto).fechaMovimiento(LocalDateTime.now(ZONE))
                .descripcion("Devolución de "+loan.getPersona()).destinatarioExterno(loan.getPersona()).referenciaCliente(ref).build();
            require(monto.compareTo(loan.getPendiente())<=0,"El abono supera el saldo pendiente.");
            account.setSaldoActual(account.getSaldoActual().add(monto));cuentas.save(account);
        }
        money(monto);require(monto.compareTo(loan.getPendiente())<=0,"El abono supera el saldo pendiente.");
        movement.setTipo(TipoMovimiento.COBRO_PRESTAMO);movement.setPrestamoOtorgadoId(id);movimientos.save(movement);
        loan.setPendiente(loan.getPendiente().subtract(monto));return prestamos.save(loan);
    }
}
