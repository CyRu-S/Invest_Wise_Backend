package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.AdvisorAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdvisorAppointmentRepository extends JpaRepository<AdvisorAppointment, Long> {
    List<AdvisorAppointment> findByInvestorIdOrderByCreatedAtDesc(Long investorId);

    List<AdvisorAppointment> findByAdvisorIdOrderByCreatedAtDesc(Long advisorId);
}
