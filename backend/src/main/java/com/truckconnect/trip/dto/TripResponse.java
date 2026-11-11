package com.truckconnect.trip.dto;

import com.truckconnect.trip.Trip;
import com.truckconnect.trip.TripStatus;
import java.time.LocalDateTime;

public record TripResponse(
        Long tripId,
        Long bookingId,
        Long truckId,
        String registrationNumber,
        Long driverId,
        String driverName,
        String driverPhone,
        TripStatus status,
        String pickup,
        String destination,
        String proofNote,
        String proofUrl,
        LocalDateTime startedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt) {

    public static TripResponse from(Trip t) {
        return new TripResponse(
                t.getId(),
                t.getBooking().getId(),
                t.getTruck().getId(),
                t.getTruck().getRegistrationNumber(),
                t.getDriver().getId(),
                t.getDriver().getName(),
                t.getDriver().getPhone(),
                t.getStatus(),
                t.getPickup(),
                t.getDestination(),
                t.getProofNote(),
                t.getProofUrl(),
                t.getStartedAt(),
                t.getPickedUpAt(),
                t.getDeliveredAt(),
                t.getCompletedAt(),
                t.getCreatedAt());
    }
}
