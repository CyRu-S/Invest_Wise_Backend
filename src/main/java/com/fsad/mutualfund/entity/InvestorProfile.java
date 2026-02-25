package com.fsad.mutualfund.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "investor_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "risk_tolerance_score")
    private int riskToleranceScore = 0; // 0-100

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category")
    private RiskCategory riskCategory = RiskCategory.MODERATE;

    @Builder.Default
    @Column(name = "wallet_balance", precision = 19, scale = 4)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(name = "investment_horizon")
    private String investmentHorizon; // e.g., "1-3 years", "5+ years"

    public enum RiskCategory {
        CONSERVATIVE, MODERATE, AGGRESSIVE
    }
}
