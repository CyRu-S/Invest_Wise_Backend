package com.fsad.mutualfund.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fsad.mutualfund.dto.FundDetailResponse;
import com.fsad.mutualfund.dto.FundResponse;
import com.fsad.mutualfund.entity.MutualFund;
import com.fsad.mutualfund.repository.MutualFundRepository;
import com.fsad.mutualfund.utils.FinancialCalculator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class ExternalMfService {

    private static final String MFAPI_URL = "https://api.mfapi.in/mf";
    private static final DateTimeFormatter MFAPI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Duration FUND_LIST_TTL = Duration.ofHours(12);
    private static final Duration FUND_DETAIL_TTL = Duration.ofHours(6);
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.06");
    private static final int DEFAULT_LIMIT = 24;
    private static final int MAX_LIMIT = 48;

    private final RestTemplate restTemplate;
    private final MutualFundRepository fundRepository;
    private final Object fundListLock = new Object();
    private final ConcurrentMap<String, CachedValue<MfApiFundDetail>> fundDetailCache = new ConcurrentHashMap<>();

    private volatile CachedValue<List<MfApiFundSummary>> fundListCache;

    public ExternalMfService(RestTemplateBuilder restTemplateBuilder,
                             MutualFundRepository fundRepository) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
        this.fundRepository = fundRepository;
        this.fundListCache = CachedValue.expired();
    }

    public List<FundResponse> getAllFunds(String query, String category, Integer maxRisk, Integer limit) {
        int resolvedLimit = Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        String normalizedQuery = normalizeQuery(query);
        boolean recentOnly = normalizedQuery.isBlank();
        List<MfApiFundSummary> filteredSummaries = rankAndFilterFundSummaries(getAllFundSummaries(), query);
        int candidateLimit = determineCandidateLimit(resolvedLimit, query, category, maxRisk);

        List<FundResponse> funds = filteredSummaries.stream()
                .limit(candidateLimit)
                .parallel()
                .map(this::buildFundSummary)
                .filter(Objects::nonNull)
                .filter(fund -> !recentOnly || isRecentlyUpdated(fund.getNavDate()))
                .filter(fund -> matchesCategory(fund, category))
                .filter(fund -> maxRisk == null || fund.getRiskRating() <= maxRisk)
                .sorted(buildFundComparator(normalizedQuery))
                .limit(resolvedLimit)
                .collect(Collectors.toList());

        return funds;
    }

    public FundDetailResponse getFundDetails(String schemeCode) {
        MfApiFundDetail detail = getCachedFundDetail(schemeCode);
        MutualFund trackedFund = upsertTrackedFund(detail);
        List<FundDetailResponse.NavPoint> navHistory = buildNavHistory(detail);
        AnalyticsSnapshot analytics = buildAnalytics(navHistory);
        MutualFund.Category category = mapCategory(detail.getMeta().getSchemeCategory());

        return FundDetailResponse.builder()
                .id(trackedFund.getId())
                .localFundId(trackedFund.getId())
                .schemeCode(String.valueOf(detail.getMeta().getSchemeCode()))
                .fundName(detail.getMeta().getSchemeName())
                .tickerSymbol(resolveTickerSymbol(trackedFund))
                .category(category.name())
                .expenseRatio(trackedFund.getExpenseRatio())
                .riskRating(resolveRiskRating(trackedFund, detail.getMeta().getSchemeCategory()))
                .currentNav(resolveCurrentNav(detail))
                .navDate(resolveLatestNavDate(detail))
                .fundHouse(detail.getMeta().getFundHouse())
                .fundManager(resolveFundManager(trackedFund, detail))
                .description(resolveDescription(trackedFund, detail))
                .minInvestment(trackedFund.getMinInvestment())
                .cagr(analytics.cagr())
                .sharpeRatio(analytics.sharpeRatio())
                .standardDeviation(analytics.standardDeviation())
                .oneYearReturn(analytics.oneYearReturn())
                .navHistory(navHistory)
                .build();
    }

    public MutualFund refreshTrackedFund(String schemeCode) {
        if (schemeCode == null || schemeCode.isBlank()) {
            throw new IllegalArgumentException("Scheme code is required");
        }

        return upsertTrackedFund(getCachedFundDetail(schemeCode));
    }

    private List<MfApiFundSummary> getAllFundSummaries() {
        CachedValue<List<MfApiFundSummary>> cached = fundListCache;
        if (cached.isValid()) {
            return cached.value();
        }

        synchronized (fundListLock) {
            cached = fundListCache;
            if (cached.isValid()) {
                return cached.value();
            }

            MfApiFundSummary[] response = restTemplate.getForObject(MFAPI_URL, MfApiFundSummary[].class);
            List<MfApiFundSummary> summaries = response == null
                    ? List.of()
                    : java.util.Arrays.stream(response)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(MfApiFundSummary::getSchemeName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            fundListCache = CachedValue.of(summaries, FUND_LIST_TTL);
            return summaries;
        }
    }

    private MfApiFundDetail getCachedFundDetail(String schemeCode) {
        String normalizedCode = normalizeSchemeCode(schemeCode);
        CachedValue<MfApiFundDetail> cached = fundDetailCache.get(normalizedCode);
        if (cached != null && cached.isValid()) {
            return cached.value();
        }

        MfApiFundDetail detail = restTemplate.getForObject(MFAPI_URL + "/" + normalizedCode, MfApiFundDetail.class);
        if (detail == null || detail.getMeta() == null || detail.getData() == null || detail.getData().isEmpty()) {
            throw new RuntimeException("MFAPI returned no detail for scheme " + normalizedCode);
        }

        fundDetailCache.put(normalizedCode, CachedValue.of(detail, FUND_DETAIL_TTL));
        return detail;
    }

    private List<MfApiFundSummary> rankAndFilterFundSummaries(List<MfApiFundSummary> summaries, String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            return summaries.stream()
                    .sorted(Comparator
                            .comparing(MfApiFundSummary::getSchemeCode, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(MfApiFundSummary::getSchemeName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        return summaries.stream()
                .filter(summary -> containsQuery(summary, normalizedQuery))
                .sorted(Comparator
                        .comparingInt((MfApiFundSummary summary) -> queryScore(summary, normalizedQuery))
                        .thenComparing(MfApiFundSummary::getSchemeName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean containsQuery(MfApiFundSummary summary, String query) {
        return normalizeQuery(summary.getSchemeName()).contains(query)
                || String.valueOf(summary.getSchemeCode()).contains(query);
    }

    private int queryScore(MfApiFundSummary summary, String query) {
        String schemeName = normalizeQuery(summary.getSchemeName());
        String schemeCode = String.valueOf(summary.getSchemeCode());

        if (schemeCode.equals(query)) {
            return 0;
        }
        if (schemeName.startsWith(query)) {
            return 1;
        }
        if (schemeName.contains(query)) {
            return 2;
        }
        return 3;
    }

    private Comparator<FundResponse> buildFundComparator(String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return Comparator
                    .comparing((FundResponse fund) -> parseNavDate(fund.getNavDate()), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparingInt(fund -> categoryPriority(fund.getCategory()))
                    .thenComparing(FundResponse::getOneYearReturn, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(FundResponse::getFundName, String.CASE_INSENSITIVE_ORDER);
        }

        return Comparator
                .comparingInt((FundResponse fund) -> {
                    String schemeCode = fund.getSchemeCode() == null ? "" : fund.getSchemeCode();
                    String fundName = normalizeQuery(fund.getFundName());
                    if (schemeCode.equals(normalizedQuery)) {
                        return 0;
                    }
                    if (fundName.startsWith(normalizedQuery)) {
                        return 1;
                    }
                    return 2;
                })
                .thenComparing(FundResponse::getFundName, String.CASE_INSENSITIVE_ORDER);
    }

    private FundResponse buildFundSummary(MfApiFundSummary summary) {
        try {
            MfApiFundDetail detail = getCachedFundDetail(String.valueOf(summary.getSchemeCode()));
            List<FundDetailResponse.NavPoint> navHistory = buildNavHistory(detail);
            AnalyticsSnapshot analytics = buildAnalytics(navHistory);
            Optional<MutualFund> trackedFund = fundRepository.findByExternalSchemeCode(String.valueOf(summary.getSchemeCode()));
            MutualFund.Category category = mapCategory(detail.getMeta().getSchemeCategory());

            return FundResponse.builder()
                    .id(trackedFund.map(MutualFund::getId).orElse(null))
                    .schemeCode(String.valueOf(summary.getSchemeCode()))
                    .fundName(detail.getMeta().getSchemeName())
                    .tickerSymbol(trackedFund.map(this::resolveTickerSymbol).orElse(null))
                    .category(category.name())
                    .expenseRatio(trackedFund.map(MutualFund::getExpenseRatio).orElse(null))
                    .riskRating(resolveRiskRating(trackedFund.orElse(null), detail.getMeta().getSchemeCategory()))
                    .currentNav(resolveCurrentNav(detail))
                    .oneYearReturn(analytics.oneYearReturn())
                    .navDate(resolveLatestNavDate(detail))
                    .fundHouse(detail.getMeta().getFundHouse())
                    .fundManager(trackedFund.map(fund -> resolveFundManager(fund, detail)).orElse(detail.getMeta().getFundHouse()))
                    .description(trackedFund.map(fund -> resolveDescription(fund, detail)).orElse(buildDefaultDescription(detail)))
                    .minInvestment(trackedFund.map(MutualFund::getMinInvestment).orElse(null))
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    private List<FundDetailResponse.NavPoint> buildNavHistory(MfApiFundDetail detail) {
        List<FundDetailResponse.NavPoint> allPoints = detail.getData().stream()
                .map(this::toNavPoint)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FundDetailResponse.NavPoint::getDate))
                .toList();

        if (allPoints.isEmpty()) {
            return List.of();
        }

        LocalDate latestDate = allPoints.get(allPoints.size() - 1).getDate();
        LocalDate oneYearAgo = latestDate.minusYears(1);

        List<FundDetailResponse.NavPoint> trailingYear = allPoints.stream()
                .filter(point -> !point.getDate().isBefore(oneYearAgo))
                .toList();

        return trailingYear.isEmpty() ? allPoints : trailingYear;
    }

    private AnalyticsSnapshot buildAnalytics(List<FundDetailResponse.NavPoint> navHistory) {
        if (navHistory.size() < 2) {
            return AnalyticsSnapshot.empty();
        }

        FundDetailResponse.NavPoint first = navHistory.get(0);
        FundDetailResponse.NavPoint last = navHistory.get(navHistory.size() - 1);
        long daysBetween = ChronoUnit.DAYS.between(first.getDate(), last.getDate());
        double years = daysBetween / 365.25d;

        BigDecimal oneYearReturn = FinancialCalculator.calculateReturn(first.getValue(), last.getValue());
        BigDecimal cagr = years >= 1
                ? FinancialCalculator.calculateCAGR(first.getValue(), last.getValue(), years)
                : oneYearReturn;
        List<BigDecimal> periodicReturns = computePeriodicReturns(navHistory);
        BigDecimal standardDeviation = periodicReturns.size() >= 2
                ? FinancialCalculator.calculateStandardDeviation(periodicReturns)
                : BigDecimal.ZERO;
        BigDecimal sharpeRatio = periodicReturns.size() >= 2
                ? FinancialCalculator.calculateSharpeRatio(periodicReturns, RISK_FREE_RATE)
                : BigDecimal.ZERO;

        return new AnalyticsSnapshot(cagr, sharpeRatio, standardDeviation, oneYearReturn);
    }

    private List<BigDecimal> computePeriodicReturns(List<FundDetailResponse.NavPoint> history) {
        if (history.size() < 2) {
            return List.of();
        }

        int step = Math.max(1, history.size() / 12);
        List<BigDecimal> returns = new ArrayList<>();
        for (int index = step; index < history.size(); index += step) {
            BigDecimal previousNav = history.get(index - step).getValue();
            BigDecimal currentNav = history.get(index).getValue();
            if (previousNav.compareTo(BigDecimal.ZERO) > 0) {
                returns.add(FinancialCalculator.calculateReturn(previousNav, currentNav));
            }
        }
        return returns;
    }

    private FundDetailResponse.NavPoint toNavPoint(MfApiNavPoint point) {
        if (point == null || point.getDate() == null || point.getNav() == null) {
            return null;
        }

        try {
            return FundDetailResponse.NavPoint.builder()
                    .date(LocalDate.parse(point.getDate(), MFAPI_DATE_FORMATTER))
                    .value(new BigDecimal(point.getNav()).setScale(4, RoundingMode.HALF_UP))
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    private MutualFund upsertTrackedFund(MfApiFundDetail detail) {
        String schemeCode = String.valueOf(detail.getMeta().getSchemeCode());
        MutualFund.Category category = mapCategory(detail.getMeta().getSchemeCategory());
        BigDecimal currentNav = resolveCurrentNav(detail);

        MutualFund fund = fundRepository.findByExternalSchemeCode(schemeCode)
                .or(() -> fundRepository.findByFundName(detail.getMeta().getSchemeName()))
                .orElseGet(MutualFund::new);

        if (fund.getId() == null) {
            fund.setTickerSymbol(generateTickerSymbol(schemeCode));
            fund.setMinInvestment(null);
            fund.setExpenseRatio(null);
        }

        fund.setExternalSchemeCode(schemeCode);
        fund.setFundName(detail.getMeta().getSchemeName());
        fund.setCategory(category);
        fund.setRiskRating(resolveRiskRating(fund, detail.getMeta().getSchemeCategory()));
        fund.setCurrentNav(currentNav);

        if (isBlank(fund.getFundManager())) {
            fund.setFundManager(detail.getMeta().getFundHouse());
        }

        if (isBlank(fund.getDescription())) {
            fund.setDescription(buildDefaultDescription(detail));
        }

        return fundRepository.save(fund);
    }

    private String generateTickerSymbol(String schemeCode) {
        return fundRepository.findByExternalSchemeCode(schemeCode)
                .map(MutualFund::getTickerSymbol)
                .orElse("MF" + schemeCode);
    }

    private MutualFund.Category mapCategory(String schemeCategory) {
        String normalized = normalizeQuery(schemeCategory);
        if (normalized.contains("elss")) {
            return MutualFund.Category.ELSS;
        }
        if (normalized.contains("equity")) {
            return MutualFund.Category.EQUITY;
        }
        if (normalized.contains("hybrid")) {
            return MutualFund.Category.HYBRID;
        }
        if (normalized.contains("debt")) {
            return MutualFund.Category.DEBT;
        }
        return MutualFund.Category.OTHER;
    }

    private int resolveRiskRating(MutualFund fund, String schemeCategory) {
        if (fund != null && fund.getRiskRating() > 0) {
            return fund.getRiskRating();
        }

        String normalized = normalizeQuery(schemeCategory);
        if (normalized.contains("small cap") || normalized.contains("mid cap") || normalized.contains("sectoral") || normalized.contains("thematic")) {
            return 5;
        }
        if (normalized.contains("elss")) {
            return 4;
        }
        if (normalized.contains("equity")) {
            return 4;
        }
        if (normalized.contains("hybrid")) {
            return 3;
        }
        if (normalized.contains("liquid") || normalized.contains("overnight")) {
            return 1;
        }
        if (normalized.contains("debt")) {
            return 2;
        }
        return 3;
    }

    private String resolveTickerSymbol(MutualFund fund) {
        if (fund == null) {
            return null;
        }
        return fund.getTickerSymbol();
    }

    private String resolveFundManager(MutualFund fund, MfApiFundDetail detail) {
        if (fund != null && !isBlank(fund.getFundManager())) {
            return fund.getFundManager();
        }
        return detail.getMeta().getFundHouse();
    }

    private String resolveDescription(MutualFund fund, MfApiFundDetail detail) {
        if (fund != null && !isBlank(fund.getDescription())) {
            return fund.getDescription();
        }
        return buildDefaultDescription(detail);
    }

    private String buildDefaultDescription(MfApiFundDetail detail) {
        return String.format(
                Locale.ENGLISH,
                "%s strategy from %s.",
                nullSafe(detail.getMeta().getSchemeCategory(), "Mutual fund"),
                nullSafe(detail.getMeta().getFundHouse(), "the listed fund house")
        );
    }

    private BigDecimal resolveCurrentNav(MfApiFundDetail detail) {
        if (detail.getData() == null || detail.getData().isEmpty()) {
            return null;
        }

        return Optional.ofNullable(detail.getData().get(0))
                .map(MfApiNavPoint::getNav)
                .filter(value -> !value.isBlank())
                .map(BigDecimal::new)
                .map(value -> value.setScale(4, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private String resolveLatestNavDate(MfApiFundDetail detail) {
        if (detail.getData() == null || detail.getData().isEmpty()) {
            return null;
        }
        return detail.getData().get(0).getDate();
    }

    private int determineCandidateLimit(int resolvedLimit, String query, String category, Integer maxRisk) {
        boolean filteredRequest = !normalizeQuery(query).isBlank() || !isBlank(category) || maxRisk != null;
        if (!filteredRequest) {
            return 96;
        }
        return Math.min(Math.max(resolvedLimit * 4, 80), 160);
    }

    private boolean isRecentlyUpdated(String navDate) {
        LocalDate parsedDate = parseNavDate(navDate);
        return parsedDate != null && !parsedDate.isBefore(LocalDate.now().minusDays(10));
    }

    private int categoryPriority(String category) {
        if (category == null) {
            return 5;
        }

        return switch (category.toUpperCase(Locale.ENGLISH)) {
            case "EQUITY" -> 0;
            case "HYBRID" -> 1;
            case "DEBT" -> 2;
            case "ELSS" -> 3;
            case "OTHER" -> 4;
            default -> 5;
        };
    }

    private LocalDate parseNavDate(String navDate) {
        if (isBlank(navDate)) {
            return null;
        }

        try {
            return LocalDate.parse(navDate, MFAPI_DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean matchesCategory(FundResponse fund, String category) {
        if (isBlank(category)) {
            return true;
        }
        return category.trim().equalsIgnoreCase(fund.getCategory());
    }

    private String normalizeQuery(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private String normalizeSchemeCode(String schemeCode) {
        String normalized = schemeCode == null ? "" : schemeCode.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Scheme code is required");
        }
        return normalized;
    }

    private String nullSafe(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record CachedValue<T>(T value, Instant expiresAt) {
        static <T> CachedValue<T> of(T value, Duration ttl) {
            return new CachedValue<>(value, Instant.now().plus(ttl));
        }

        static <T> CachedValue<T> expired() {
            return new CachedValue<>(null, Instant.EPOCH);
        }

        boolean isValid() {
            return value != null && Instant.now().isBefore(expiresAt);
        }
    }

    private record AnalyticsSnapshot(
            BigDecimal cagr,
            BigDecimal sharpeRatio,
            BigDecimal standardDeviation,
            BigDecimal oneYearReturn
    ) {
        static AnalyticsSnapshot empty() {
            return new AnalyticsSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MfApiFundSummary {
        private Integer schemeCode;
        private String schemeName;

        public Integer getSchemeCode() {
            return schemeCode;
        }

        public void setSchemeCode(Integer schemeCode) {
            this.schemeCode = schemeCode;
        }

        public String getSchemeName() {
            return schemeName;
        }

        public void setSchemeName(String schemeName) {
            this.schemeName = schemeName;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MfApiFundDetail {
        private MfApiFundMeta meta;
        private List<MfApiNavPoint> data;

        public MfApiFundMeta getMeta() {
            return meta;
        }

        public void setMeta(MfApiFundMeta meta) {
            this.meta = meta;
        }

        public List<MfApiNavPoint> getData() {
            return data;
        }

        public void setData(List<MfApiNavPoint> data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MfApiFundMeta {
        @JsonProperty("fund_house")
        private String fundHouse;

        @JsonProperty("scheme_category")
        private String schemeCategory;

        @JsonProperty("scheme_code")
        private Integer schemeCode;

        @JsonProperty("scheme_name")
        private String schemeName;

        public String getFundHouse() {
            return fundHouse;
        }

        public void setFundHouse(String fundHouse) {
            this.fundHouse = fundHouse;
        }

        public String getSchemeCategory() {
            return schemeCategory;
        }

        public void setSchemeCategory(String schemeCategory) {
            this.schemeCategory = schemeCategory;
        }

        public Integer getSchemeCode() {
            return schemeCode;
        }

        public void setSchemeCode(Integer schemeCode) {
            this.schemeCode = schemeCode;
        }

        public String getSchemeName() {
            return schemeName;
        }

        public void setSchemeName(String schemeName) {
            this.schemeName = schemeName;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MfApiNavPoint {
        private String date;
        private String nav;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getNav() {
            return nav;
        }

        public void setNav(String nav) {
            this.nav = nav;
        }
    }
}
