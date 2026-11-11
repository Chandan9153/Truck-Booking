package com.truckconnect.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Booking> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<Booking> findByTruckIdOrderByCreatedAtDesc(Long truckId);

    /** True if the truck already has a live booking - the no-double-booking check. */
    boolean existsByTruckIdAndStatusIn(Long truckId, Collection<BookingStatus> statuses);

    // --- Module 8: admin dashboard counts ---
    long countByStatus(BookingStatus status);
    List<Booking> findByStatus(BookingStatus status);

    // Revenue this month = SUM of the platform fee only, on COMPLETED bookings.
    // NEVER sum totalFare here - that money belongs to the owner, not the platform.
    // NOTE: Module 4's Booking has no completedAt field, so this uses updatedAt
    // (set when the status becomes COMPLETED) - see Module 8 Part 3 flag.
    @Query("SELECT COALESCE(SUM(b.platformFee), 0) FROM Booking b " +
           "WHERE b.status = 'COMPLETED' AND b.updatedAt >= :from")
    BigDecimal sumPlatformFeesSince(@Param("from") LocalDateTime from);

    // --- Module 9: live bookings for one load post ---
    int countByLoadPostIdAndStatusIn(Long loadPostId, Collection<BookingStatus> statuses);
}
