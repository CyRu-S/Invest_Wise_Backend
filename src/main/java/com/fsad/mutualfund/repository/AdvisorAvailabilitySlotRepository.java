package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.AdvisorAvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdvisorAvailabilitySlotRepository extends JpaRepository<AdvisorAvailabilitySlot, Long> {
    List<AdvisorAvailabilitySlot> findByAdvisorIdOrderByStartTimeAsc(Long advisorId);

    List<AdvisorAvailabilitySlot> findByAdvisorIdAndBookedFalseAndStartTimeAfterOrderByStartTimeAsc(Long advisorId, LocalDateTime after);

    Optional<AdvisorAvailabilitySlot> findByIdAndAdvisorId(Long id, Long advisorId);
}
