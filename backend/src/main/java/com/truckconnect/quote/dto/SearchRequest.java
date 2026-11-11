package com.truckconnect.quote.dto;

import com.truckconnect.truck.BodyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SearchRequest(
        @NotBlank String pickup,
        @NotBlank String destination,
        @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal distanceKm,
        @NotNull @DecimalMin("0.1") @DecimalMax("40") BigDecimal loadTons,
        String goodsType,
        BodyType bodyType,          // optional: null means "any body"
        Long categoryId,            // optional: null means "any category"
        @DecimalMin("0") BigDecimal tollCharges,
        @DecimalMin("0") BigDecimal loadingCharges
) { }
