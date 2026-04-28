package com.fsad.mutualfund.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsad.mutualfund.dto.*;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.entity.AuthCode;
import com.fsad.mutualfund.entity.InvestorProfile;
import com.fsad.mutualfund.entity.User;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.repository.AuthCodeRepository;
import com.fsad.mutualfund.repository.InvestorProfileRepository;
import com.fsad.mutualfund.repository.UserRepository;
import com.fsad.mutualfund.security.JwtUtil;
import com.fsad.mutualfund.service.AuthService;
import com.fsad.mutualfund.service.CaptchaService;
import com.fsad.mutualfund.service.EmailService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final AuthCodeRepository authCodeRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;
    private final EmailService emailService;
    private final String googleClientId;
    private final int codeExpiryMinutes;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AuthServiceImpl(UserRepository userRepository,
                           AuthCodeRepository authCodeRepository,
                           InvestorProfileRepository investorProfileRepository,
                           AdvisorProfileRepository advisorProfileRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           CaptchaService captchaService,
                           EmailService emailService,
                           @Value("${app.google.client-id}") String googleClientId,
                           @Value("${app.auth.code-expiration-minutes:10}") int codeExpiryMinutes) {
        this.userRepository = userRepository;
        this.authCodeRepository = authCodeRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.advisorProfileRepository = advisorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
        this.emailService = emailService;
        this.googleClientId = googleClientId;
        this.codeExpiryMinutes = codeExpiryMinutes;
    }

    @Override
    public CaptchaChallengeResponse createCaptcha() {
        return captchaService.createChallenge();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        captchaService.verifyChallenge(request.getCaptchaId(), request.getCaptchaCode());

        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isSuspended()) {
            throw new RuntimeException("This account has been suspended. Contact support or an administrator.");
        }

        if (user.getAuthProvider() == User.AuthProvider.LOCAL && !user.isVerified()) {
            throw new RuntimeException("Please verify your email before logging in.");
        }

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    @Override
    @Transactional
    public ApiResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password and confirm password must match.");
        }

        String email = normalizeEmail(request.getEmail());
        User.Role role = resolvePublicRole(request.getRole());

        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null && existingUser.isVerified()) {
            throw new RuntimeException("Email already registered: " + email);
        }

        if (existingUser != null && existingUser.getAuthProvider() == User.AuthProvider.GOOGLE) {
            throw new RuntimeException("This email is already registered with Google sign-in.");
        }

        User user = existingUser != null ? existingUser : User.builder().build();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setRole(role);
        user.setAuthProvider(User.AuthProvider.LOCAL);
        user.setVerified(false);
        user.setSuspended(false);

        user = userRepository.save(user);
        String code = createFreshCode(email, AuthCode.Purpose.REGISTRATION_VERIFICATION);
        emailService.sendRegistrationVerificationCode(user.getEmail(), user.getFullName(), code);

        return ApiResponse.success("Verification code sent to your email. Enter it to activate your account.");
    }

    @Override
    @Transactional
    public AuthResponse verifyRegistration(EmailVerificationRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No pending account found for this email."));

        if (user.isVerified()) {
            throw new RuntimeException("This account is already verified. Please sign in.");
        }

        verifyCode(email, AuthCode.Purpose.REGISTRATION_VERIFICATION, request.getCode(), true);
        user.setVerified(true);
        user = userRepository.save(user);
        createRoleProfileIfMissing(user, user.getRole());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return buildAuthResponse(user, token);
    }

    @Override
    @Transactional
    public ApiResponse requestPasswordReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.getEmail());

        userRepository.findByEmail(email)
                .filter(user -> user.getAuthProvider() == User.AuthProvider.LOCAL && user.isVerified())
                .ifPresent(user -> {
                    String code = createFreshCode(email, AuthCode.Purpose.PASSWORD_RESET);
                    emailService.sendPasswordResetCode(user.getEmail(), user.getFullName(), code);
                });

        return ApiResponse.success("If an eligible account exists, a reset code has been sent to the registered email.");
    }

    @Override
    @Transactional
    public ApiResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New password and confirm password must match.");
        }

        captchaService.verifyChallenge(request.getCaptchaId(), request.getCaptchaCode());

        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found for this email."));

        if (user.getAuthProvider() != User.AuthProvider.LOCAL) {
            throw new RuntimeException("This account uses Google sign-in and cannot reset a local password.");
        }

        if (!user.isVerified()) {
            throw new RuntimeException("Please verify your email before resetting your password.");
        }

        verifyCode(email, AuthCode.Purpose.PASSWORD_RESET, request.getCode(), true);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ApiResponse.success("Password updated successfully. You can now sign in with your new password.");
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenPayload googleUser = verifyGoogleToken(request.getIdToken());
        String email = normalizeEmail(googleUser.email());

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(googleUser.fullName())
                    .role(User.Role.INVESTOR)
                    .authProvider(User.AuthProvider.GOOGLE)
                    .verified(true)
                    .build();
            User savedUser = userRepository.save(newUser);
            createRoleProfileIfMissing(savedUser, User.Role.INVESTOR);
            return savedUser;
        });

        if (user.isSuspended()) {
            throw new RuntimeException("This account has been suspended. Contact support or an administrator.");
        }

        if (user.getAuthProvider() == User.AuthProvider.LOCAL && user.getPassword() != null) {
            if (!user.isVerified()) {
                throw new RuntimeException("An account with this email already exists but is not verified yet. Please verify it with email login.");
            }
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Unable to verify Google credential", e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to verify Google credential", e);
        }
    }

    private User.Role resolvePublicRole(String roleValue) {
        try {
            User.Role role = User.Role.valueOf(roleValue.toUpperCase(Locale.ROOT));
            if (role == User.Role.ADMIN) {
                throw new RuntimeException("Admin accounts cannot be created through public registration.");
            }
            return role;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role: " + roleValue);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String createFreshCode(String email, AuthCode.Purpose purpose) {
        authCodeRepository.deleteByEmailAndPurpose(email, purpose);
        authCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        AuthCode authCode = AuthCode.builder()
                .email(email)
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(LocalDateTime.now().plusMinutes(codeExpiryMinutes))
                .build();
        authCodeRepository.save(authCode);
        return code;
    }

    private void verifyCode(String email, AuthCode.Purpose purpose, String code, boolean consume) {
        AuthCode authCode = authCodeRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new RuntimeException("No active verification code found. Please request a new code."));

        if (authCode.getConsumedAt() != null) {
            throw new RuntimeException("This verification code has already been used. Please request a new one.");
        }

        if (authCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            authCodeRepository.delete(authCode);
            throw new RuntimeException("This verification code has expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(code, authCode.getCodeHash())) {
            throw new RuntimeException("Invalid verification code.");
        }

        if (consume) {
            authCode.setConsumedAt(LocalDateTime.now());
            authCodeRepository.save(authCode);
        }
    }

    private void createRoleProfileIfMissing(User user, User.Role role) {
        if (role == User.Role.INVESTOR) {
            if (investorProfileRepository.findByUserId(user.getId()).isPresent()) {
                return;
            }

            InvestorProfile profile = InvestorProfile.builder()
                    .user(user)
                    .riskToleranceScore(0)
                    .riskCategory(InvestorProfile.RiskCategory.MODERATE)
                    .walletBalance(new BigDecimal("100000.0000"))
                    .build();
            investorProfileRepository.save(profile);
        } else if (role == User.Role.ADVISOR) {
            if (advisorProfileRepository.findByUserId(user.getId()).isPresent()) {
                return;
            }

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
