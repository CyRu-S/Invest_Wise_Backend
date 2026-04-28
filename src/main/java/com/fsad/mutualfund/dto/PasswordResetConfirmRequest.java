package com.fsad.mutualfund.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Reset code is required")
    @Size(min = 6, max = 6, message = "Reset code must be 6 digits")
    private String code;

    @NotBlank(message = "Captcha challenge is required")
    private String captchaId;

    @NotBlank(message = "Captcha answer is required")
    private String captchaCode;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.replaceAll("\\D", "");
    }
}
