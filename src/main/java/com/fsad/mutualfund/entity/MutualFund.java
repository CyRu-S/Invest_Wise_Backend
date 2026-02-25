package com.fsad.mutualfund.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mutual_funds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MutualFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_name", unique = true, nullable = false)
    private String fundName;

    @Column(name = "ticker_symbol", unique = true, nullable = false, length = 10)
    private String tickerSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(name = "expense_ratio", precision = 5, scale = 2)
    private BigDecimal expenseRatio;

    @Column(name = "risk_rating", nullable = false)
    private int riskRating; // 1-5 (1=Low, 5=Very High)

    @Column(name = "current_nav", precision = 19, scale = 4)
    private BigDecimal currentNav;

    @Column(name = "fund_manager", length = 100)
    private String fundManager;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_investment", precision = 19, scale = 4)
    private BigDecimal minInvestment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Category {
        EQUITY, DEBT, HYBRID, ELSS
    }
}
