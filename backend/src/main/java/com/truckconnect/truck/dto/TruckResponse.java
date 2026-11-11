package com.truckconnect.truck.dto;

import com.truckconnect.truck.Truck;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TruckResponse(
        Long id, String registrationNumber, Long categoryId, String categoryName,
        int tyreCount, BigDecimal permittedPayloadTons, BigDecimal grossVehicleWeightTons,
        String bodyType, BigDecimal lengthFeet, BigDecimal widthFeet, BigDecimal heightFeet,
        Long ownerId, String ownerEmail,
        String availabilityStatus,
        LocalDateTime createdAt
) {
    public static TruckResponse from(Truck t) {
        return new TruckResponse(
            t.getId(), t.getRegistrationNumber(),
            t.getCategory().getId(), t.getCategory().getName(),
            t.getTyreCount(), t.getPermittedPayloadTons(), t.getGrossVehicleWeightTons(),
            t.getBodyType().name(), t.getLengthFeet(), t.getWidthFeet(), t.getHeightFeet(),
            t.getOwner().getId(), t.getOwner().getEmail(),
            t.getAvailabilityStatus().name(),
            t.getCreatedAt());
    }
}
