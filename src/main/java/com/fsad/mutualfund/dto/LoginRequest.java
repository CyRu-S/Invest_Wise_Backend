package com.fsad.mutualfund.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Captcha challenge is required")
    private String captchaId;

    @NotBlank(message = "Captcha answer is required")
    private String captchaCode;
}
