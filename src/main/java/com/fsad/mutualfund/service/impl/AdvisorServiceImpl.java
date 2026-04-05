package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.entity.AdvisorAppointment;
import com.fsad.mutualfund.entity.AdvisorAvailabilitySlot;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.Transaction;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.AdvisorAppointmentRepository;
import com.fsad.mutualfund.repository.AdvisorAvailabilitySlotRepository;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.TransactionRepository;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.service.AdvisorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AdvisorServiceImpl implements AdvisorService {

    private final AdvisorProfileRepository advisorProfileRepository;
    private final AdvisorAppointmentRepository appointmentRepository;
    private final AdvisorAvailabilitySlotRepository availabilitySlotRepository;
    private final UserRepository userRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final TransactionRepository transactionRepository;

    public AdvisorServiceImpl(
            AdvisorProfileRepository advisorProfileRepository,
            AdvisorAppointmentRepository appointmentRepository,
            AdvisorAvailabilitySlotRepository availabilitySlotRepository,
            UserRepository userRepository,
            InvestorProfileRepository investorProfileRepository,
            TransactionRepository transactionRepository
    ) {
        this.advisorProfileRepository = advisorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<AdvisorProfile> getAllAdvisors() {
        return advisorProfileRepository.findAll();
    }

    @Override
    public AdvisorProfile getAdvisorDetail(Long advisorId) {
        return advisorProfileRepository.findById(advisorId)
                .orElseThrow(() -> new RuntimeException("Advisor not found: " + advisorId));
    }

    @Override
    @Transactional
    public AdvisorAppointment bookAppointment(
            Long advisorId,
            Long investorUserId,
            LocalDateTime scheduledAt,
            String notes,
            Long availabilitySlotId
    ) {
        AdvisorProfile advisor = advisorProfileRepository.findById(advisorId)
                .orElseThrow(() -> new RuntimeException("Advisor not found"));

        User investor = userRepository.findById(investorUserId)
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        if (investor.isSuspended()) {
            throw new RuntimeException("Your account is suspended and cannot create bookings");
        }

        InvestorProfile investorProfile = investorProfileRepository.findByUserId(investorUserId)
                .orElseThrow(() -> new RuntimeException("Investor profile not found"));

        LocalDateTime finalScheduledAt = resolveScheduledAt(advisorId, scheduledAt, availabilitySlotId);

        BigDecimal fee = advisor.getConsultationFee();
        if (investorProfile.getWalletBalance().compareTo(fee) < 0) {
            throw new RuntimeException("Insufficient wallet balance. Required: ₹" + fee);
        }

        investorProfile.setWalletBalance(investorProfile.getWalletBalance().subtract(fee));
        investorProfileRepository.save(investorProfile);

        AdvisorAppointment appointment = AdvisorAppointment.builder()
                .investor(investor)
                .advisor(advisor)
                .scheduledAt(finalScheduledAt)
                .notes(notes)
                .status(AdvisorAppointment.AppointmentStatus.PENDING)
                .paymentIntentId("WALLET-" + System.currentTimeMillis())
                .build();

        AdvisorAppointment savedAppointment = appointmentRepository.save(appointment);
        reserveAvailabilitySlot(advisorId, availabilitySlotId, savedAppointment);
        recordFeePayment(investor, advisor, savedAppointment, fee);
        return savedAppointment;
    }

    @Override
    public List<AdvisorAppointment> getAppointmentsByInvestor(Long investorUserId) {
        return appointmentRepository.findByInvestorIdOrderByCreatedAtDesc(investorUserId);
    }

    @Override
    public List<AdvisorAppointment> getAppointmentsByAdvisor(Long advisorProfileId) {
        return appointmentRepository.findByAdvisorIdOrderByCreatedAtDesc(advisorProfileId);
    }

    @Override
    public List<AdvisorAppointment> getAppointmentsByAdvisorUser(Long advisorUserId) {
        AdvisorProfile advisorProfile = advisorProfileRepository.findByUserId(advisorUserId)
                .orElseThrow(() -> new RuntimeException("Advisor profile not found"));

        return appointmentRepository.findByAdvisorIdOrderByCreatedAtDesc(advisorProfile.getId());
    }

    @Override
    @Transactional
    public AdvisorAppointment updateAppointmentStatus(
            Long appointmentId,
            Long advisorUserId,
            AdvisorAppointment.AppointmentStatus status
    ) {
        AdvisorAppointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getAdvisor().getUser().getId().equals(advisorUserId)) {
            throw new RuntimeException("You do not have access to update this appointment");
        }

        AdvisorAppointment.AppointmentStatus currentStatus = appointment.getStatus();
        if (currentStatus == AdvisorAppointment.AppointmentStatus.CANCELLED
                || currentStatus == AdvisorAppointment.AppointmentStatus.COMPLETED) {
            throw new RuntimeException("This appointment can no longer be updated");
        }

        boolean validTransition =
                (currentStatus == AdvisorAppointment.AppointmentStatus.PENDING
                        && (status == AdvisorAppointment.AppointmentStatus.CONFIRMED
                        || status == AdvisorAppointment.AppointmentStatus.CANCELLED))
                || (currentStatus == AdvisorAppointment.AppointmentStatus.CONFIRMED
                        && (status == AdvisorAppointment.AppointmentStatus.COMPLETED
                        || status == AdvisorAppointment.AppointmentStatus.CANCELLED));

        if (!validTransition) {
            throw new RuntimeException("Invalid status transition");
        }

        appointment.setStatus(status);
        AdvisorAppointment updated = appointmentRepository.save(appointment);

        if (status == AdvisorAppointment.AppointmentStatus.CANCELLED) {
            releaseAvailabilitySlot(updated);
            refundInvestor(updated);
        }

        return updated;
    }

    @Override
    @Transactional
    public AdvisorAppointment rescheduleAppointmentByInvestor(
            Long appointmentId,
            Long investorUserId,
            LocalDateTime scheduledAt,
            Long availabilitySlotId,
            String notes
    ) {
        AdvisorAppointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getInvestor().getId().equals(investorUserId)) {
            throw new RuntimeException("You do not have access to update this appointment");
        }

        if (appointment.getStatus() == AdvisorAppointment.AppointmentStatus.CANCELLED
                || appointment.getStatus() == AdvisorAppointment.AppointmentStatus.COMPLETED) {
            throw new RuntimeException("This appointment can no longer be rescheduled");
        }

        releaseAvailabilitySlot(appointment);
        LocalDateTime finalScheduledAt = resolveScheduledAt(
                appointment.getAdvisor().getId(),
                scheduledAt,
                availabilitySlotId
        );

        appointment.setScheduledAt(finalScheduledAt);
        appointment.setStatus(AdvisorAppointment.AppointmentStatus.PENDING);
        if (notes != null) {
            appointment.setNotes(notes);
        }

        AdvisorAppointment updated = appointmentRepository.save(appointment);
        reserveAvailabilitySlot(appointment.getAdvisor().getId(), availabilitySlotId, updated);
        return updated;
    }

    @Override
    @Transactional
    public AdvisorAppointment cancelAppointmentByInvestor(Long appointmentId, Long investorUserId) {
        AdvisorAppointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getInvestor().getId().equals(investorUserId)) {
            throw new RuntimeException("You do not have access to cancel this appointment");
        }

        if (appointment.getStatus() == AdvisorAppointment.AppointmentStatus.CANCELLED
                || appointment.getStatus() == AdvisorAppointment.AppointmentStatus.COMPLETED) {
            throw new RuntimeException("This appointment can no longer be cancelled");
        }

        appointment.setStatus(AdvisorAppointment.AppointmentStatus.CANCELLED);
        AdvisorAppointment updated = appointmentRepository.save(appointment);
        releaseAvailabilitySlot(updated);
        refundInvestor(updated);
        return updated;
    }

    @Override
    public List<AdvisorAvailabilitySlot> getAvailabilityByAdvisor(Long advisorId) {
        return availabilitySlotRepository.findByAdvisorIdAndBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
                advisorId,
                LocalDateTime.now().minusMinutes(1)
        );
    }

    @Override
    public List<AdvisorAvailabilitySlot> getAvailabilityByAdvisorUser(Long advisorUserId) {
        AdvisorProfile advisorProfile = advisorProfileRepository.findByUserId(advisorUserId)
                .orElseThrow(() -> new RuntimeException("Advisor profile not found"));
        return availabilitySlotRepository.findByAdvisorIdOrderByStartTimeAsc(advisorProfile.getId());
    }

    @Override
    @Transactional
    public AdvisorAvailabilitySlot createAvailabilitySlot(Long advisorUserId, LocalDateTime startTime, LocalDateTime endTime) {
        AdvisorProfile advisorProfile = advisorProfileRepository.findByUserId(advisorUserId)
                .orElseThrow(() -> new RuntimeException("Advisor profile not found"));

        if (startTime == null || endTime == null) {
            throw new RuntimeException("Start and end time are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new RuntimeException("End time must be after start time");
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Availability must be created in the future");
        }

        AdvisorAvailabilitySlot slot = AdvisorAvailabilitySlot.builder()
                .advisor(advisorProfile)
                .startTime(startTime)
                .endTime(endTime)
                .booked(false)
                .build();

        return availabilitySlotRepository.save(slot);
    }

    @Override
    @Transactional
    public void deleteAvailabilitySlot(Long slotId, Long advisorUserId) {
        AdvisorProfile advisorProfile = advisorProfileRepository.findByUserId(advisorUserId)
                .orElseThrow(() -> new RuntimeException("Advisor profile not found"));

        AdvisorAvailabilitySlot slot = availabilitySlotRepository.findByIdAndAdvisorId(slotId, advisorProfile.getId())
                .orElseThrow(() -> new RuntimeException("Availability slot not found"));

        if (slot.isBooked()) {
            throw new RuntimeException("Booked slots cannot be removed");
        }

        availabilitySlotRepository.delete(slot);
    }

    private LocalDateTime resolveScheduledAt(Long advisorId, LocalDateTime scheduledAt, Long availabilitySlotId) {
        if (availabilitySlotId != null) {
            AdvisorAvailabilitySlot slot = availabilitySlotRepository.findByIdAndAdvisorId(availabilitySlotId, advisorId)
                    .orElseThrow(() -> new RuntimeException("Availability slot not found"));

            if (slot.isBooked()) {
                throw new RuntimeException("That availability slot has already been booked");
            }
            if (slot.getStartTime().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("That availability slot is no longer valid");
            }
            return slot.getStartTime();
        }

        if (scheduledAt == null) {
            throw new RuntimeException("A booking time is required");
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Appointments must be booked in the future");
        }
        return scheduledAt;
    }

    private void reserveAvailabilitySlot(Long advisorId, Long availabilitySlotId, AdvisorAppointment appointment) {
        if (availabilitySlotId == null) {
            return;
        }

        AdvisorAvailabilitySlot slot = availabilitySlotRepository.findByIdAndAdvisorId(availabilitySlotId, advisorId)
                .orElseThrow(() -> new RuntimeException("Availability slot not found"));
        slot.setBooked(true);
        slot.setAppointment(appointment);
        availabilitySlotRepository.save(slot);
    }

    private void releaseAvailabilitySlot(AdvisorAppointment appointment) {
        List<AdvisorAvailabilitySlot> slots = availabilitySlotRepository.findByAdvisorIdOrderByStartTimeAsc(
                appointment.getAdvisor().getId()
        );

        Optional<AdvisorAvailabilitySlot> matchedSlot = slots.stream()
                .filter(slot -> slot.getAppointment() != null && slot.getAppointment().getId().equals(appointment.getId()))
                .findFirst();

        matchedSlot.ifPresent(slot -> {
            slot.setBooked(false);
            slot.setAppointment(null);
            availabilitySlotRepository.save(slot);
        });
    }

    private void refundInvestor(AdvisorAppointment appointment) {
        if (appointment.getPaymentIntentId() == null || !appointment.getPaymentIntentId().startsWith("WALLET-")) {
            return;
        }

        InvestorProfile investorProfile = investorProfileRepository.findByUserId(appointment.getInvestor().getId())
                .orElseThrow(() -> new RuntimeException("Investor profile not found"));

        BigDecimal refundAmount = appointment.getAdvisor().getConsultationFee();
        investorProfile.setWalletBalance(investorProfile.getWalletBalance().add(refundAmount));
        investorProfileRepository.save(investorProfile);

        transactionRepository.save(Transaction.builder()
                .user(appointment.getInvestor())
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(refundAmount)
                .status(Transaction.TransactionStatus.SUCCESS)
                .referenceId("APPOINTMENT-" + appointment.getId())
                .description("Refund issued for cancelled appointment with " + appointment.getAdvisor().getUser().getFullName())
                .build());

        appointment.setPaymentIntentId("REFUNDED-" + appointment.getId());
        appointmentRepository.save(appointment);
    }

    private void recordFeePayment(User investor, AdvisorProfile advisor, AdvisorAppointment appointment, BigDecimal fee) {
        transactionRepository.save(Transaction.builder()
                .user(investor)
                .type(Transaction.TransactionType.FEE_PAYMENT)
                .amount(fee)
                .status(Transaction.TransactionStatus.SUCCESS)
                .referenceId("APPOINTMENT-" + appointment.getId())
                .description("Consultation fee paid to " + advisor.getUser().getFullName())
                .build());
    }
}
