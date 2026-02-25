package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.dto.*;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository,
                           InvestorProfileRepository investorProfileRepository,
                           AdvisorProfileRepository advisorProfileRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.advisorProfileRepository = advisorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        User.Role role;
        try {
            role = User.Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role: " + request.getRole());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(role)
                .authProvider(User.AuthProvider.LOCAL)
                .verified(true)
                .build();

        user = userRepository.save(user);

        // Create role-specific profile
        createRoleProfile(user, role);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        // In production, verify the Google ID token here using Google API Client.
        // For this implementation, we'll simulate by extracting email from the token.
        // NOTE: Replace with actual Google token verification in production.

        // Simulated extraction — in production use GoogleIdTokenVerifier
        String email = "google.user@gmail.com"; // Placeholder
        String fullName = "Google User";

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(fullName)
                    .role(User.Role.INVESTOR)
                    .authProvider(User.AuthProvider.GOOGLE)
                    .verified(true)
                    .build();
            newUser = userRepository.save(newUser);
            createRoleProfile(newUser, User.Role.INVESTOR);
            return newUser;
        });

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    private void createRoleProfile(User user, User.Role role) {
        if (role == User.Role.INVESTOR) {
            InvestorProfile profile = InvestorProfile.builder()
                    .user(user)
                    .riskToleranceScore(0)
                    .riskCategory(InvestorProfile.RiskCategory.MODERATE)
                    .walletBalance(new BigDecimal("100000.0000")) // Starting balance for demo
                    .build();
            investorProfileRepository.save(profile);
        } else if (role == User.Role.ADVISOR) {
            AdvisorProfile profile = AdvisorProfile.builder()
                    .user(user)
                    .consultationFee(new BigDecimal("50.00"))
                    .experienceYears(0)
                    .specialization("General Financial Planning")
                    .bio("Financial advisor ready to help with your investment journey.")
                    .build();
            advisorProfileRepository.save(profile);
        }
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }
}
