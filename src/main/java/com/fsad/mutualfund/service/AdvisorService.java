package com.fsad.mutualfund.service;

import com.fsad.mutualfund.entity.AdvisorAppointment;
import com.fsad.mutualfund.entity.AdvisorAvailabilitySlot;
import com.fsad.mutualfund.entity.AdvisorProfile;

import java.time.LocalDateTime;
import java.util.List;

public interface AdvisorService {
    List<AdvisorProfile> getAllAdvisors();
    AdvisorProfile getAdvisorDetail(Long advisorId);
    AdvisorAppointment bookAppointment(Long advisorId, Long investorUserId, LocalDateTime scheduledAt, String notes, Long availabilitySlotId);
    List<AdvisorAppointment> getAppointmentsByInvestor(Long investorUserId);
    List<AdvisorAppointment> getAppointmentsByAdvisor(Long advisorProfileId);
    List<AdvisorAppointment> getAppointmentsByAdvisorUser(Long advisorUserId);
    AdvisorAppointment updateAppointmentStatus(Long appointmentId, Long advisorUserId, AdvisorAppointment.AppointmentStatus status);
    AdvisorAppointment rescheduleAppointmentByInvestor(Long appointmentId, Long investorUserId, LocalDateTime scheduledAt, Long availabilitySlotId, String notes);
    AdvisorAppointment cancelAppointmentByInvestor(Long appointmentId, Long investorUserId);
    List<AdvisorAvailabilitySlot> getAvailabilityByAdvisor(Long advisorId);
    List<AdvisorAvailabilitySlot> getAvailabilityByAdvisorUser(Long advisorUserId);
    AdvisorAvailabilitySlot createAvailabilitySlot(Long advisorUserId, LocalDateTime startTime, LocalDateTime endTime);
    void deleteAvailabilitySlot(Long slotId, Long advisorUserId);
}
