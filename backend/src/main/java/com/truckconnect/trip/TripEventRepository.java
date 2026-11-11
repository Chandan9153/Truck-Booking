package com.truckconnect.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    /** A trip's whole timeline, oldest first — the order the customer reads. */
    List<TripEvent> findByTripIdOrderByCreatedAtAsc(Long tripId);
}
