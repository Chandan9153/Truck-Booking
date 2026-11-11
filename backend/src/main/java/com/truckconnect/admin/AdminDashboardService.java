package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminDashboardResponse;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.settlement.OwnerSettlementRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final TruckRepository truckRepository;
    private final BookingRepository bookingRepository;
    private final OwnerSettlementRepository settlementRepository;

    public AdminDashboardService(UserRepository userRepository,
                                 TruckRepository truckRepository,
                                 BookingRepository bookingRepository,
                                 OwnerSettlementRepository settlementRepository) {
        this.userRepository = userRepository;
        this.truckRepository = truckRepository;
        this.bookingRepository = bookingRepository;
        this.settlementRepository = settlementRepository;
    }

    public AdminDashboardResponse getDashboard() {
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        long totalUsers = 0;
        for (Role role : Role.values()) {
            long count = userRepository.countByRole(role);
            usersByRole.put(role.name(), count);
            totalUsers += count;
        }

        Map<String, Long> trucksByStatus = new LinkedHashMap<>();
        long totalTrucks = 0;
        for (AvailabilityStatus status : AvailabilityStatus.values()) {
            long count = truckRepository.countByAvailabilityStatus(status);
            trucksByStatus.put(status.name(), count);
            totalTrucks += count;
        }

        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        long totalBookings = 0;
        for (BookingStatus status : BookingStatus.values()) {
            long count = bookingRepository.countByStatus(status);
            bookingsByStatus.put(status.name(), count);
            totalBookings += count;
        }

        // Revenue this month: platform fee only, COMPLETED bookings only.
        LocalDateTime startOfMonth = LocalDate.now()
                .withDayOfMonth(1).atStartOfDay();
        BigDecimal revenueThisMonth =
                bookingRepository.sumPlatformFeesSince(startOfMonth);

        BigDecimal pendingSettlementsTotal =
                settlementRepository.sumPendingSettlements();

        return new AdminDashboardResponse(
                usersByRole, trucksByStatus, bookingsByStatus,
                revenueThisMonth, pendingSettlementsTotal,
                totalUsers, totalTrucks, totalBookings);
    }
}
