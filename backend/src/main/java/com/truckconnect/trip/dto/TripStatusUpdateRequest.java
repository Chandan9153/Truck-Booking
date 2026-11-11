package com.truckconnect.trip.dto;

import com.truckconnect.trip.TripStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripStatusUpdateRequest(
        @NotNull(message = "status is required")
        TripStatus status,

        @Size(max = 500, message = "Note must be at most 500 characters")
        String note,

        /** Delivery proof (Part 4). Either field may be sent with the
            DELIVERED update, or left empty if proof was saved earlier. */
        @Size(max = 500, message = "Proof note must be at most 500 characters")
        String proofNote,

        @Size(max = 500, message = "Proof URL must be at most 500 characters")
        String proofUrl
) { }
