package com.truckconnect.booking;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    DRIVER_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    DISPUTED;

    /** The transition table from Part 3. A move not listed here is refused. */
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED =
            new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(CONFIRMED, REJECTED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(DRIVER_ASSIGNED, PAYMENT_PENDING,
                CANCELLED, DISPUTED));
        ALLOWED.put(PAYMENT_PENDING, EnumSet.of(CONFIRMED, PAYMENT_FAILED));
        ALLOWED.put(PAYMENT_FAILED, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(DRIVER_ASSIGNED, EnumSet.of(PICKED_UP, CANCELLED, DISPUTED));
        ALLOWED.put(PICKED_UP, EnumSet.of(IN_TRANSIT, DISPUTED));
        ALLOWED.put(IN_TRANSIT, EnumSet.of(DELIVERED, DISPUTED));
        ALLOWED.put(DELIVERED, EnumSet.of(COMPLETED, DISPUTED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(DISPUTED, EnumSet.of(CONFIRMED, CANCELLED));
    }

    public static boolean canMoveTo(BookingStatus from, BookingStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Statuses in which the truck is still committed to this booking. */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(
            PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT);

    /** Statuses from which nobody can move any more. */
    public static boolean isFinal(BookingStatus status) {
        return status == COMPLETED || status == REJECTED || status == CANCELLED;
    }
}
