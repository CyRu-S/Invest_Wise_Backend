package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.PaymentIntentResponse;

public interface PaymentService {
    PaymentIntentResponse createPaymentIntent(Long advisorId);
}
