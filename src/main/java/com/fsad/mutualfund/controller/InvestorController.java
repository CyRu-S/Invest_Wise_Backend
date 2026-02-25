package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.dto.RiskQuestionnaireRequest;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.InvestorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/investor")
@PreAuthorize("hasRole('INVESTOR')")
public class InvestorController {

    private final InvestorService investorService;
    private final JwtUtil jwtUtil;

    public InvestorController(InvestorService investorService, JwtUtil jwtUtil) {
        this.investorService = investorService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        InvestorProfile profile = investorService.getProfile(userId);

        Map<String, Object> response = Map.of(
                "riskToleranceScore", profile.getRiskToleranceScore(),
                "riskCategory", profile.getRiskCategory().name(),
                "walletBalance", profile.getWalletBalance(),
                "investmentHorizon", profile.getInvestmentHorizon() != null ? profile.getInvestmentHorizon() : "");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/risk-profile")
    public ResponseEntity<ApiResponse> submitRiskQuestionnaire(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody RiskQuestionnaireRequest request) {
        Long userId = extractUserId(authHeader);
        InvestorProfile profile = investorService.processRiskQuestionnaire(userId, request);

        return ResponseEntity.ok(ApiResponse.success(
                "Risk profile updated! Your category: " + profile.getRiskCategory().name(),
                Map.of(
                        "score", profile.getRiskToleranceScore(),
                        "category", profile.getRiskCategory().name())));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse> deposit(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, BigDecimal> body) {
        Long userId = extractUserId(authHeader);
        BigDecimal amount = body.get("amount");
        InvestorProfile profile = investorService.depositToWallet(userId, amount);

        return ResponseEntity.ok(ApiResponse.success(
                "Deposited successfully",
                Map.of("walletBalance", profile.getWalletBalance())));
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getUserIdFromToken(token);
    }
}
