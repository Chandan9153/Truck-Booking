package com.truckconnect.payment.dto;

import com.truckconnect.payment.Payment;
import com.truckconnect.payment.PaymentMethod;
import com.truckconnect.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String gatewayReference,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getBooking().getId(), p.getAmount(),
                p.getMethod(), p.getStatus(), p.getGatewayReference(),
                p.getPaidAt(), p.getCreatedAt());
    }
}
