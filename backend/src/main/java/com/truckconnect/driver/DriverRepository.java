package com.truckconnect.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    /** All drivers of one owner, newest first. */
    List<Driver> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** Only the owner's ACTIVE drivers — the list assignment offers. */
    List<Driver> findByOwnerIdAndActiveTrue(Long ownerId);

    /** One driver, but only if they belong to this owner.
        Returns empty for another owner's driver — that is the ownership
        check, done by the database query itself. */
    Optional<Driver> findByIdAndOwnerId(Long id, Long ownerId);
}
