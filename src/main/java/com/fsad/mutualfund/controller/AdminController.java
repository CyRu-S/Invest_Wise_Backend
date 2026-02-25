package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.ApiResponse;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.service.FundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final FundService fundService;

    public AdminController(UserRepository userRepository, FundService fundService) {
        this.userRepository = userRepository;
        this.fundService = fundService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();

        List<Map<String, Object>> result = users.stream().map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "email", u.getEmail(),
                "fullName", u.getFullName(),
                "role", u.getRole().name(),
                "authProvider", u.getAuthProvider().name(),
                "verified", u.isVerified(),
                "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted"));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        User.Role role = User.Role.valueOf(body.get("role").toUpperCase());
        user.setRole(role);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("Role updated to " + role.name()));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalUsers = userRepository.count();
        long investors = userRepository.findByRole(User.Role.INVESTOR).size();
        long advisors = userRepository.findByRole(User.Role.ADVISOR).size();
        long analysts = userRepository.findByRole(User.Role.ANALYST).size();

        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "investors", investors,
                "advisors", advisors,
                "analysts", analysts
        ));
    }
}
