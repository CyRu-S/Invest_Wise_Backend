package com.fsad.mutualfund.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundDetailResponse {
    private Long id;
    private String fundName;
    private String tickerSymbol;
    private String category;
    private BigDecimal expenseRatio;
    private int riskRating;
    private BigDecimal currentNav;
    private String fundManager;
    private String description;
    private BigDecimal minInvestment;

    // Analytics
    private BigDecimal cagr;
    private BigDecimal sharpeRatio;
    private BigDecimal standardDeviation;
    private BigDecimal oneYearReturn;

    // NAV History for charting
    private List<NavPoint> navHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NavPoint {
        private LocalDate date;
        private BigDecimal value;
    }
}
