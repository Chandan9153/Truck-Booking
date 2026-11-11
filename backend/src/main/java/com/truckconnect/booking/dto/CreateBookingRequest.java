package com.truckconnect.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "quoteId is required") Long quoteId
) { }
