package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.dto.PaymentIntentResponse;
import com.fsad.mutualfund.entity.AdvisorAppointment;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.AdvisorService;
import com.fsad.mutualfund.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/advisors")
public class AdvisorController {

    private final AdvisorService advisorService;
    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    public AdvisorController(AdvisorService advisorService, PaymentService paymentService, JwtUtil jwtUtil) {
        this.advisorService = advisorService;
        this.paymentService = paymentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getAllAdvisors() {
        List<AdvisorProfile> advisors = advisorService.getAllAdvisors();

        List<Map<String, Object>> result = advisors.stream().map(a -> Map.<String, Object>of(
                "id", a.getId(),
                "name", a.getUser().getFullName(),
                "email", a.getUser().getEmail(),
                "specialization", a.getSpecialization() != null ? a.getSpecialization() : "",
                "consultationFee", a.getConsultationFee(),
                "experienceYears", a.getExperienceYears(),
                "bio", a.getBio() != null ? a.getBio() : "",
                "averageRating", a.getAverageRating(),
                "totalReviews", a.getTotalReviews())).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getAdvisorDetail(@PathVariable Long id) {
        AdvisorProfile a = advisorService.getAdvisorDetail(id);

        Map<String, Object> result = Map.of(
                "id", a.getId(),
                "name", a.getUser().getFullName(),
                "email", a.getUser().getEmail(),
                "specialization", a.getSpecialization() != null ? a.getSpecialization() : "",
                "consultationFee", a.getConsultationFee(),
                "experienceYears", a.getExperienceYears(),
                "bio", a.getBio() != null ? a.getBio() : "",
                "averageRating", a.getAverageRating(),
                "totalReviews", a.getTotalReviews());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/hire")
    @PreAuthorize("hasRole('INVESTOR')")
    public ResponseEntity<PaymentIntentResponse> hireAdvisor(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.createPaymentIntent(id));
    }

    @PostMapping("/{id}/book")
    @PreAuthorize("hasRole('INVESTOR')")
    public ResponseEntity<ApiResponse> bookAppointment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        LocalDateTime scheduledAt = LocalDateTime.parse(body.get("scheduledAt"));
        String notes = body.getOrDefault("notes", "");

        advisorService.bookAppointment(id, userId, scheduledAt, notes);
        return ResponseEntity.ok(ApiResponse.success("Appointment booked successfully"));
    }

    @GetMapping("/appointments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getAppointments(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        List<AdvisorAppointment> appointments = advisorService.getAppointmentsByInvestor(userId);

        List<Map<String, Object>> result = appointments.stream().map(a -> Map.<String, Object>of(
                "id", a.getId(),
                "advisorName", a.getAdvisor().getUser().getFullName(),
                "specialization", a.getAdvisor().getSpecialization() != null ? a.getAdvisor().getSpecialization() : "",
                "scheduledAt", a.getScheduledAt() != null ? a.getScheduledAt().toString() : "",
                "status", a.getStatus().name(),
                "notes", a.getNotes() != null ? a.getNotes() : "",
                "createdAt", a.getCreatedAt().toString()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}

