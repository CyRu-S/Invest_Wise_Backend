package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.dto.AuthResponse;
import com.fsad.mutualfund.dto.GoogleLoginRequest;
import com.fsad.mutualfund.dto.GoogleTokenPayload;
import com.fsad.mutualfund.dto.LoginRequest;
import com.fsad.mutualfund.dto.RegisterRequest;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.AuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String googleClientId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AuthServiceImpl(UserRepository userRepository,
                           InvestorProfileRepository investorProfileRepository,
                           AdvisorProfileRepository advisorProfileRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           @Value("${app.google.client-id}") String googleClientId) {
        this.userRepository = userRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.advisorProfileRepository = advisorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.googleClientId = googleClientId;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));

        if (user.isSuspended()) {
            throw new RuntimeException("This account has been suspended. Contact support or an administrator.");
        }

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
        createRoleProfile(user, role);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenPayload googleUser = verifyGoogleToken(request.getIdToken());

        User user = userRepository.findByEmail(googleUser.email()).orElseGet(() -> {
            User newUser = User.builder()
                    .email(googleUser.email())
                    .fullName(googleUser.fullName())
                    .role(User.Role.INVESTOR)
                    .authProvider(User.AuthProvider.GOOGLE)
                    .verified(true)
                    .build();
            newUser = userRepository.save(newUser);
            createRoleProfile(newUser, User.Role.INVESTOR);
            return newUser;
        });

        if (user.isSuspended()) {
            throw new RuntimeException("This account has been suspended. Contact support or an administrator.");
        }

        if (user.getAuthProvider() == User.AuthProvider.LOCAL && user.getPassword() != null) {
            throw new RuntimeException("An account with this email already exists. Please sign in with your password.");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    private GoogleTokenPayload verifyGoogleToken(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Google credential is required");
        }

        if (token.contains(".")) {
            return verifyGoogleIdToken(token);
        }

        return verifyGoogleAccessToken(token);
    }

    private GoogleTokenPayload verifyGoogleIdToken(String idToken) {
        if (googleClientId == null || googleClientId.isBlank() || googleClientId.contains("your-google-client-id")) {
            throw new RuntimeException("Google authentication is not configured on the server");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                throw new RuntimeException("Invalid Google credential");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            String fullName = (String) payload.get("name");
            Object emailVerified = payload.get("email_verified");

            if (email == null || email.isBlank()) {
                throw new RuntimeException("Google account email is unavailable");
            }

            if (!(emailVerified instanceof Boolean verified) || !verified) {
                throw new RuntimeException("Google account email is not verified");
            }

            if (fullName == null || fullName.isBlank()) {
                int atIndex = email.indexOf('@');
                fullName = atIndex > 0 ? email.substring(0, atIndex) : email;
            }

            return new GoogleTokenPayload(email, fullName);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Unable to verify Google credential", e);
        }
    }

    private GoogleTokenPayload verifyGoogleAccessToken(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openidconnect.googleapis.com/v1/userinfo"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Invalid Google access token");
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String email = payload.path("email").asText(null);
            String fullName = payload.path("name").asText(null);
            boolean emailVerified = payload.path("email_verified").asBoolean(false);

            if (email == null || email.isBlank()) {
                throw new RuntimeException("Google account email is unavailable");
            }

            if (!emailVerified) {
                throw new RuntimeException("Google account email is not verified");
            }

            if (fullName == null || fullName.isBlank()) {
                int atIndex = email.indexOf('@');
                fullName = atIndex > 0 ? email.substring(0, atIndex) : email;
            }

            return new GoogleTokenPayload(email, fullName);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Unable to verify Google credential", e);
        }
    }

    private void createRoleProfile(User user, User.Role role) {
        if (role == User.Role.INVESTOR) {
            InvestorProfile profile = InvestorProfile.builder()
                    .user(user)
                    .riskToleranceScore(0)
                    .riskCategory(InvestorProfile.RiskCategory.MODERATE)
                    .walletBalance(new BigDecimal("100000.0000"))
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
