package com.truckconnect.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverRequest(
        @NotBlank(message = "Driver name is required")
        @Size(min = 2, max = 100, message = "Driver name must be 2-100 characters")
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$",
                 message = "Phone must be a 10-digit Indian mobile number")
        String phone,

        @NotBlank(message = "Licence number is required")
        @Size(min = 5, max = 30, message = "Licence number must be 5-30 characters")
        String licenceNumber
) { }
