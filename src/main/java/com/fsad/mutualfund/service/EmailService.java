package com.fsad.mutualfund.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String mailMode;
    private final String smtpHost;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String configuredFromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.enabled:auto}") String mailMode,
                        @Value("${spring.mail.host:}") String smtpHost,
                        @Value("${spring.mail.username:}") String smtpUsername,
                        @Value("${spring.mail.password:}") String smtpPassword,
                        @Value("${app.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.mailMode = mailMode;
        this.smtpHost = smtpHost;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.configuredFromAddress = fromAddress;

        if (isMailEnabled()) {
            log.info("Email delivery is enabled using SMTP host {} and sender {}", smtpHost, resolveFromAddress());
        } else if (hasSmtpConfiguration()) {
            log.warn("SMTP credentials are present, but email delivery is disabled because app.mail.enabled is set to false.");
        } else {
            log.info("Email delivery is disabled. Verification codes will only appear in backend logs until SMTP is configured.");
        }
    }

    public void sendRegistrationVerificationCode(String email, String fullName, String code) {
        String body = String.format(
                "Hi %s,%n%nYour InvestWise email verification code is: %s%n%nThis code will expire soon. If you did not start this signup, you can ignore this email.%n",
                fullName,
                code
        );
        sendEmail(email, "Verify your InvestWise account", body, code, "registration");
    }

    public void sendPasswordResetCode(String email, String fullName, String code) {
        String body = String.format(
                "Hi %s,%n%nYour InvestWise password reset code is: %s%n%nUse this code together with the captcha on the reset screen to set a new password. If you did not request this, you can ignore this email.%n",
                fullName,
                code
        );
        sendEmail(email, "Reset your InvestWise password", body, code, "password reset");
    }

    private void sendEmail(String email, String subject, String body, String code, String purpose) {
        if (!isMailEnabled()) {
            log.info("[MAIL DISABLED] {} code for {}: {}", purpose, email, code);
            return;
        }

        if (!hasSmtpConfiguration()) {
            log.error("Email delivery is enabled, but SMTP settings are incomplete. Host set: {}, username set: {}, password set: {}",
                    StringUtils.hasText(smtpHost),
                    StringUtils.hasText(smtpUsername),
                    StringUtils.hasText(smtpPassword));
            throw new RuntimeException("Email delivery is not configured correctly on the server. Please contact support.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(resolveFromAddress());
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Sent {} email to {}", purpose, email);
        } catch (MailException ex) {
            log.error("Failed to send {} email to {} using from address {}", purpose, email, resolveFromAddress(), ex);
            throw new RuntimeException("Unable to send verification email right now. Please try again in a moment.", ex);
        }
    }

    private boolean isMailEnabled() {
        String normalizedMode = mailMode == null ? "auto" : mailMode.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalizedMode)) {
            return true;
        }
        if ("false".equals(normalizedMode)) {
            return false;
        }
        return hasSmtpConfiguration();
    }

    private boolean hasSmtpConfiguration() {
        return StringUtils.hasText(smtpHost)
                && StringUtils.hasText(smtpUsername)
                && StringUtils.hasText(smtpPassword);
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(configuredFromAddress)) {
            return configuredFromAddress.trim();
        }
        return smtpUsername;
    }
}
