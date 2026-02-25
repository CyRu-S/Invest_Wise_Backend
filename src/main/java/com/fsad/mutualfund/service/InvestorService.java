package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.RiskQuestionnaireRequest;
import com.fsad.mutualfund.entity.InvestorProfile;

import java.math.BigDecimal;

public interface InvestorService {
    InvestorProfile getProfile(Long userId);
    InvestorProfile processRiskQuestionnaire(Long userId, RiskQuestionnaireRequest request);
    InvestorProfile depositToWallet(Long userId, BigDecimal amount);
}
