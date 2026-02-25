package com.fsad.mutualfund.controller;

import com.fsad.mutualfund.dto.PaymentIntentResponse;
import com.fsad.mutualfund.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('INVESTOR')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a Stripe Payment Intent for an advisor consultation.
     * Body: { "advisorProfileId": 1 }
     */
    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(@RequestBody Map<String, Long> body) {
        Long advisorProfileId = body.get("advisorProfileId");
        if (advisorProfileId == null) {
            throw new RuntimeException("advisorProfileId is required");
        }
        return ResponseEntity.ok(paymentService.createPaymentIntent(advisorProfileId));
    }

    /**
     * Confirm payment completion (webhook or client callback).
     * In production, this would verify with Stripe.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(@RequestBody Map<String, String> body) {
        String paymentIntentId = body.get("paymentIntentId");
        // In production: verify with Stripe API
        return ResponseEntity.ok(Map.of(
                "status", "confirmed",
                "paymentIntentId", paymentIntentId != null ? paymentIntentId : "",
                "message", "Payment confirmed successfully"
        ));
    }
}
