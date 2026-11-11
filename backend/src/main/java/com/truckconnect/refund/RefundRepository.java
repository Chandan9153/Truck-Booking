package com.truckconnect.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    List<Refund> findByStatusOrderByCreatedAtDesc(RefundStatus status);
}
