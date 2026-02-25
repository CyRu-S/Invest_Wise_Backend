package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.dto.FundDetailResponse;
import com.fsad.mutualfund.dto.FundResponse;
import com.fsad.mutualfund.entity.MutualFund;
import com.fsad.mutualfund.entity.NavHistory;
import com.fsad.mutualfund.repository.MutualFundRepository;
import com.fsad.mutualfund.repository.NavHistoryRepository;
import com.fsad.mutualfund.service.FundService;
import com.fsad.mutualfund.utils.FinancialCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FundServiceImpl implements FundService {

    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.06"); // 6% annual

    private final MutualFundRepository fundRepository;
    private final NavHistoryRepository navHistoryRepository;

    public FundServiceImpl(MutualFundRepository fundRepository,
                           NavHistoryRepository navHistoryRepository) {
        this.fundRepository = fundRepository;
        this.navHistoryRepository = navHistoryRepository;
    }

    @Override
    public List<FundResponse> getAllFunds(String category, Integer maxRisk) {
        List<MutualFund> funds;

        if (category != null && maxRisk != null) {
            MutualFund.Category cat = MutualFund.Category.valueOf(category.toUpperCase());
            funds = fundRepository.findByCategoryAndRiskRatingLessThanEqual(cat, maxRisk);
        } else if (category != null) {
            MutualFund.Category cat = MutualFund.Category.valueOf(category.toUpperCase());
            funds = fundRepository.findByCategory(cat);
        } else if (maxRisk != null) {
            funds = fundRepository.findByRiskRatingLessThanEqual(maxRisk);
        } else {
            funds = fundRepository.findAll();
        }

        return funds.stream().map(this::toFundResponse).collect(Collectors.toList());
    }

    @Override
    public FundDetailResponse getFundDetail(Long fundId) {
        MutualFund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new RuntimeException("Fund not found: " + fundId));

        List<NavHistory> history = navHistoryRepository
                .findByMutualFundIdOrderByNavDateAsc(fundId);

        // Build analytics
        BigDecimal cagr = BigDecimal.ZERO;
        BigDecimal sharpeRatio = BigDecimal.ZERO;
        BigDecimal stdDev = BigDecimal.ZERO;
        BigDecimal oneYearReturn = BigDecimal.ZERO;

        if (history.size() >= 2) {
            NavHistory first = history.get(0);
            NavHistory last = history.get(history.size() - 1);
            long days = ChronoUnit.DAYS.between(first.getNavDate(), last.getNavDate());
            double years = days / 365.25;

            cagr = FinancialCalculator.calculateCAGR(
                    first.getNavValue(), last.getNavValue(), years);

            // Monthly returns for Sharpe
            List<BigDecimal> monthlyReturns = computeMonthlyReturns(history);
            if (monthlyReturns.size() >= 2) {
                sharpeRatio = FinancialCalculator.calculateSharpeRatio(monthlyReturns, RISK_FREE_RATE);
                stdDev = FinancialCalculator.calculateStandardDeviation(monthlyReturns);
            }

            // 1-year return
            oneYearReturn = FinancialCalculator.calculateReturn(
                    first.getNavValue(), last.getNavValue());
        }

        List<FundDetailResponse.NavPoint> navPoints = history.stream()
                .map(h -> FundDetailResponse.NavPoint.builder()
                        .date(h.getNavDate())
                        .value(h.getNavValue())
                        .build())
                .collect(Collectors.toList());

        return FundDetailResponse.builder()
                .id(fund.getId())
                .fundName(fund.getFundName())
                .tickerSymbol(fund.getTickerSymbol())
                .category(fund.getCategory().name())
                .expenseRatio(fund.getExpenseRatio())
                .riskRating(fund.getRiskRating())
                .currentNav(fund.getCurrentNav())
                .fundManager(fund.getFundManager())
                .description(fund.getDescription())
                .minInvestment(fund.getMinInvestment())
                .cagr(cagr)
                .sharpeRatio(sharpeRatio)
                .standardDeviation(stdDev)
                .oneYearReturn(oneYearReturn)
                .navHistory(navPoints)
                .build();
    }

    @Override
    @Transactional
    public FundResponse createFund(MutualFund fund) {
        MutualFund saved = fundRepository.save(fund);
        return toFundResponse(saved);
    }

    @Override
    @Transactional
    public FundResponse updateFund(Long fundId, MutualFund fundData) {
        MutualFund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new RuntimeException("Fund not found: " + fundId));

        fund.setFundName(fundData.getFundName());
        fund.setTickerSymbol(fundData.getTickerSymbol());
        fund.setCategory(fundData.getCategory());
        fund.setExpenseRatio(fundData.getExpenseRatio());
        fund.setRiskRating(fundData.getRiskRating());
        fund.setCurrentNav(fundData.getCurrentNav());
        fund.setFundManager(fundData.getFundManager());
        fund.setDescription(fundData.getDescription());
        fund.setMinInvestment(fundData.getMinInvestment());

        MutualFund updated = fundRepository.save(fund);
        return toFundResponse(updated);
    }

    @Override
    @Transactional
    public void deleteFund(Long fundId) {
        if (!fundRepository.existsById(fundId)) {
            throw new RuntimeException("Fund not found: " + fundId);
        }
        navHistoryRepository.deleteByMutualFundId(fundId);
        fundRepository.deleteById(fundId);
    }

    private List<BigDecimal> computeMonthlyReturns(List<NavHistory> history) {
        List<BigDecimal> returns = new ArrayList<>();
        int step = Math.max(1, history.size() / 12); // Approximate monthly intervals
        for (int i = step; i < history.size(); i += step) {
            BigDecimal prevNav = history.get(i - step).getNavValue();
            BigDecimal curNav = history.get(i).getNavValue();
            if (prevNav.compareTo(BigDecimal.ZERO) > 0) {
                returns.add(FinancialCalculator.calculateReturn(prevNav, curNav));
            }
        }
        return returns;
    }

    private FundResponse toFundResponse(MutualFund fund) {
        return FundResponse.builder()
                .id(fund.getId())
                .fundName(fund.getFundName())
                .tickerSymbol(fund.getTickerSymbol())
                .category(fund.getCategory().name())
                .expenseRatio(fund.getExpenseRatio())
                .riskRating(fund.getRiskRating())
                .currentNav(fund.getCurrentNav())
                .fundManager(fund.getFundManager())
                .description(fund.getDescription())
                .minInvestment(fund.getMinInvestment())
                .build();
    }
}
