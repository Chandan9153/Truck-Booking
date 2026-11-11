package com.truckconnect.payment.dto;

import com.truckconnect.payment.PaymentMethod;
import com.truckconnect.payment.PaymentStatus;
import java.math.BigDecimal;

public record InitiatePaymentResponse(
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        boolean idempotentReplay,
        String message
) { }
