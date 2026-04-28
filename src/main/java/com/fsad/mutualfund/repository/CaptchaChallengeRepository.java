package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.CaptchaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface CaptchaChallengeRepository extends JpaRepository<CaptchaChallenge, String> {
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
