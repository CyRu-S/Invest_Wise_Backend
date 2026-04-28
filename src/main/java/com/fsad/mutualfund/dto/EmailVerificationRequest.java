package com.fsad.mutualfund.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailVerificationRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Verification code is required")
    @Size(min = 6, max = 6, message = "Verification code must be 6 digits")
    private String code;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.replaceAll("\\D", "");
    }
}
