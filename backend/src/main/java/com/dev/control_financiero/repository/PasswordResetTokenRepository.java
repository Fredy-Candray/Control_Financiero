package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, LocalDateTime now);
    void deleteAllByUsuario_IdAndUsedAtIsNull(Long usuarioId);
}
