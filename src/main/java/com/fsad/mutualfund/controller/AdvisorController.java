package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.dto.PaymentIntentResponse;
import com.fsad.mutualfund.entity.AdvisorAppointment;
import com.fsad.mutualfund.entity.AdvisorAvailabilitySlot;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.AdvisorService;
import com.fsad.mutualfund.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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

        List<Map<String, Object>> result = advisors.stream().map(this::toAdvisorMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getAdvisorDetail(@PathVariable Long id) {
        AdvisorProfile advisor = advisorService.getAdvisorDetail(id);
        Map<String, Object> result = toAdvisorMap(advisor);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getAdvisorAvailability(@PathVariable Long id) {
        List<Map<String, Object>> result = advisorService.getAvailabilityByAdvisor(id)
                .stream()
                .map(this::toAvailabilityMap)
                .collect(Collectors.toList());

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
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        LocalDateTime scheduledAt = body.get("scheduledAt") != null
                ? LocalDateTime.parse(String.valueOf(body.get("scheduledAt")))
                : null;
        Long availabilitySlotId = body.get("availabilitySlotId") != null
                ? Long.valueOf(String.valueOf(body.get("availabilitySlotId")))
                : null;
        String notes = body.getOrDefault("notes", "").toString();

        AdvisorAppointment appointment = advisorService.bookAppointment(id, userId, scheduledAt, notes, availabilitySlotId);
        return ResponseEntity.ok(ApiResponse.success(
                "Appointment booked and paid from wallet successfully",
                toInvestorAppointmentMap(appointment)
        ));
    }

    @GetMapping("/appointments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getAppointments(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        List<Map<String, Object>> result = advisorService.getAppointmentsByInvestor(userId)
                .stream()
                .map(this::toInvestorAppointmentMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/appointments/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('INVESTOR')")
    public ResponseEntity<Map<String, Object>> rescheduleInvestorAppointment(
            @PathVariable Long appointmentId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        LocalDateTime scheduledAt = body.get("scheduledAt") != null
                ? LocalDateTime.parse(String.valueOf(body.get("scheduledAt")))
                : null;
        Long availabilitySlotId = body.get("availabilitySlotId") != null
                ? Long.valueOf(String.valueOf(body.get("availabilitySlotId")))
                : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;

        AdvisorAppointment appointment = advisorService.rescheduleAppointmentByInvestor(
                appointmentId,
                userId,
                scheduledAt,
                availabilitySlotId,
                notes
        );

        return ResponseEntity.ok(toInvestorAppointmentMap(appointment));
    }

    @PatchMapping("/appointments/{appointmentId}/cancel")
    @PreAuthorize("hasRole('INVESTOR')")
    public ResponseEntity<Map<String, Object>> cancelInvestorAppointment(
            @PathVariable Long appointmentId,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        AdvisorAppointment appointment = advisorService.cancelAppointmentByInvestor(appointmentId, userId);
        return ResponseEntity.ok(toInvestorAppointmentMap(appointment));
    }

    @GetMapping("/advisor-appointments")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<List<Map<String, Object>>> getAdvisorAppointments(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        List<Map<String, Object>> result = advisorService.getAppointmentsByAdvisorUser(userId)
                .stream()
                .map(this::toAdvisorAppointmentMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/advisor-appointments/{appointmentId}/status")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<Map<String, Object>> updateAdvisorAppointmentStatus(
            @PathVariable Long appointmentId,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        AdvisorAppointment.AppointmentStatus status = AdvisorAppointment.AppointmentStatus.valueOf(
                body.getOrDefault("status", "PENDING").toUpperCase()
        );

        AdvisorAppointment appointment = advisorService.updateAppointmentStatus(appointmentId, userId, status);
        return ResponseEntity.ok(toAdvisorAppointmentMap(appointment));
    }

    @GetMapping("/advisor-availability")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<List<Map<String, Object>>> getAdvisorAvailabilityByUser(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        List<Map<String, Object>> result = advisorService.getAvailabilityByAdvisorUser(userId)
                .stream()
                .map(this::toAvailabilityMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/advisor-availability")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<Map<String, Object>> createAdvisorAvailability(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        LocalDateTime startTime = LocalDateTime.parse(body.get("startTime"));
        LocalDateTime endTime = LocalDateTime.parse(body.get("endTime"));

        AdvisorAvailabilitySlot slot = advisorService.createAvailabilitySlot(userId, startTime, endTime);
        return ResponseEntity.ok(toAvailabilityMap(slot));
    }

    @DeleteMapping("/advisor-availability/{slotId}")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<ApiResponse> deleteAdvisorAvailability(
            @PathVariable Long slotId,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.replace("Bearer ", ""));
        advisorService.deleteAvailabilitySlot(slotId, userId);
        return ResponseEntity.ok(ApiResponse.success("Availability slot removed"));
    }

    private Map<String, Object> toAdvisorMap(AdvisorProfile advisor) {
        return Map.of(
                "id", advisor.getId(),
                "name", advisor.getUser().getFullName(),
                "email", advisor.getUser().getEmail(),
                "specialization", advisor.getSpecialization() != null ? advisor.getSpecialization() : "",
                "consultationFee", advisor.getConsultationFee(),
                "experienceYears", advisor.getExperienceYears(),
                "bio", advisor.getBio() != null ? advisor.getBio() : "",
                "averageRating", advisor.getAverageRating(),
                "totalReviews", advisor.getTotalReviews(),
                "availability", advisorService.getAvailabilityByAdvisor(advisor.getId()).stream()
                        .map(this::toAvailabilityMap)
                        .collect(Collectors.toList())
        );
    }

    private Map<String, Object> toInvestorAppointmentMap(AdvisorAppointment appointment) {
        return Map.of(
                "id", appointment.getId(),
                "advisorId", appointment.getAdvisor().getId(),
                "advisorName", appointment.getAdvisor().getUser().getFullName(),
                "specialization", appointment.getAdvisor().getSpecialization() != null ? appointment.getAdvisor().getSpecialization() : "",
                "scheduledAt", appointment.getScheduledAt() != null ? appointment.getScheduledAt().toString() : "",
                "status", appointment.getStatus().name(),
                "notes", appointment.getNotes() != null ? appointment.getNotes() : "",
                "createdAt", appointment.getCreatedAt() != null ? appointment.getCreatedAt().toString() : "",
                "updatedAt", appointment.getUpdatedAt() != null ? appointment.getUpdatedAt().toString() : "",
                "consultationFee", appointment.getAdvisor().getConsultationFee()
        );
    }

    private Map<String, Object> toAdvisorAppointmentMap(AdvisorAppointment appointment) {
        return Map.of(
                "id", appointment.getId(),
                "investorName", appointment.getInvestor().getFullName(),
                "investorEmail", appointment.getInvestor().getEmail(),
                "specialization", appointment.getAdvisor().getSpecialization() != null ? appointment.getAdvisor().getSpecialization() : "",
                "scheduledAt", appointment.getScheduledAt() != null ? appointment.getScheduledAt().toString() : "",
                "status", appointment.getStatus().name(),
                "notes", appointment.getNotes() != null ? appointment.getNotes() : "",
                "createdAt", appointment.getCreatedAt() != null ? appointment.getCreatedAt().toString() : "",
                "updatedAt", appointment.getUpdatedAt() != null ? appointment.getUpdatedAt().toString() : "",
                "consultationFee", appointment.getAdvisor().getConsultationFee()
        );
    }

    private Map<String, Object> toAvailabilityMap(AdvisorAvailabilitySlot slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", slot.getId());
        result.put("advisorId", slot.getAdvisor().getId());
        result.put("startTime", slot.getStartTime().toString());
        result.put("endTime", slot.getEndTime().toString());
        result.put("booked", slot.isBooked());
        result.put("appointmentId", slot.getAppointment() != null ? slot.getAppointment().getId() : null);
        return result;
    }
}
