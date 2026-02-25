package com.fsad.mutualfund.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "nav_history", indexes = {
        @Index(name = "idx_nav_fund_date", columnList = "fund_id, nav_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private MutualFund mutualFund;

    @Column(name = "nav_date", nullable = false)
    private LocalDate navDate;

    @Column(name = "nav_value", precision = 19, scale = 4, nullable = false)
    private BigDecimal navValue;
}
