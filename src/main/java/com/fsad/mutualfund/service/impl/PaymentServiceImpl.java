package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.dto.PaymentIntentResponse;
import com.fsad.mutualfund.entity.AdvisorProfile;
import com.fsad.mutualfund.repository.AdvisorProfileRepository;
import com.fsad.mutualfund.service.PaymentService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${stripe.api.secret-key}")
    private String stripeSecretKey;

    private final AdvisorProfileRepository advisorProfileRepository;

    public PaymentServiceImpl(AdvisorProfileRepository advisorProfileRepository) {
        this.advisorProfileRepository = advisorProfileRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Override
    public PaymentIntentResponse createPaymentIntent(Long advisorId) {
        AdvisorProfile advisor = advisorProfileRepository.findById(advisorId)
                .orElseThrow(() -> new RuntimeException("Advisor not found: " + advisorId));

        long amountInCents = advisor.getConsultationFee()
                .multiply(new java.math.BigDecimal("100"))
                .longValue();

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            return PaymentIntentResponse.builder()
                    .clientSecret(intent.getClientSecret())
                    .paymentIntentId(intent.getId())
                    .amount(amountInCents)
                    .currency("usd")
                    .build();
        } catch (StripeException e) {
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }
}
