package com.truckconnect.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuoteResponse(
        Long quoteId,
        Long truckId,
        String registrationNumber,
        String categoryName,
        int tyreCount,
        BigDecimal payloadTons,
        String bodyType,
        BigDecimal ratePerTonKm,
        BigDecimal chargeableTons,
        boolean minimumChargeApplied,
        BigDecimal baseFare,
        BigDecimal tollCharges,
        BigDecimal loadingCharges,
        BigDecimal platformFee,
        BigDecimal totalFare,
        LocalDateTime validUntil
) { }
