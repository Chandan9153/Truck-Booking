package com.truckconnect.settlement.dto;

import com.truckconnect.settlement.OwnerSettlement;
import com.truckconnect.settlement.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementResponse(
        Long settlementId,
        Long bookingId,
        Long paymentId,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal ownerAmount,
        SettlementStatus status,
        String note,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {
    public static SettlementResponse from(OwnerSettlement s) {
        return new SettlementResponse(
                s.getId(), s.getBooking().getId(), s.getPayment().getId(),
                s.getGrossAmount(), s.getPlatformFee(), s.getOwnerAmount(),
                s.getStatus(), s.getNote(), s.getSettledAt(), s.getCreatedAt());
    }
}
