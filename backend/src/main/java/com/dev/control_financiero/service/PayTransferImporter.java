package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.entity.BankEmailImport;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class PayTransferImporter {
    private final GmailConnectionRepository connections;
    private final BankEmailImportRepository imports;
    private final CuentaRepository accounts;
    private final MovimientoService movements;
    private final PayTransferParser parser;
    @Value("${app.pay.user-id:0}") private Long configuredUser;
    @Value("${app.pay.import-since:}") private String since;
    @Value("${app.pay.destination-last-four:}") private String lastFour;
    @Value("${app.pay.origin-name:Pay}") private String originName;
    @Value("${app.pay.destination-name:Tarjeta débito}") private String destinationName;
    @Value("${app.pay.extended:false}") private boolean extended;
    @Value("${app.pay.owner-name:}") private String ownerName="";
    public boolean enabled(Long userId) { return userId.equals(configuredUser) && !since.isBlank() && lastFour.matches("\\d{4}"); }
    public Instant importSince() { return Instant.parse(since); }
    @Transactional public void process(Long userId,String gmailId,Instant received,byte[] raw) throws Exception {
        if(!enabled(userId)) return;
        var connection=connections.lockByUser(userId).orElseThrow();
        if(!connection.isEnabled()) return;
        var record=imports.findByUsuarioIdAndGmailMessageId(userId,gmailId).orElseGet(BankEmailImport::new);
        if("IMPORTADO".equals(record.getEstado()) || "IGNORADO".equals(record.getEstado())) return;
        record.setUsuarioId(userId); record.setGmailMessageId(gmailId); record.setProcesadoEn(Instant.now());
        if(received.isBefore(importSince())) { save(record,"IGNORADO","Anterior a la activación de PAY."); return; }
        PayTransferParser.Transfer transfer;
        try { transfer=parser.parse(raw); } catch(IllegalArgumentException e) {
            if(extended) {
                try { importCredit(userId,record,parser.parseCredit(raw)); }
                catch(IllegalArgumentException invalid) { save(record,"PENDIENTE","Aviso PAY sin formato reconocido. Revisa el correo antes de afectar saldos."); }
            } else save(record,"ERROR",e.getMessage());
            return;
        }
        var instant=transfer.date().atZone(ZoneId.of("America/El_Salvador")).toInstant();
        if(instant.isBefore(importSince())) { save(record,"IGNORADO","Transferencia anterior a la activación de PAY."); return; }
        if(instant.isAfter(Instant.now().plusSeconds(300))) { save(record,"ERROR","Fecha PAY en el futuro."); return; }
        record.setMonto(transfer.amount()); record.setFechaCompra(transfer.date()); record.setUltimosCuatro(lastFour);
        record.setComercio("PAY → "+destinationName);
        String transactionKey="pay:"+transfer.transactionId();
        record.setRfcMessageId(transactionKey);
        if(imports.existsByUsuarioIdAndRfcMessageIdAndEstado(userId,transactionKey,"IMPORTADO")) { save(record,"IGNORADO","Transacción PAY ya registrada."); return; }
        if(!transfer.destination().endsWith(lastFour) || !transfer.bank().equalsIgnoreCase("BANCO AMÉRICA CENTRAL")) {
            if(extended) importExternal(userId,record,transfer);
            else save(record,"ERROR","El destino PAY no coincide con la cuenta BAC configurada.");
            return;
        }
        var userAccounts=accounts.findByUsuarioId(userId);
        var origins=userAccounts.stream().filter(a->originName.equalsIgnoreCase(a.getNombre()) && Boolean.TRUE.equals(a.getActiva()) && a.getTipo()==TipoCuenta.DEBITO).toList();
        var destinations=userAccounts.stream().filter(a->destinationName.equalsIgnoreCase(a.getNombre()) && "BAC".equals(a.getBanco()) && Boolean.TRUE.equals(a.getActiva()) && a.getTipo()==TipoCuenta.DEBITO).toList();
        if(origins.size()!=1 || destinations.size()!=1 || origins.get(0).getId().equals(destinations.get(0).getId())) {
            save(record,"ERROR","Revisa las cuentas Pay y Tarjeta débito configuradas."); return;
        }
        long origin=origins.get(0).getId(), destination=destinations.get(0).getId();
        accounts.findLockedById(Math.min(origin,destination)).orElseThrow();
        accounts.findLockedById(Math.max(origin,destination)).orElseThrow();
        var locked=accounts.findById(origin).orElseThrow();
        if(locked.getSaldoActual().compareTo(transfer.amount())<0) { save(record,"ERROR","Saldo insuficiente en Pay."); return; }
        var request=new CrearMovimientoRequest(); request.setUsuarioId(userId); request.setTipo(TipoMovimiento.TRANSFERENCIA);
        request.setCuentaOrigenId(origin); request.setCuentaDestinoId(destination); request.setMonto(transfer.amount());
        request.setFechaMovimiento(transfer.date());
        request.setDescripcion("TRANSFERENCIA");
        var movement=movements.crearMovimiento(request); record.setMovimientoId(movement.getId());
        save(record,"IMPORTADO","Transferencia PAY registrada; no es ingreso ni gasto.");
    }
    private boolean ownName(String name) {
        return name!=null && !ownerName.isBlank() && name.trim().equalsIgnoreCase(ownerName.trim());
    }
    private void importExternal(Long userId,BankEmailImport record,PayTransferParser.Transfer transfer) {
        if(ownerName.isBlank() || ownName(transfer.recipient())) {
            save(record,"PENDIENTE","Transferencia a otra cuenta propia sin asociación. No se registra como gasto."); return;
        }
        record.setUltimosCuatro(transfer.destination().substring(transfer.destination().length()-4));
        record.setComercio("PAY → "+transfer.recipient());
        applyPay(userId,record,TipoMovimiento.GASTO,"PAGO A "+transfer.recipient());
    }
    private void importCredit(Long userId,BankEmailImport record,PayTransferParser.Credit credit) {
        var instant=credit.date().atZone(ZoneId.of("America/El_Salvador")).toInstant();
        if(instant.isBefore(importSince())) { save(record,"IGNORADO","Abono anterior a la activación."); return; }
        if(instant.isAfter(Instant.now().plusSeconds(300))) { save(record,"ERROR","Fecha PAY en el futuro."); return; }
        record.setMonto(credit.amount()); record.setFechaCompra(credit.date()); record.setUltimosCuatro(null);
        record.setRfcMessageId("pay:"+credit.transactionId()); record.setComercio("ABONO PAY");
        if(imports.existsByUsuarioIdAndRfcMessageIdAndEstado(userId,record.getRfcMessageId(),"IMPORTADO")) {
            save(record,"IGNORADO","Transacción PAY ya registrada."); return;
        }
        if(ownName(credit.payer())) { save(record,"PENDIENTE","Abono de una cuenta propia: falta identificar el origen de la transferencia."); return; }
        applyPay(userId,record,TipoMovimiento.INGRESO,credit.payer()==null?"ABONO PAY":"ABONO DE "+credit.payer());
    }
    private void applyPay(Long userId,BankEmailImport record,TipoMovimiento type,String description) {
        var matches=accounts.findByUsuarioId(userId).stream().filter(a->originName.equalsIgnoreCase(a.getNombre())
                && Boolean.TRUE.equals(a.getActiva()) && a.getTipo()==TipoCuenta.DEBITO).toList();
        if(matches.size()!=1) { save(record,"ERROR","No hay una única cuenta Pay activa."); return; }
        var account=accounts.findLockedById(matches.get(0).getId()).orElseThrow();
        if(type==TipoMovimiento.GASTO && account.getSaldoActual().compareTo(record.getMonto())<0) {
            save(record,"ERROR","Saldo insuficiente en Pay."); return;
        }
        var request=new CrearMovimientoRequest(); request.setUsuarioId(userId); request.setTipo(type);
        request.setCuentaOrigenId(account.getId()); request.setMonto(record.getMonto()); request.setFechaMovimiento(record.getFechaCompra());
        request.setDescripcion(description.substring(0,Math.min(description.length(),255)));
        record.setMovimientoId(movements.crearMovimiento(request).getId());
        save(record,"IMPORTADO",type==TipoMovimiento.INGRESO?"Abono PAY registrado.":"Salida PAY a un tercero registrada como gasto.");
    }
    private void save(BankEmailImport record,String status,String detail) { record.setEstado(status); record.setDetalle(detail); imports.save(record); }
}
