package com.dev.control_financiero.controller;

import com.dev.control_financiero.service.*;
import com.dev.control_financiero.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Reports health only; never exposes the sender address, secrets or Google responses. */
@RestController @RequestMapping("/api/notificaciones/correo") @RequiredArgsConstructor
public class EmailConnectionStatusController {
    private final NotificationEmailService email;
    private final UsuarioRepository users;
    @GetMapping("/estado") public GmailNotificationSender.PermissionStatus status(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute("userId") instanceof Long user) ||
                users.findById(user).filter(u->Boolean.TRUE.equals(u.getActivo())).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicia sesión nuevamente.");
        return email.permissionStatus();
    }
}
