package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminDashboardResponse;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.payment.OwnerSettlementRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock TruckRepository truckRepository;
    @Mock BookingRepository bookingRepository;
    @Mock OwnerSettlementRepository settlementRepository;
    @InjectMocks AdminDashboardService dashboardService;

    @Test
    void revenue_isPlatformFeeOnly_notTotalFare() {
        // 2 completed bookings this month, fee 0 each -> revenue 0.
        // Their total fares (33280 + 17890 = 51170) must NOT appear as revenue.
        when(userRepository.countByRole(any(Role.class))).thenReturn(0L);
        when(truckRepository.countByAvailabilityStatus(any(AvailabilityStatus.class)))
                .thenReturn(0L);
        when(bookingRepository.countByStatus(any(BookingStatus.class))).thenReturn(0L);
        when(bookingRepository.sumPlatformFeesSince(any()))
                .thenReturn(new BigDecimal("0.00"));
        when(settlementRepository.sumPendingSettlements())
                .thenReturn(new BigDecimal("51170.00"));

        AdminDashboardResponse dto = dashboardService.getDashboard();

        assertEquals(new BigDecimal("0.00"), dto.revenueThisMonth());
        assertEquals(new BigDecimal("51170.00"), dto.pendingSettlementsTotal());
        assertNotEquals(dto.revenueThisMonth(), dto.pendingSettlementsTotal(),
                "Revenue and owner money must never be the same number");
    }
}
