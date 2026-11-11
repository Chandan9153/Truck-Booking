package com.truckconnect.pricing.dto;

import java.math.BigDecimal;

public record PricingRateResponse(
        Long id, String categoryName, int tyreCount,
        BigDecimal targetPayloadTons, BigDecimal ratePerTonKm, boolean active) { }
