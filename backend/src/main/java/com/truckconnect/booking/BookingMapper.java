package com.truckconnect.booking;

import com.truckconnect.booking.dto.BookingResponse;

public final class BookingMapper {

    private BookingMapper() { }

    public static BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getQuote().getId(),
                b.getStatus(),
                b.getCustomer().getId(),
                b.getCustomer().getFullName(),
                b.getCustomer().getPhone(),
                b.getOwner().getId(),
                b.getOwner().getFullName(),
                b.getOwner().getPhone(),
                b.getTruck().getId(),
                b.getTruckRegistrationNumber(),
                b.getCategoryName(),
                b.getTyreCount(),
                b.getPayloadTonsAtBooking(),
                b.getBodyType() == null ? null : b.getBodyType().name(),
                b.getPickup(),
                b.getDestination(),
                b.getDistanceKm(),
                b.getLoadTons(),
                b.getGoodsType(),
                b.getRatePerTonKm(),
                b.getChargeableTons(),
                b.isMinimumChargeApplied(),
                b.getBaseFare(),
                b.getTollCharges(),
                b.getLoadingCharges(),
                b.getPlatformFee(),
                b.getTotalFare(),
                b.getCancelReason(),
                b.getOwnerNote(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }
}
