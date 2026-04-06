package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.dto.TransactionRequest;
import com.fsad.mutualfund.entity.MutualFund;
import com.fsad.mutualfund.entity.PortfolioHolding;
import com.fsad.mutualfund.entity.Transaction;
import com.fsad.mutualfund.repository.MutualFundRepository;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("hasRole('INVESTOR')")
public class TransactionController {

    private final TransactionService transactionService;
    private final JwtUtil jwtUtil;
    private final MutualFundRepository mutualFundRepository;

    public TransactionController(TransactionService transactionService,
                                 JwtUtil jwtUtil,
                                 MutualFundRepository mutualFundRepository) {
        this.transactionService = transactionService;
        this.jwtUtil = jwtUtil;
        this.mutualFundRepository = mutualFundRepository;
    }

    @PostMapping("/buy")
    public ResponseEntity<ApiResponse> buyFund(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TransactionRequest request) {
        Long userId = extractUserId(authHeader);
        Transaction tx = transactionService.buyFund(userId, request);
        return ResponseEntity.ok(ApiResponse.success(tx.getDescription()));
    }

    @PostMapping("/sell")
    public ResponseEntity<ApiResponse> sellFund(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TransactionRequest request) {
        Long userId = extractUserId(authHeader);
        Transaction tx = transactionService.sellFund(userId, request);
        return ResponseEntity.ok(ApiResponse.success(tx.getDescription()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        List<Transaction> transactions = transactionService.getTransactionHistory(userId);

        List<Map<String, Object>> result = transactions.stream()
                .map(this::toTransactionMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/portfolio")
    public ResponseEntity<List<Map<String, Object>>> getPortfolio(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        List<PortfolioHolding> holdings = transactionService.getPortfolio(userId);

        List<Map<String, Object>> result = holdings.stream().map(h -> Map.<String, Object>of(
                "holdingId", h.getId(),
                "fundId", h.getMutualFund().getId(),
                "fundName", h.getMutualFund().getFundName(),
                "tickerSymbol", h.getMutualFund().getTickerSymbol(),
                "unitsOwned", h.getUnitsOwned(),
                "averageBuyPrice", h.getAverageBuyPrice(),
                "currentNav", h.getMutualFund().getCurrentNav(),
                "currentValue", h.getUnitsOwned().multiply(h.getMutualFund().getCurrentNav())
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getUserIdFromToken(token);
    }

    private Map<String, Object> toTransactionMap(Transaction tx) {
        MutualFund fund = resolveFund(tx.getReferenceId());
        String category = mapCategory(tx, fund);
        String cashflowType = mapCashflowType(tx);
        String balanceDirection = mapBalanceDirection(tx);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", tx.getId());
        payload.put("type", tx.getType().name());
        payload.put("cashflowType", cashflowType);
        payload.put("balanceDirection", balanceDirection);
        payload.put("category", category);
        payload.put("amount", tx.getAmount());
        payload.put("status", tx.getStatus().name());
        payload.put("description", tx.getDescription() != null ? tx.getDescription() : "");
        payload.put("createdAt", tx.getCreatedAt().toString());
        payload.put("referenceId", tx.getReferenceId() != null ? tx.getReferenceId() : "");
        payload.put("title", mapTitle(tx));
        payload.put("fundName", fund != null ? fund.getFundName() : "");
        payload.put("tickerSymbol", fund != null ? fund.getTickerSymbol() : "");
        return payload;
    }

    private MutualFund resolveFund(String referenceId) {
        if (referenceId == null || !referenceId.startsWith("FUND-")) {
            return null;
        }

        try {
            Long fundId = Long.parseLong(referenceId.substring("FUND-".length()));
            return mutualFundRepository.findById(fundId).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String mapCashflowType(Transaction tx) {
        if (isAdvisorRefund(tx)) {
            return "REFUND";
        }

        return switch (tx.getType()) {
            case DEPOSIT, SELL -> "INCOME";
            case BUY, FEE_PAYMENT -> "EXPENSE";
        };
    }

    private String mapBalanceDirection(Transaction tx) {
        return switch (tx.getType()) {
            case DEPOSIT, SELL -> "CREDIT";
            case BUY, FEE_PAYMENT -> "DEBIT";
        };
    }

    private String mapCategory(Transaction tx, MutualFund fund) {
        if (isAdvisorRefund(tx)) {
            return "Advisor Refunds";
        }

        return switch (tx.getType()) {
            case DEPOSIT -> "Wallet Funding";
            case BUY -> fund != null ? fund.getCategory().name() + " Investment" : "Investment";
            case SELL -> fund != null ? fund.getCategory().name() + " Redemption" : "Redemption";
            case FEE_PAYMENT -> "Advisor Fees";
        };
    }

    private String mapTitle(Transaction tx) {
        if (isAdvisorRefund(tx)) {
            return "Advisor Refund";
        }

        return switch (tx.getType()) {
            case DEPOSIT -> "Wallet Top-up";
            case BUY -> "Fund Purchase";
            case SELL -> "Fund Redemption";
            case FEE_PAYMENT -> "Advisor Fee";
        };
    }

    private boolean isAdvisorRefund(Transaction tx) {
        return tx.getType() == Transaction.TransactionType.DEPOSIT
                && tx.getReferenceId() != null
                && tx.getReferenceId().startsWith("APPOINTMENT-")
                && tx.getDescription() != null
                && tx.getDescription().toLowerCase().contains("refund");
    }
}
