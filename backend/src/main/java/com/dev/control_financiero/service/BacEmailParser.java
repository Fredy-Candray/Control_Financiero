package com.dev.control_financiero.service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

@Component
public class BacEmailParser {
    public record Purchase(String lastFour,String merchant,BigDecimal amount,LocalDateTime date,String rfcId) {}
    public String authenticatedNoticeSubject(byte[] raw) throws Exception {
        var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw));
        var from=message.getFrom(); var auth=message.getHeader("Authentication-Results");
        if(from==null || from.length!=1 || !(from[0] instanceof InternetAddress sender)
                || !sender.getAddress().toLowerCase(Locale.ROOT).endsWith("@baccredomatic.com")
                || auth==null || auth.length==0 || !auth[0].trim().toLowerCase(Locale.ROOT).startsWith("mx.google.com;")
                || !Pattern.compile("dmarc=pass\\b[^;]*header\\.from=baccredomatic\\.com(?:\\s|;|$)",Pattern.CASE_INSENSITIVE).matcher(auth[0]).find())
            throw new IllegalArgumentException("Aviso BAC sin autenticidad confirmada.");
        return message.getSubject()==null?"Aviso BAC":message.getSubject();
    }
    public LocalDateTime noticeOperationDate(byte[] raw) throws Exception {
        authenticatedNoticeSubject(raw);
        var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw));
        String text=body(message,"text/plain");
        if(text.isBlank()) text=body(message,"text/html").replaceAll("(?i)</(?:td|tr|p|div|h[1-6])>|<br\\s*/?>","\n").replaceAll("<[^>]+>"," ").replace("&nbsp;"," ");
        text=text.replace('\u00a0',' ');
        var matcher=Pattern.compile("(?i)Fecha(?: y hora| de (?:la )?(?:transacci[oó]n|operaci[oó]n))?\\s*:?\\s*(\\d{4}/\\d{2}/\\d{2}|\\d{2}/\\d{2}/\\d{4})\\s*(?:-|a las|\\s)\\s*(\\d{2}:\\d{2}(?::\\d{2})?)(?:\\s*([AP]M))?").matcher(text);
        if(!matcher.find()) return null;
        String pattern=matcher.group(1).length()>4 && matcher.group(1).charAt(4)=='/'?"uuuu/MM/dd":"dd/MM/uuuu";
        pattern+=" "+(matcher.group(3)==null?"HH:mm":"hh:mm");
        if(matcher.group(2).length()==8) pattern+=":ss";
        if(matcher.group(3)!=null) pattern+=" a";
        String value=matcher.group(1)+" "+matcher.group(2)+(matcher.group(3)==null?"":" "+matcher.group(3).toUpperCase(Locale.ROOT));
        try { return LocalDateTime.parse(value,DateTimeFormatter.ofPattern(pattern,Locale.US).withResolverStyle(java.time.format.ResolverStyle.STRICT)); }
        catch(java.time.DateTimeException invalid) { return null; }
    }
    public Purchase parse(byte[] raw) throws Exception {
        var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw));
        var from=message.getFrom();
        if(from==null || from.length!=1 || !(from[0] instanceof InternetAddress sender)
                || !"info@baccredomatic.com".equalsIgnoreCase(sender.getAddress()))
            throw new IllegalArgumentException("El remitente no es el configurado para BAC.");
        // Only the receiving Gmail server's Authentication-Results is accepted; ARC/body headers are not trusted.
        String[] auth=message.getHeader("Authentication-Results");
        if(auth==null || auth.length==0 || !auth[0].trim().toLowerCase(Locale.ROOT).startsWith("mx.google.com;")
                || !Pattern.compile("dmarc=pass\\b[^;]*header\\.from=baccredomatic\\.com(?:\\s|;|$)",Pattern.CASE_INSENSITIVE).matcher(auth[0]).find())
            throw new IllegalArgumentException("Gmail no confirmó la autenticidad del aviso de BAC.");
        String text=body(message,"text/plain");
        if(text.isBlank()) text=body(message,"text/html").replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","")
                .replaceAll("(?i)</(?:td|tr|p|div|br|h[1-6])>|<br\\s*/?>","\n")
                .replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&");
        text=text.replace('\u00a0',' ').replaceAll("[\\t ]+"," ");
        if(!Pattern.compile("(?im)^\\s*Aprobada\\s*$").matcher(text).find())
            throw new IllegalArgumentException("El aviso no corresponde a una compra aprobada.");
        String digits=match(text,"(?i)Se acaba de realizar una compra con tu tarjeta\\s+(?:adicional\\s+)?(?:VISA|MASTERCARD|AMERICAN\\s+EXPRESS)\\s+terminada en\\s+(\\d{4})\\b",1);
        var details=Pattern.compile("(?is)Comercio\\s+Monto\\s+(.+?)\\s+([0-9][0-9,]*\\.[0-9]{2})\\s+Fecha y hora\\s+(\\d{4}/\\d{2}/\\d{2}-\\d{2}:\\d{2}:\\d{2})").matcher(text);
        if(!details.find()) throw new IllegalArgumentException("El formato del aviso cambió o faltan datos.");
        String merchant=details.group(1).replaceAll("\\s+"," ").trim();
        BigDecimal amount=new BigDecimal(details.group(2).replace(",",""));
        if(amount.signum()<=0 || amount.precision()-amount.scale()>16 || merchant.length()>255)
            throw new IllegalArgumentException("Los datos de compra no son válidos.");
        return new Purchase(digits,merchant,amount,LocalDateTime.parse(details.group(3),DateTimeFormatter.ofPattern("uuuu/MM/dd-HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)),message.getMessageID());
    }
    private String match(String text,String regex,int group) {
        var matcher=Pattern.compile(regex).matcher(text); if(!matcher.find()) throw new IllegalArgumentException("No se identificó la tarjeta.");
        return matcher.group(group);
    }
    String body(Part part,String mime) throws Exception {
        if(Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) return "";
        if(part.isMimeType(mime)) return String.valueOf(part.getContent());
        if(part.isMimeType("multipart/*")) {
            var parts=(Multipart)part.getContent();
            for(int i=0;i<parts.getCount();i++) { String text=body(parts.getBodyPart(i),mime); if(!text.isBlank()) return text; }
        }
        return "";
    }
}
