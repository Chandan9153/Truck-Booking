package com.truckconnect.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingMessageRepository extends JpaRepository<BookingMessage, Long> {
    /** Oldest first, so the frontend can render the thread top to bottom. */
    List<BookingMessage> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
