package com.learnflow.repository;

import com.learnflow.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    List<PasswordResetToken> findAllByUser_IdAndUsedAtIsNull(Long userId);

    long deleteByExpiresAtBefore(Instant cutoff);
}
