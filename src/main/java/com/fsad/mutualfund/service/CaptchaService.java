package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.CaptchaChallengeResponse;
import com.fsad.mutualfund.entity.CaptchaChallenge;
import com.fsad.mutualfund.repository.CaptchaChallengeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaService {

    private static final char[] CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final CaptchaChallengeRepository captchaChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final int captchaExpiryMinutes;

    public CaptchaService(CaptchaChallengeRepository captchaChallengeRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.auth.captcha-expiration-minutes:10}") int captchaExpiryMinutes) {
        this.captchaChallengeRepository = captchaChallengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.captchaExpiryMinutes = captchaExpiryMinutes;
    }

    @Transactional
    public CaptchaChallengeResponse createChallenge() {
        cleanupExpiredChallenges();

        String answer = generateCaptchaText(5);
        CaptchaChallenge challenge = CaptchaChallenge.builder()
                .answerHash(passwordEncoder.encode(answer))
                .expiresAt(LocalDateTime.now().plusMinutes(captchaExpiryMinutes))
                .build();

        challenge = captchaChallengeRepository.save(challenge);

        return CaptchaChallengeResponse.builder()
                .captchaId(challenge.getId())
                .imageData(renderImage(answer))
                .build();
    }

    @Transactional
    public void verifyChallenge(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new RuntimeException("Captcha challenge is required");
        }

        if (captchaCode == null || captchaCode.isBlank()) {
            throw new RuntimeException("Captcha answer is required");
        }

        CaptchaChallenge challenge = captchaChallengeRepository.findById(captchaId)
                .orElseThrow(() -> new RuntimeException("Captcha challenge not found or expired"));

        if (challenge.getConsumedAt() != null) {
            throw new RuntimeException("Captcha challenge has already been used");
        }

        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            captchaChallengeRepository.delete(challenge);
            throw new RuntimeException("Captcha challenge has expired");
        }

        String normalizedAnswer = captchaCode.trim().toUpperCase(Locale.ROOT);
        if (!passwordEncoder.matches(normalizedAnswer, challenge.getAnswerHash())) {
            throw new RuntimeException("Invalid captcha answer");
        }

        challenge.setConsumedAt(LocalDateTime.now());
        captchaChallengeRepository.save(challenge);
    }

    private void cleanupExpiredChallenges() {
        captchaChallengeRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private String generateCaptchaText(int length) {
        StringBuilder builder = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            builder.append(CAPTCHA_CHARS[random.nextInt(CAPTCHA_CHARS.length)]);
        }
        return builder.toString();
    }

    private String renderImage(String answer) {
        try {
            int width = 180;
            int height = 60;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();

            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(14, 18, 31));
            graphics.fillRect(0, 0, width, height);

            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 8; i++) {
                graphics.setColor(new Color(
                        random.nextInt(80, 180),
                        random.nextInt(80, 180),
                        random.nextInt(80, 180),
                        180
                ));
                graphics.drawLine(
                        random.nextInt(width),
                        random.nextInt(height),
                        random.nextInt(width),
                        random.nextInt(height)
                );
            }

            graphics.setFont(new Font("Arial", Font.BOLD, 30));
            for (int i = 0; i < answer.length(); i++) {
                int x = 20 + (i * 28) + random.nextInt(-2, 3);
                int y = 38 + random.nextInt(-5, 6);
                int angle = random.nextInt(-15, 16);
                graphics.setColor(new Color(
                        random.nextInt(180, 255),
                        random.nextInt(180, 255),
                        random.nextInt(180, 255)
                ));
                graphics.rotate(Math.toRadians(angle), x, y);
                graphics.drawString(String.valueOf(answer.charAt(i)), x, y);
                graphics.rotate(Math.toRadians(-angle), x, y);
            }

            for (int i = 0; i < 120; i++) {
                image.setRGB(random.nextInt(width), random.nextInt(height), new Color(
                        random.nextInt(255),
                        random.nextInt(255),
                        random.nextInt(255)
                ).getRGB());
            }

            graphics.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception ex) {
            throw new RuntimeException("Unable to generate captcha image", ex);
        }
    }
}
