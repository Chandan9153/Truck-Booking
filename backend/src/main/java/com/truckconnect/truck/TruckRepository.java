package com.truckconnect.truck;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TruckRepository extends JpaRepository<Truck, Long> {
    boolean existsByRegistrationNumber(String registrationNumber);
    List<Truck> findByOwnerId(Long ownerId);
    Optional<Truck> findByIdAndOwnerId(Long id, Long ownerId);
    List<Truck> findByAvailabilityStatus(AvailabilityStatus status);
    long countByAvailabilityStatus(AvailabilityStatus status);
    // Module 9: trucks of one owner that are currently available
    List<Truck> findByOwnerIdAndAvailabilityStatus(Long ownerId, AvailabilityStatus status);
}
