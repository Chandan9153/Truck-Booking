package com.truckconnect.booking.dto;

import com.truckconnect.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusUpdateRequest(
        @NotNull(message = "status is required") BookingStatus status,
        String note,        // optional note from the owner, e.g. driver name
        String reason       // optional reason, required by the service for REJECTED/CANCELLED
) { }
