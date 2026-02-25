package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.dto.TransactionRequest;
import com.fsad.mutualfund.entity.PortfolioHolding;
import com.fsad.mutualfund.entity.Transaction;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("hasRole('INVESTOR')")
public class TransactionController {

    private final TransactionService transactionService;
    private final JwtUtil jwtUtil;

    public TransactionController(TransactionService transactionService, JwtUtil jwtUtil) {
        this.transactionService = transactionService;
        this.jwtUtil = jwtUtil;
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

        List<Map<String, Object>> result = transactions.stream().map(tx -> Map.<String, Object>of(
                "id", tx.getId(),
                "type", tx.getType().name(),
                "amount", tx.getAmount(),
                "status", tx.getStatus().name(),
                "description", tx.getDescription() != null ? tx.getDescription() : "",
                "createdAt", tx.getCreatedAt().toString()
        )).collect(Collectors.toList());

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
}
