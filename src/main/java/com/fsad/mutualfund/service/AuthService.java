package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.*;

public interface AuthService {
    CaptchaChallengeResponse createCaptcha();

    AuthResponse login(LoginRequest request);

    ApiResponse register(RegisterRequest request);

    AuthResponse verifyRegistration(EmailVerificationRequest request);

    ApiResponse requestPasswordReset(PasswordResetRequest request);

    ApiResponse confirmPasswordReset(PasswordResetConfirmRequest request);

    AuthResponse googleLogin(GoogleLoginRequest request);
}
