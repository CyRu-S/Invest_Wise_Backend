package com.fsad.mutualfund.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundResponse {
    private Long id;
    private String schemeCode;
    private String fundName;
    private String tickerSymbol;
    private String category;
    private BigDecimal expenseRatio;
    private int riskRating;
    private BigDecimal currentNav;
    private BigDecimal oneYearReturn;
    private String navDate;
    private String fundHouse;
    private String fundManager;
    private String description;
    private BigDecimal minInvestment;
}
