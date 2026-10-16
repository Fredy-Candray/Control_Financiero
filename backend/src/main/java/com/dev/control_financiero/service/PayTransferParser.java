package com.dev.control_financiero.service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

@Component @RequiredArgsConstructor
public class PayTransferParser {
    private final BacEmailParser mime;
    public record Transfer(BigDecimal amount,String destination,String bank,String recipient,String transactionId,LocalDateTime date,String description) {}
    public Transfer parse(byte[] raw) throws Exception {
        String text=authenticatedText(raw);
        if(!text.contains("transferencia que realizaste pudo ser procesada correctamente")
                || !Pattern.compile("(?i)Estado de transferencia:\\s*Completada\\b").matcher(text).find())
            throw new IllegalArgumentException("No es una transferencia PAY completada.");
        BigDecimal amount=amount(text);
        String destination=value(text,"Cuenta de banco destino:\\s*([Xx*]*[0-9]{4,})");
        String bank=value(text,"Banco destino:\\s*([^\\r\\n]+)").trim();
        String recipient=value(text,"Quien recibe:\\s*([^\\r\\n]+)").trim();
        var description=Pattern.compile("(?im)^\\s*(?:Descripci[oó]n|Concepto):\\s*([^\\r\\n]+)").matcher(text);
        return new Transfer(amount,destination,bank,recipient,transactionId(text),date(text),description.find()?description.group(1).trim():null);
    }
    public record Credit(BigDecimal amount,String payer,String transactionId,LocalDateTime date) {}
    public Credit parseCredit(byte[] raw) throws Exception {
        String text=authenticatedText(raw);
        if(!Pattern.compile("(?i)has recibido un\\s+abono por el valor de:").matcher(text).find())
            throw new IllegalArgumentException("No es un abono PAY confirmado.");
        var payer=Pattern.compile("(?i)De parte de:\\s*([^\\r\\n]+)").matcher(text);
        return new Credit(amount(text),payer.find()?payer.group(1).trim():null,transactionId(text),date(text));
    }
    private String authenticatedText(byte[] raw) throws Exception {
        var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw));
        var from=message.getFrom();
        if(from==null || from.length!=1 || !(from[0] instanceof InternetAddress sender)
                || !Set.of("notificacionespay@bcr.gob.sv","notificaciones365pay@bcr.gob.sv").contains(sender.getAddress().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Remitente PAY no válido.");
        var auth=message.getHeader("Authentication-Results");
        if(auth==null || auth.length==0 || !auth[0].trim().toLowerCase(Locale.ROOT).startsWith("mx.google.com;")
                || !Pattern.compile("dmarc=pass\\b[^;]*header\\.from=bcr\\.gob\\.sv(?:\\s|;|$)",Pattern.CASE_INSENSITIVE).matcher(auth[0]).find())
            throw new IllegalArgumentException("Gmail no confirmó la autenticidad de PAY.");
        String text=mime.body(message,"text/plain");
        if(text.isBlank()) text=mime.body(message,"text/html").replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","")
                .replaceAll("(?i)</(?:td|tr|p|div|h[1-6])>|<br\\s*/?>","\n").replaceAll("<[^>]+>"," ")
                .replace("&nbsp;"," ").replace("&amp;","&").replace("&#243;","ó");
        text=text.replace('\u00a0',' ').replaceAll("[\\t ]+"," ");
        return text;
    }
    private BigDecimal amount(String text) {
        BigDecimal amount=new BigDecimal(value(text,"valor de:\\s*\\$([0-9,]+\\.[0-9]{2})").replace(",",""));
        if(amount.signum()<=0 || amount.precision()-amount.scale()>16) throw new IllegalArgumentException("Monto PAY no válido.");
        return amount;
    }
    private String transactionId(String text) { return value(text,"ID transacci[oó]n:\\s*([a-zA-Z0-9-]{8,100})"); }
    private LocalDateTime date(String text) {
        String date=value(text,"Fecha y hora:\\s*(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}(?::\\d{2})?\\s+[AP]M)").replaceAll("\\s+"," ");
        var format=new DateTimeFormatterBuilder().appendPattern("dd/MM/uuuu hh:mm").optionalStart().appendPattern(":ss").optionalEnd().appendPattern(" a")
                .toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
        try { return LocalDateTime.parse(date,format); }
        catch(java.time.DateTimeException invalid) { throw new IllegalArgumentException("Fecha PAY inválida."); }
    }
    private String value(String text,String pattern) {
        var m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(text);
        if(!m.find()) throw new IllegalArgumentException("Faltan datos de la transferencia PAY.");
        return m.group(1);
    }
}
