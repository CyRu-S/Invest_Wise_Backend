package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.dto.RiskQuestionnaireRequest;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.service.InvestorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvestorServiceImpl implements InvestorService {

    private final InvestorProfileRepository profileRepository;
    private final UserRepository userRepository;

    public InvestorServiceImpl(InvestorProfileRepository profileRepository,
                               UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Override
    public InvestorProfile getProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Investor profile not found for user: " + userId));
    }

    @Override
    @Transactional
    public InvestorProfile processRiskQuestionnaire(Long userId, RiskQuestionnaireRequest request) {
        InvestorProfile profile = getProfile(userId);
        List<Integer> answers = request.getAnswers();

        if (answers == null || answers.isEmpty()) {
            throw new RuntimeException("No answers provided");
        }

        // Calculate risk score: sum of answers normalized to 0-100
        int maxPossible = answers.size() * 5; // Each answer is 1-5
        int totalScore = answers.stream().mapToInt(Integer::intValue).sum();
        int normalizedScore = (int) ((totalScore / (double) maxPossible) * 100);

        // Determine risk category
        InvestorProfile.RiskCategory category;
        if (normalizedScore <= 33) {
            category = InvestorProfile.RiskCategory.CONSERVATIVE;
        } else if (normalizedScore <= 66) {
            category = InvestorProfile.RiskCategory.MODERATE;
        } else {
            category = InvestorProfile.RiskCategory.AGGRESSIVE;
        }

        profile.setRiskToleranceScore(normalizedScore);
        profile.setRiskCategory(category);

        return profileRepository.save(profile);
    }

    @Override
    @Transactional
    public InvestorProfile depositToWallet(Long userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Deposit amount must be positive");
        }

        InvestorProfile profile = getProfile(userId);
        profile.setWalletBalance(profile.getWalletBalance().add(amount));
        return profileRepository.save(profile);
    }
}
