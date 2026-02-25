package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.*;

public interface AuthService {
    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse googleLogin(GoogleLoginRequest request);
}
