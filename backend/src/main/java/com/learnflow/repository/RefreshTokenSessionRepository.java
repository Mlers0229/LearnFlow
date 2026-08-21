package com.learnflow.repository;

import com.learnflow.entity.RefreshTokenSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenSession token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshTokenSession> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    List<RefreshTokenSession> findAllByFamilyId(String familyId);

    List<RefreshTokenSession> findAllByUser_Id(Long userId);
}
