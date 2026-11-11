package com.truckconnect.truck.dto;

import com.truckconnect.truck.BodyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TruckRequest(
        @NotBlank(message = "Registration number is required")
        @Pattern(regexp = "^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{4}$",
                 message = "Registration number format looks wrong, e.g. KA05MN1234")
        String registrationNumber,

        @NotNull(message = "Category is required")
        Long categoryId,

        @Min(value = 4, message = "Tyre count must be at least 4")
        @Max(value = 30, message = "Tyre count must be 30 or less")
        int tyreCount,

        @NotNull(message = "Permitted payload is required")
        @DecimalMin(value = "0.5", message = "Permitted payload must be at least 0.5 tons")
        @DecimalMax(value = "60.0", message = "Permitted payload must be 60 tons or less")
        BigDecimal permittedPayloadTons,

        @NotNull(message = "Gross vehicle weight is required")
        @DecimalMin(value = "1.0", message = "GVW must be at least 1 ton")
        @DecimalMax(value = "100.0", message = "GVW must be 100 tons or less")
        BigDecimal grossVehicleWeightTons,

        @NotNull(message = "Body type is required")
        BodyType bodyType,

        @NotNull @DecimalMin(value = "1.0", message = "Length must be at least 1 foot")
        BigDecimal lengthFeet,

        @NotNull @DecimalMin(value = "1.0", message = "Width must be at least 1 foot")
        BigDecimal widthFeet,

        @NotNull @DecimalMin(value = "1.0", message = "Height must be at least 1 foot")
        BigDecimal heightFeet
) { }
