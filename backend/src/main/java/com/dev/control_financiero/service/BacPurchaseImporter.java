package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;

@Service @RequiredArgsConstructor
public class BacPurchaseImporter {
    private final GmailConnectionRepository connections;
    private final BankEmailImportRepository imports;
    private final CuentaRepository accounts;
    private final UsuarioRepository users;
    private final MovimientoService movements;
    private final BacEmailParser parser;
    private final MerchantCategoryService merchantCategories;
    @Value("${app.gmail.events-since:}") private String eventsSince="";
    // Explicit owner-scoped associations: usuario:adicional:principal, separated by commas.
    @Value("${app.gmail.card-aliases:}") private String cardAliases="";

    @Transactional public void process(Long userId,String gmailId,Instant received,byte[] raw) throws Exception {
        // Serializes imports for this mailbox across processes; balance and email receipt commit together.
        var connection=connections.lockByUser(userId).orElseThrow();
        if(!connection.isEnabled() || connection.getImportSince()==null
                || !users.findById(userId).map(u->Boolean.TRUE.equals(u.getActivo())).orElse(false)) return;
        Instant cutoff=connection.getImportSince();
        if(!eventsSince.isBlank() && Instant.parse(eventsSince).isAfter(cutoff)) cutoff=Instant.parse(eventsSince);
        var record=imports.findByUsuarioIdAndGmailMessageId(userId,gmailId).orElseGet(BankEmailImport::new);
        if("IMPORTADO".equals(record.getEstado()) || "IGNORADO".equals(record.getEstado())) return;
        record.setUsuarioId(userId); record.setGmailMessageId(gmailId); record.setProcesadoEn(Instant.now());
        if(received.isBefore(cutoff)) { save(record,"IGNORADO","Anterior al saldo inicial confirmado."); return; }
        BacEmailParser.Purchase purchase;
        try { purchase=parser.parse(raw); }
        catch(Exception e) {
            try {
                String subject=parser.authenticatedNoticeSubject(raw);
                record.setComercio(subject.substring(0,Math.min(subject.length(),255)));
                var operationDate=parser.noticeOperationDate(raw);
                record.setFechaCompra(operationDate);
                if(operationDate!=null && operationDate.atZone(ZoneId.of("America/El_Salvador")).toInstant().isBefore(cutoff)) {
                    save(record,"IGNORADO","Operación incluida en los saldos iniciales confirmados, aunque el correo llegó después."); return;
                }
                save(record,"PENDIENTE","Aviso BAC sin una regla verificada para esta operación. No se modificaron saldos."); return;
            } catch(Exception untrusted) { /* Preserve rejection of untrusted senders. */ }
            String detail=e instanceof IllegalArgumentException?e.getMessage():"No se pudo interpretar el correo.";
            boolean ignore=detail!=null && (detail.contains("remitente") || detail.contains("autenticidad") || detail.contains("no corresponde a una compra aprobada"));
            save(record,ignore?"IGNORADO":"ERROR",detail); return;
        }
        record.setRfcMessageId(purchase.rfcId()!=null && purchase.rfcId().length()<=255 ? purchase.rfcId() : null);
        record.setUltimosCuatro(purchase.lastFour());
        record.setComercio(purchase.merchant()); record.setMonto(purchase.amount()); record.setFechaCompra(purchase.date());
        if(purchase.date().atZone(ZoneId.of("America/El_Salvador")).toInstant().isBefore(cutoff)) {
            save(record,"IGNORADO","La compra es anterior a la activación."); return;
        }
        if(purchase.date().atZone(ZoneId.of("America/El_Salvador")).toInstant().isAfter(Instant.now().plusSeconds(300))) {
            save(record,"ERROR","La fecha de compra está en el futuro."); return;
        }
        if(purchase.rfcId()==null || purchase.rfcId().length()>255) { save(record,"IGNORADO","Falta un identificador válido del mensaje."); return; }
        if(imports.existsByUsuarioIdAndRfcMessageIdAndEstado(userId,purchase.rfcId(),"IMPORTADO")) {
            save(record,"IGNORADO","Este mensaje ya fue importado."); return;
        }
        String accountDigits=associatedCard(userId,purchase.lastFour());
        var candidates=accounts.findByUsuarioId(userId).stream().filter(a->"BAC".equals(a.getBanco())
                && accountDigits.equals(a.getUltimosCuatro()) && Boolean.TRUE.equals(a.getActiva())
                && a.getTipo()!=TipoCuenta.EFECTIVO && (a.getMoneda()==null || "USD".equals(a.getMoneda()))).toList();
        if(candidates.size()!=1) { save(record,"ERROR","No hay una única tarjeta BAC activa asociada a estos dígitos."); return; }
        var account=accounts.findLockedById(candidates.get(0).getId()).orElseThrow();
        if(account.getSaldoActual().compareTo(purchase.amount())<0) { save(record,"ERROR","Saldo o crédito disponible insuficiente. Revisa el saldo y vuelve a sincronizar."); return; }
        var request=new CrearMovimientoRequest(); request.setUsuarioId(userId); request.setTipo(TipoMovimiento.GASTO);
        request.setMonto(purchase.amount()); request.setCuentaOrigenId(account.getId()); request.setDescripcion(purchase.merchant());
        request.setFechaMovimiento(purchase.date());
        request.setCategoriaId(merchantCategories.categoryId(purchase.merchant()));
        var movement=movements.crearMovimiento(request);
        String categoryDetail=request.getCategoriaId()==null?"":" Categoría automática según comercio.";
        record.setMovimientoId(movement.getId()); save(record,"IMPORTADO",(accountDigits.equals(purchase.lastFour())
                ? "Compra registrada automáticamente en USD."
                : "Compra de tarjeta adicional "+purchase.lastFour()+" registrada en tarjeta principal "+accountDigits+" en USD.")+categoryDetail);
    }
    private String associatedCard(Long userId,String digits) {
        String target=digits;
        for(String association:cardAliases.split(",")) {
            String[] parts=association.trim().split(":");
            if(parts.length!=3 || !parts[0].equals(userId.toString()) || !parts[1].equals(digits)) continue;
            if(!parts[2].matches("\\d{4}") || (!target.equals(digits) && !target.equals(parts[2])))
                throw new IllegalStateException("Asociación de tarjeta adicional inválida o ambigua.");
            target=parts[2];
        }
        return target;
    }
    private void save(BankEmailImport record,String status,String detail) {
        record.setEstado(status); record.setDetalle(detail); imports.save(record);
    }
}
