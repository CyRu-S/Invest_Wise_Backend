package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.entity.AdvisorAppointment;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.AdvisorAppointmentRepository;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.service.AdvisorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdvisorServiceImpl implements AdvisorService {

    private final AdvisorProfileRepository advisorProfileRepository;
    private final AdvisorAppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final InvestorProfileRepository investorProfileRepository;

    public AdvisorServiceImpl(AdvisorProfileRepository advisorProfileRepository,
            AdvisorAppointmentRepository appointmentRepository,
            UserRepository userRepository,
            InvestorProfileRepository investorProfileRepository) {
        this.advisorProfileRepository = advisorProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.investorProfileRepository = investorProfileRepository;
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
    public AdvisorAppointment bookAppointment(Long advisorId, Long investorUserId, LocalDateTime scheduledAt,
            String notes) {
        AdvisorProfile advisor = advisorProfileRepository.findById(advisorId)
                .orElseThrow(() -> new RuntimeException("Advisor not found"));

        User investor = userRepository.findById(investorUserId)
                .orElseThrow(() -> new RuntimeException("Investor not found"));

        InvestorProfile investorProfile = investorProfileRepository.findByUserId(investorUserId)
                .orElseThrow(() -> new RuntimeException("Investor profile not found"));

        // Deduct consultation fee from wallet
        BigDecimal fee = advisor.getConsultationFee();
        if (investorProfile.getWalletBalance().compareTo(fee) < 0) {
            throw new RuntimeException("Insufficient wallet balance. Required: ₹" + fee);
        }
        investorProfile.setWalletBalance(investorProfile.getWalletBalance().subtract(fee));
        investorProfileRepository.save(investorProfile);

        AdvisorAppointment appointment = AdvisorAppointment.builder()
                .investor(investor)
                .advisor(advisor)
                .scheduledAt(scheduledAt)
                .notes(notes)
                .status(AdvisorAppointment.AppointmentStatus.PENDING)
                .build();

        return appointmentRepository.save(appointment);
    }

    @Override
    public List<AdvisorAppointment> getAppointmentsByInvestor(Long investorUserId) {
        return appointmentRepository.findByInvestorIdOrderByCreatedAtDesc(investorUserId);
    }

    @Override
    public List<AdvisorAppointment> getAppointmentsByAdvisor(Long advisorProfileId) {
        return appointmentRepository.findByAdvisorIdOrderByCreatedAtDesc(advisorProfileId);
    }
}
