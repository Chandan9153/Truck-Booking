package com.truckconnect.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RateUpdateRequest(
        @NotNull @DecimalMin("0.10") @DecimalMax("20.00") BigDecimal ratePerTonKm,
        Boolean active) { }
