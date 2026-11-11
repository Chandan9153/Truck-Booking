package com.truckconnect.payment.dto;

import com.truckconnect.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull Long bookingId,
        @NotNull PaymentMethod method
) { }
