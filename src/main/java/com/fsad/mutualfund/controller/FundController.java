package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.FundDetailResponse;
import com.fsad.mutualfund.dto.FundResponse;
import com.fsad.mutualfund.entity.MutualFund;
import com.fsad.mutualfund.service.FundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funds")
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    // Public endpoint — no auth required
    @GetMapping("/public")
    public ResponseEntity<List<FundResponse>> getAllFunds(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer maxRisk) {
        return ResponseEntity.ok(fundService.getAllFunds(category, maxRisk));
    }

    // Public endpoint — fund detail with analytics
    @GetMapping("/public/{id}")
    public ResponseEntity<FundDetailResponse> getFundDetail(@PathVariable Long id) {
        return ResponseEntity.ok(fundService.getFundDetail(id));
    }

    // Admin/Analyst: Create a new fund
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<FundResponse> createFund(@RequestBody MutualFund fund) {
        return ResponseEntity.ok(fundService.createFund(fund));
    }

    // Admin/Analyst: Update a fund
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<FundResponse> updateFund(@PathVariable Long id, @RequestBody MutualFund fund) {
        return ResponseEntity.ok(fundService.updateFund(id, fund));
    }

    // Admin: Delete a fund
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFund(@PathVariable Long id) {
        fundService.deleteFund(id);
        return ResponseEntity.noContent().build();
    }
}
