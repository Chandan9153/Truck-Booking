package com.truckconnect.loadpost;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum LoadPostStatus {
    OPEN,
    PARTIALLY_BOOKED,
    FULLY_BOOKED,
    CANCELLED,
    EXPIRED;

    /** The transition table from Part 3. A move not listed here is refused. */
    private static final Map<LoadPostStatus, Set<LoadPostStatus>> ALLOWED =
            new EnumMap<>(LoadPostStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(PARTIALLY_BOOKED, FULLY_BOOKED,
                CANCELLED, EXPIRED));
        ALLOWED.put(PARTIALLY_BOOKED, EnumSet.of(FULLY_BOOKED,
                CANCELLED, EXPIRED));
        ALLOWED.put(FULLY_BOOKED, EnumSet.of(CANCELLED));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(LoadPostStatus.class));
        ALLOWED.put(EXPIRED, EnumSet.noneOf(LoadPostStatus.class));
    }

    public static boolean canMoveTo(LoadPostStatus from, LoadPostStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Statuses in which trucks can still be booked from the post. */
    public static boolean isBookable(LoadPostStatus status) {
        return status == OPEN || status == PARTIALLY_BOOKED;
    }

    public static boolean isFinal(LoadPostStatus status) {
        return status == CANCELLED || status == EXPIRED;
    }
}
