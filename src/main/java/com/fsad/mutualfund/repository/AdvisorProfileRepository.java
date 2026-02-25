package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.AdvisorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdvisorProfileRepository extends JpaRepository<AdvisorProfile, Long> {
    Optional<AdvisorProfile> findByUserId(Long userId);
}
