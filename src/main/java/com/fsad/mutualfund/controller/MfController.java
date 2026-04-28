package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.FundDetailResponse;
import com.fsad.mutualfund.dto.FundResponse;
import com.fsad.mutualfund.service.ExternalMfService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mf")
@CrossOrigin(origins = "*")
public class MfController {

    private final ExternalMfService externalMfService;

    public MfController(ExternalMfService externalMfService) {
        this.externalMfService = externalMfService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<FundResponse>> getAllFunds(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer maxRisk,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(externalMfService.getAllFunds(query, category, maxRisk, limit));
    }

    @GetMapping("/{code}")
    public ResponseEntity<FundDetailResponse> getFundDetails(@PathVariable String code) {
        return ResponseEntity.ok(externalMfService.getFundDetails(code));
    }
}
