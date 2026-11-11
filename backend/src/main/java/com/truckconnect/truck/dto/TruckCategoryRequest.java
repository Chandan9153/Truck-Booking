package com.truckconnect.truck.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TruckCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 60, message = "Name must be 60 characters or less")
        String name,

        @Min(value = 4, message = "Tyre count must be at least 4")
        @Max(value = 30, message = "Tyre count must be 30 or less")
        int tyreCount,

        @NotNull(message = "Target payload is required")
        @DecimalMin(value = "1.0", message = "Target payload must be at least 1 ton")
        @DecimalMax(value = "60.0", message = "Target payload must be 60 tons or less")
        BigDecimal targetPayloadTons,

        @Size(max = 255, message = "Description must be 255 characters or less")
        String description
) { }
