package com.truckconnect.quote;

import com.truckconnect.quote.dto.QuoteResponse;

public final class QuoteMapper {

    private QuoteMapper() { }

    public static QuoteResponse toResponse(Quote q) {
        return new QuoteResponse(
                q.getId(),
                q.getTruck().getId(),
                q.getTruck().getRegistrationNumber(),
                q.getTruck().getCategory().getName(),
                q.getTruck().getCategory().getTyreCount(),
                q.getTruck().getPayloadTons(),
                q.getTruck().getBodyType() == null
                        ? null : q.getTruck().getBodyType().name(),
                q.getRatePerTonKm(),
                q.getChargeableTons(),
                q.isMinimumChargeApplied(),
                q.getBaseFare(),
                q.getTollCharges(),
                q.getLoadingCharges(),
                q.getPlatformFee(),
                q.getTotalFare(),
                q.getValidUntil());
    }
}
