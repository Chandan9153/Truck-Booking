package com.truckconnect.trip.dto;

import com.truckconnect.trip.TripEvent;
import com.truckconnect.trip.TripEventType;
import com.truckconnect.user.Role;
import java.time.LocalDateTime;

public record TripEventResponse(
        Long id, TripEventType eventType, String note,
        Role createdByRole, LocalDateTime createdAt) {

    public static TripEventResponse from(TripEvent e) {
        return new TripEventResponse(e.getId(), e.getEventType(), e.getNote(),
                e.getCreatedByRole(), e.getCreatedAt());
    }
}
