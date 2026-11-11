package com.truckconnect.trip.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignDriverRequest(
        @NotNull(message = "driverId is required") Long driverId
) { }
