package com.truckconnect.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminDashboardResponse(
        Map<String, Long> usersByRole,
        Map<String, Long> trucksByStatus,
        Map<String, Long> bookingsByStatus,
        BigDecimal revenueThisMonth,
        BigDecimal pendingSettlementsTotal,
        long totalUsers,
        long totalTrucks,
        long totalBookings
) { }
