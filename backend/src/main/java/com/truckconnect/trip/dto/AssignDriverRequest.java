package com.truckconnect.trip.dto;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(
        @NotNull(message = "bookingId is required") Long bookingId,
        @NotNull(message = "driverId is required") Long driverId
) { }
