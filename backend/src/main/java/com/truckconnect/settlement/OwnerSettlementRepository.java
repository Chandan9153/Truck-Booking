package com.truckconnect.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OwnerSettlementRepository extends JpaRepository<OwnerSettlement, Long> {
    // Total still owed to owners: SUM of ownerAmount where the settlement is PENDING.
    @Query("SELECT COALESCE(SUM(s.ownerAmount), 0) FROM OwnerSettlement s " +
           "WHERE s.status = 'PENDING'")
    BigDecimal sumPendingSettlements();
    Optional<OwnerSettlement> findByBookingId(Long bookingId);
    List<OwnerSettlement> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<OwnerSettlement> findByStatusOrderByCreatedAtDesc(SettlementStatus status);
}
