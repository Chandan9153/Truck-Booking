package com.truckconnect.booking;

import com.truckconnect.admin.AdminDashboardService;
import com.truckconnect.admin.dto.AdminDashboardResponse;
import com.truckconnect.quote.Quote;
import com.truckconnect.quote.QuoteRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.BodyType;
import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckCategory;
import com.truckconnect.truck.TruckCategoryRepository;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 *
 * NOTE 2: a Booking cannot be saved bare — customer, owner, truck and quote
 * are all NOT NULL, as are the snapshot pickup/fare fields. So this test
 * builds the whole chain first (users, category, truck, quote), then the
 * COMPLETED booking on top of it.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired TruckCategoryRepository categoryRepository;
    @Autowired TruckRepository truckRepository;
    @Autowired QuoteRepository quoteRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired AdminDashboardService dashboardService;

    private User user(String name, String email, Role role) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPhone("9999999999");
        u.setPasswordHash("hashed");
        u.setRole(role);
        return userRepository.save(u);
    }

    @Test
    void completedBooking_countsZeroRevenue_notFullFare() {
        User customer = user("Test Customer", "customer@test.local", Role.CUSTOMER);
        User owner = user("Test Owner", "owner@test.local", Role.OWNER);

        // The app seeds the 4 categories at startup, so reuse the seeded one
        // instead of inserting a duplicate name (unique constraint).
        TruckCategory category = categoryRepository.findByName("12 Tyre Truck")
                .orElseGet(() -> {
                    TruckCategory c = new TruckCategory();
                    c.setName("12 Tyre Truck");
                    c.setTyreCount(12);
                    c.setTargetPayloadTons(new BigDecimal("20"));
                    c.setDescription("12 tyre / 20 tons");
                    return categoryRepository.save(c);
                });

        Truck truck = new Truck();
        truck.setRegistrationNumber("KA01AA0001");
        truck.setCategory(category);
        truck.setTyreCount(12);
        truck.setPermittedPayloadTons(new BigDecimal("20"));
        truck.setGrossVehicleWeightTons(new BigDecimal("25"));
        truck.setBodyType(BodyType.OPEN);
        truck.setLengthFeet(new BigDecimal("20"));
        truck.setWidthFeet(new BigDecimal("8"));
        truck.setHeightFeet(new BigDecimal("8"));
        truck.setOwner(owner);
        truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        truck = truckRepository.save(truck);

        Quote quote = new Quote();
        quote.setCustomer(customer);
        quote.setTruck(truck);
        quote.setPickup("Bengaluru");
        quote.setDestination("Hyderabad");
        quote.setDistanceKm(new BigDecimal("570"));
        quote.setLoadTons(new BigDecimal("20"));
        quote.setRatePerTonKm(new BigDecimal("2.70"));
        quote.setChargeableTons(new BigDecimal("20"));
        quote.setMinimumChargeApplied(false);
        quote.setBaseFare(new BigDecimal("30780"));
        quote.setTollCharges(new BigDecimal("1500"));
        quote.setLoadingCharges(new BigDecimal("1000"));
        quote.setPlatformFee(new BigDecimal("0"));
        quote.setTotalFare(new BigDecimal("33280"));
        quote.setValidUntil(LocalDateTime.now().plusHours(1));
        quote.setStatus("ACTIVE");
        quote = quoteRepository.save(quote);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setOwner(owner);
        booking.setTruck(truck);
        booking.setQuote(quote);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPickup("Bengaluru");
        booking.setDestination("Hyderabad");
        booking.setDistanceKm(new BigDecimal("570"));
        booking.setLoadTons(new BigDecimal("20"));
        booking.setTruckRegistrationNumber("KA01AA0001");
        booking.setCategoryName("12 Tyre Truck");
        booking.setTyreCount(12);
        booking.setPayloadTonsAtBooking(new BigDecimal("20"));
        booking.setBodyType(BodyType.OPEN);
        booking.setRatePerTonKm(new BigDecimal("2.70"));
        booking.setChargeableTons(new BigDecimal("20"));
        booking.setMinimumChargeApplied(false);
        booking.setBaseFare(new BigDecimal("30780"));
        booking.setTollCharges(new BigDecimal("1500"));
        booking.setLoadingCharges(new BigDecimal("1000"));
        booking.setPlatformFee(new BigDecimal("0"));
        booking.setTotalFare(new BigDecimal("33280"));
        booking.setCompletedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        AdminDashboardResponse dto = dashboardService.getDashboard();

        assertEquals(new BigDecimal("0.00"), dto.revenueThisMonth(),
                "One completed booking must add exactly 0 to revenue, not 33280");
        assertEquals(1L, dto.bookingsByStatus().get("COMPLETED"));
        assertEquals(1L, dto.totalBookings());
    }
}
