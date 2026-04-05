package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.entity.AdminAuditLog;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.AdminAuditLogRepository;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvestorProfileRepository investorProfileRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            InvestorProfileRepository investorProfileRepository,
            AdvisorProfileRepository advisorProfileRepository,
            AdminAuditLogRepository adminAuditLogRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.investorProfileRepository = investorProfileRepository;
        this.advisorProfileRepository = advisorProfileRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> result = userRepository.findAll().stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse> createUser(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String fullName = body.getOrDefault("fullName", "").trim();
        String roleValue = body.getOrDefault("role", "INVESTOR").trim().toUpperCase();

        if (email.isBlank() || fullName.isBlank()) {
            throw new RuntimeException("Full name and email are required");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered: " + email);
        }

        User.Role role = User.Role.valueOf(roleValue);
        String tempPassword = body.getOrDefault("password", generateTemporaryPassword());

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(tempPassword))
                .fullName(fullName)
                .role(role)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .suspended(false)
                .build();

        User savedUser = userRepository.save(user);
        createRoleProfile(savedUser, role);
        logAction(extractActor(request), savedUser, "USER_CREATED", "USER", savedUser.getEmail(),
                "Created user with role " + role.name());

        return ResponseEntity.ok(ApiResponse.success(
                "User created successfully",
                Map.of(
                        "user", toUserMap(savedUser),
                        "temporaryPassword", tempPassword
                )
        ));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        logAction(extractActor(request), user, "USER_DELETED", "USER", user.getEmail(), "Deleted user account");
        userRepository.delete(user);
        return ResponseEntity.ok(ApiResponse.success("User deleted"));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        User.Role previousRole = user.getRole();
        User.Role role = User.Role.valueOf(body.get("role").toUpperCase());
        user.setRole(role);
        User updatedUser = userRepository.save(user);

        logAction(extractActor(request), updatedUser, "ROLE_UPDATED", "USER", updatedUser.getEmail(),
                "Role changed from " + previousRole.name() + " to " + role.name());

        return ResponseEntity.ok(ApiResponse.success("Role updated to " + role.name()));
    }

    @PatchMapping("/users/{id}/suspension")
    public ResponseEntity<ApiResponse> updateSuspension(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        boolean suspended = Boolean.parseBoolean(String.valueOf(body.getOrDefault("suspended", false)));
        String reason = String.valueOf(body.getOrDefault("reason", "")).trim();

        user.setSuspended(suspended);
        User updatedUser = userRepository.save(user);

        logAction(extractActor(request), updatedUser,
                suspended ? "USER_SUSPENDED" : "USER_REACTIVATED",
                "USER",
                updatedUser.getEmail(),
                reason.isBlank() ? (suspended ? "Account suspended" : "Account reactivated") : reason);

        return ResponseEntity.ok(ApiResponse.success(
                suspended ? "User suspended successfully" : "User reactivated successfully"
        ));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<Map<String, Object>>> getAuditLogs() {
        List<Map<String, Object>> result = adminAuditLogRepository.findTop25ByOrderByCreatedAtDesc()
                .stream()
                .map(log -> Map.<String, Object>of(
                        "id", log.getId(),
                        "actorName", log.getActor() != null ? log.getActor().getFullName() : "System",
                        "action", log.getAction(),
                        "targetType", log.getTargetType(),
                        "targetIdentifier", log.getTargetIdentifier() != null ? log.getTargetIdentifier() : "",
                        "details", log.getDetails() != null ? log.getDetails() : "",
                        "createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : ""
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalUsers = userRepository.count();
        long investors = userRepository.findByRole(User.Role.INVESTOR).size();
        long advisors = userRepository.findByRole(User.Role.ADVISOR).size();
        long analysts = userRepository.findByRole(User.Role.ANALYST).size();
        long admins = userRepository.findByRole(User.Role.ADMIN).size();
        long suspendedUsers = userRepository.findAll().stream().filter(User::isSuspended).count();

        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "investors", investors,
                "advisors", advisors,
                "analysts", analysts,
                "admins", admins,
                "suspendedUsers", suspendedUsers
        ));
    }

    private Map<String, Object> toUserMap(User user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "authProvider", user.getAuthProvider().name(),
                "verified", user.isVerified(),
                "suspended", user.isSuspended(),
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
        );
    }

    private void createRoleProfile(User user, User.Role role) {
        if (role == User.Role.INVESTOR) {
            investorProfileRepository.save(InvestorProfile.builder()
                    .user(user)
                    .riskToleranceScore(0)
                    .riskCategory(InvestorProfile.RiskCategory.MODERATE)
                    .walletBalance(new BigDecimal("100000.0000"))
                    .build());
        } else if (role == User.Role.ADVISOR) {
            advisorProfileRepository.save(AdvisorProfile.builder()
                    .user(user)
                    .consultationFee(new BigDecimal("50.00"))
                    .experienceYears(0)
                    .specialization("General Financial Planning")
                    .bio("Financial advisor ready to help with your investment journey.")
                    .build());
        }
    }

    private User extractActor(HttpServletRequest request) {
        String email = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
        return email != null ? userRepository.findByEmail(email).orElse(null) : null;
    }

    private void logAction(User actor, User targetUser, String action, String targetType, String targetIdentifier, String details) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actor(actor)
                .targetUser(targetUser)
                .action(action)
                .targetType(targetType)
                .targetIdentifier(targetIdentifier)
                .details(details)
                .build());
    }

    private String generateTemporaryPassword() {
        return "Temp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
