package com.truckconnect.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** One trip per booking — the lookup the customer uses. */
    Optional<Trip> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    /** The double-booking check: does this driver already have a trip
        in one of the given statuses? */
    boolean existsByDriverIdAndStatusIn(Long driverId, List<TripStatus> statuses);

    /** All trips whose truck belongs to this owner, newest first.
        (Spring Data reads "Truck_OwnerId" as trip -> truck -> owner -> id.) */
    List<Trip> findByTruck_OwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** All trips for one customer, via their bookings. */
    List<Trip> findByBooking_CustomerIdOrderByCreatedAtDesc(Long customerId);
}
