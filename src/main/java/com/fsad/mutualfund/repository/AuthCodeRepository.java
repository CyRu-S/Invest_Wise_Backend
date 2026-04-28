package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.AuthCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthCodeRepository extends JpaRepository<AuthCode, Long> {
    Optional<AuthCode> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, AuthCode.Purpose purpose);

    void deleteByEmailAndPurpose(String email, AuthCode.Purpose purpose);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
