package com.truckconnect.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingMessageRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 1000, message = "Message must be at most 1000 characters")
        String message
) { }
