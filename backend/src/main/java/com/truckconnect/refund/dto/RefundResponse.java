package com.truckconnect.refund.dto;

import com.truckconnect.refund.Refund;
import com.truckconnect.refund.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
    public static RefundResponse from(Refund r) {
        return new RefundResponse(
                r.getId(), r.getPayment().getId(), r.getAmount(), r.getReason(),
                r.getStatus(), r.getAdminNote(), r.getCreatedAt(), r.getProcessedAt());
    }
}
