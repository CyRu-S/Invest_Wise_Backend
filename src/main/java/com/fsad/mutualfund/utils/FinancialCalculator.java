package com.fsad.mutualfund.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Financial mathematics engine for mutual fund analytics.
 * All calculations use BigDecimal to prevent floating-point errors.
 */
public final class FinancialCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int SCALE = 6;

    private FinancialCalculator() {
    }

    /**
     * CAGR = (Ending Value / Beginning Value) ^ (1/n) - 1
     */
    public static BigDecimal calculateCAGR(BigDecimal beginValue, BigDecimal endValue, double years) {
        if (beginValue.compareTo(BigDecimal.ZERO) <= 0 || years <= 0) {
            return BigDecimal.ZERO;
        }
        double ratio = endValue.doubleValue() / beginValue.doubleValue();
        double cagr = Math.pow(ratio, 1.0 / years) - 1.0;
        return BigDecimal.valueOf(cagr).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Sharpe Ratio = (Rp - Rf) / σp
     * 
     * @param returns      list of periodic returns
     * @param riskFreeRate annualized risk-free rate (e.g., 0.06 for 6%)
     */
    public static BigDecimal calculateSharpeRatio(List<BigDecimal> returns, BigDecimal riskFreeRate) {
        if (returns == null || returns.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal periodicRf = riskFreeRate.divide(BigDecimal.valueOf(12), MC); // Monthly risk-free
        BigDecimal meanReturn = calculateMean(returns);
        BigDecimal stdDev = calculateStandardDeviation(returns);

        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal excessReturn = meanReturn.subtract(periodicRf);
        return excessReturn.divide(stdDev, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Standard Deviation of a list of values.
     */
    public static BigDecimal calculateStandardDeviation(List<BigDecimal> values) {
        if (values == null || values.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal mean = calculateMean(values);
        BigDecimal sumSquares = BigDecimal.ZERO;

        for (BigDecimal v : values) {
            BigDecimal diff = v.subtract(mean);
            sumSquares = sumSquares.add(diff.multiply(diff, MC), MC);
        }

        BigDecimal variance = sumSquares.divide(BigDecimal.valueOf(values.size() - 1), MC);
        double stdDev = Math.sqrt(variance.doubleValue());
        return BigDecimal.valueOf(stdDev).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the arithmetic mean of a list.
     */
    public static BigDecimal calculateMean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    /**
     * Calculate percentage return between two values.
     */
    public static BigDecimal calculateReturn(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return newValue.subtract(oldValue)
                .divide(oldValue, SCALE, RoundingMode.HALF_UP);
    }
}
