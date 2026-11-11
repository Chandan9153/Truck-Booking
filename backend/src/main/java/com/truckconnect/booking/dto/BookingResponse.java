package com.truckconnect.booking.dto;

import com.truckconnect.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        Long quoteId,
        BookingStatus status,
        Long customerId,
        String customerName,
        String customerPhone,
        Long ownerId,
        String ownerName,
        String ownerPhone,
        Long truckId,
        String truckRegistrationNumber,
        String categoryName,
        int tyreCount,
        BigDecimal payloadTonsAtBooking,
        String bodyType,
        String pickup,
        String destination,
        BigDecimal distanceKm,
        BigDecimal loadTons,
        String goodsType,
        BigDecimal ratePerTonKm,
        BigDecimal chargeableTons,
        boolean minimumChargeApplied,
        BigDecimal baseFare,
        BigDecimal tollCharges,
        BigDecimal loadingCharges,
        BigDecimal platformFee,
        BigDecimal totalFare,
        String cancelReason,
        String ownerNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
