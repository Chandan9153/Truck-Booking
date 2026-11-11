package com.truckconnect.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Idempotency lookup: one payment per booking, so this returns at most one row.
    Optional<Payment> findByBookingId(Long bookingId);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Payment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
