package com.dev.control_financiero.controller;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.repository.*;
import com.dev.control_financiero.service.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/gmail-bac") @RequiredArgsConstructor
public class GmailBacController {
    private final GmailBacClient client;
    private final GmailTokenCipher cipher;
    private final GmailConnectionRepository connections;
    private final BankEmailImportRepository imports;
    private final GmailConnectionService service;
    private final GmailBacScheduler scheduler;
    @Value("${app.gmail.frontend-url:http://localhost:4200/cuentas}") private String frontendUrl;
    @Value("${app.gmail.events-since:}") private String eventsSince="";

    public record Status(boolean configured,boolean connected,boolean enabled,String correo,Instant importSince,Instant lastSync,String lastError) {}
    @GetMapping public Status status(HttpServletRequest request) {
        var c=connections.findByUsuarioId(user(request)).orElse(new GmailConnection());
        Instant cutoff=c.getImportSince();
        if(cutoff!=null && !eventsSince.isBlank() && Instant.parse(eventsSince).isAfter(cutoff)) cutoff=Instant.parse(eventsSince);
        return new Status(client.configured()&&cipher.configured(),c.getRefreshTokenEncrypted()!=null,c.isEnabled(),c.getCorreo(),cutoff,c.getLastSync(),c.getLastError());
    }
    @GetMapping("/history") public List<BankEmailImport> history(HttpServletRequest request) { return imports.findTop50ByUsuarioIdOrderByIdDesc(user(request)); }
    public record Notifications(Long cursor, List<BankEmailImportRepository.MovementNotice> movements) {}
    @GetMapping("/notifications") public Notifications notifications(@RequestParam(required=false) Long after, HttpServletRequest request) {
        Long userId=user(request);
        // The first request establishes a baseline; old imports are not new alerts.
        if(after==null) return new Notifications(imports.notificationCursor(userId),List.of());
        if(after<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cursor inválido.");
        var notices=imports.notifications(userId,after,org.springframework.data.domain.PageRequest.of(0,50));
        return new Notifications(notices.isEmpty()?after:notices.get(notices.size()-1).getId(),notices);
    }
    @PostMapping("/connect") public Map<String,String> connect(HttpServletRequest request) throws Exception {
        user(request);
        if(!client.configured() || !cipher.configured()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Configura las credenciales de Google y la clave de cifrado en el servidor.");
        String state=random(),verifier=random(); var session=request.getSession(false);
        session.setAttribute("gmailState",state); session.setAttribute("gmailVerifier",verifier); session.setAttribute("gmailStateExpires",Instant.now().plusSeconds(600));
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        return Map.of("url",client.authorizationUrl(state,challenge));
    }
    @GetMapping("/callback") public void callback(@RequestParam(required=false) String code,@RequestParam(required=false) String state,
            HttpServletRequest request,HttpServletResponse response) throws Exception {
        var session=request.getSession(false); Long userId=user(request);
        String expected=(String)session.getAttribute("gmailState"), verifier=(String)session.getAttribute("gmailVerifier");
        var expires=(Instant)session.getAttribute("gmailStateExpires");
        session.removeAttribute("gmailState"); session.removeAttribute("gmailVerifier"); session.removeAttribute("gmailStateExpires");
        if(state==null || expected==null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8))
                || expires==null || expires.isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La autorización expiró. Conecta Gmail nuevamente.");
        if(code==null) { response.sendRedirect(frontendUrl+"?gmail=cancelled"); return; }
        try {
            var tokens=client.exchange(code,verifier); String access=tokens.path("access_token").asText();
            if(!tokens.path("scope").asText().contains("https://www.googleapis.com/auth/gmail.readonly")) throw new IllegalArgumentException("No se concedió acceso de lectura.");
            String email=client.profile(access).path("emailAddress").asText();
            if(email.isBlank()) throw new IllegalArgumentException("No se identificó el correo.");
            service.connect(userId,email,tokens.path("refresh_token").asText());
            response.sendRedirect(frontendUrl+"?gmail=connected");
        } catch(Exception e) { response.sendRedirect(frontendUrl+"?gmail=error"); }
    }
    public record EnableRequest(boolean enabled) {}
    @PutMapping("/enabled") public Status enabled(@RequestBody EnableRequest body,HttpServletRequest request) {
        try { service.enable(user(request),body.enabled()); } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage()); }
        return status(request);
    }
    @PostMapping("/sync") public Status sync(HttpServletRequest request) { scheduler.sync(user(request)); return status(request); }
    @DeleteMapping public Status disconnect(HttpServletRequest request) {
        String token=service.disconnect(user(request));
        if(token!=null) { try { client.revoke(token); } catch(Exception e) { service.syncResult(user(request),"Acceso eliminado del sistema. Si Google no respondió, revoca también el permiso desde tu cuenta Google."); } }
        return status(request);
    }
    private Long user(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicia sesión nuevamente.");
        return id;
    }
    private String random() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
