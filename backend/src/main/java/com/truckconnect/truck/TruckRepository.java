package com.truckconnect.truck;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TruckRepository extends JpaRepository<Truck, Long> {
    // Pessimistic lock used by BookingService: no two bookings can take the
    // same truck at the same time (Module 4 no-double-booking rule).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Truck t WHERE t.id = :id")
    Optional<Truck> findByIdForUpdate(@Param("id") Long id);
    boolean existsByRegistrationNumber(String registrationNumber);
    List<Truck> findByOwnerId(Long ownerId);
    Optional<Truck> findByIdAndOwnerId(Long id, Long ownerId);
    List<Truck> findByAvailabilityStatus(AvailabilityStatus status);
    long countByAvailabilityStatus(AvailabilityStatus status);
    // Module 9: trucks of one owner that are currently available
    List<Truck> findByOwnerIdAndAvailabilityStatus(Long ownerId, AvailabilityStatus status);
}
