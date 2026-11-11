package com.truckconnect.booking;

import com.truckconnect.admin.AdminDashboardService;
import com.truckconnect.admin.dto.AdminDashboardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One booking, end to end, against a real (in-memory H2) database.
 * This test proves the dashboard revenue query sums platformFee, not totalFare,
 * and that a COMPLETED booking moves the dashboard counts.
 *
 * NOTE: H2 is used here because it needs no Docker. If you later install Docker
 * and want the test to run against real MySQL, switch to Testcontainers:
 * replace the H2 dependency with testcontainers + mysql, annotate the test
 * with @Testcontainers / @Container MySQLContainer, and set the datasource
 * URL from the container. The test methods below do not change.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired AdminDashboardService dashboardService;

    @Test
    void completedBooking_countsZeroRevenue_notFullFare() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPlatformFee(new BigDecimal("0"));
        booking.setTotalFare(new BigDecimal("33280"));
        booking.setCompletedAt(java.time.LocalDateTime.now());
        bookingRepository.save(booking);

        AdminDashboardResponse dto = dashboardService.getDashboard();

        assertEquals(new BigDecimal("0.00"), dto.revenueThisMonth(),
                "One completed booking must add exactly 0 to revenue, not 33280");
        assertEquals(1L, dto.bookingsByStatus().get("COMPLETED"));
        assertEquals(1L, dto.totalBookings());
    }
}
