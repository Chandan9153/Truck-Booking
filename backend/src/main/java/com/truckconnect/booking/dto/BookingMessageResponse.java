package com.truckconnect.booking.dto;

import java.time.LocalDateTime;

public record BookingMessageResponse(
        Long id,
        Long bookingId,
        Long senderId,
        String senderName,
        String senderRole,
        String message,
        LocalDateTime createdAt
) { }
