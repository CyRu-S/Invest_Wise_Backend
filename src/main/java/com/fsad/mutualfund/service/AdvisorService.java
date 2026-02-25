package com.fsad.mutualfund.service;

import com.fsad.mutualfund.entity.AdvisorAppointment;
import com.fsad.mutualfund.entity.AdvisorProfile;

import java.time.LocalDateTime;
import java.util.List;

public interface AdvisorService {
    List<AdvisorProfile> getAllAdvisors();
    AdvisorProfile getAdvisorDetail(Long advisorId);
    AdvisorAppointment bookAppointment(Long advisorId, Long investorUserId, LocalDateTime scheduledAt, String notes);
    List<AdvisorAppointment> getAppointmentsByInvestor(Long investorUserId);
    List<AdvisorAppointment> getAppointmentsByAdvisor(Long advisorProfileId);
}
