package com.fsad.mutualfund.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_holdings", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "investor_id", "fund_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private User investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private MutualFund mutualFund;

    @Column(name = "units_owned", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitsOwned;

    @Column(name = "average_buy_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal averageBuyPrice;
}
