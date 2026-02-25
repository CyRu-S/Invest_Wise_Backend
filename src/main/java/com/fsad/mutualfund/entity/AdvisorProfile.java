package com.fsad.mutualfund.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "advisor_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvisorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 255)
    private String specialization; // e.g., "Retirement Planning", "Tax Saving"

    @Column(name = "consultation_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal consultationFee;

    @Column(name = "experience_years")
    private int experienceYears;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Builder.Default
    @Column(name = "average_rating")
    private double averageRating = 0.0;

    @Builder.Default
    @Column(name = "total_reviews")
    private int totalReviews = 0;
}
