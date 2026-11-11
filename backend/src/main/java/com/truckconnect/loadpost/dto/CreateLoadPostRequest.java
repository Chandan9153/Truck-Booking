package com.truckconnect.loadpost.dto;

import com.truckconnect.truck.BodyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLoadPostRequest(
        @NotBlank @Size(min = 3, max = 200) String goodsDescription,
        @NotNull @DecimalMin("0.5") @DecimalMax("500") BigDecimal totalTons,
        @NotNull @Min(1) @Max(25) int trucksNeeded,
        @NotNull Long categoryId,
        BodyType bodyType,               // optional: null means "any body"
        @NotBlank String pickup,
        @NotBlank String destination,
        @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal distanceKm,
        @NotNull @FutureOrPresent LocalDate pickupDate,
        @DecimalMin("0") BigDecimal tollCharges,     // per truck, optional
        @DecimalMin("0") BigDecimal loadingCharges,  // per truck, optional
        @Pattern(regexp = "^[6-9][0-9]{9}$",
                 message = "Phone must be a 10-digit Indian mobile number")
        String contactPhone              // optional: null = use account phone
) { }
