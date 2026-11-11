package com.truckconnect.trip;

import com.truckconnect.booking.Booking;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingService;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.common.ConflictException;
import com.truckconnect.driver.Driver;
import com.truckconnect.driver.DriverRepository;
import com.truckconnect.trip.dto.*;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TripService {

    /** A driver with a trip in one of these statuses is busy.
        COMPLETED and CANCELLED are not here — a finished trip frees the driver. */
    private static final List<TripStatus> ACTIVE_STATUSES = List.of(
            TripStatus.ASSIGNED, TripStatus.PICKED_UP, TripStatus.IN_TRANSIT,
            TripStatus.DELIVERED);

    /** The only allowed forward step from each status. There is no entry
        for COMPLETED or CANCELLED — nothing moves forward from there. */
    private static final Map<TripStatus, TripStatus> NEXT_STEP = Map.of(
            TripStatus.ASSIGNED, TripStatus.PICKED_UP,
            TripStatus.PICKED_UP, TripStatus.IN_TRANSIT,
            TripStatus.IN_TRANSIT, TripStatus.DELIVERED,
            TripStatus.DELIVERED, TripStatus.COMPLETED);

    private final TripRepository tripRepository;
    private final TripEventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;

    public TripService(TripRepository tripRepository,
                       TripEventRepository eventRepository,
                       BookingRepository bookingRepository,
                       BookingService bookingService,
                       DriverRepository driverRepository,
                       TruckRepository truckRepository) {
        this.tripRepository = tripRepository;
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.driverRepository = driverRepository;
        this.truckRepository = truckRepository;
    }

    // ---------- Assignment ----------

    @Transactional
    public TripResponse assignDriver(User owner, AssignDriverRequest req) {
        Booking booking = bookingRepository.findById(req.bookingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Booking not found: " + req.bookingId()));

        // Only the booking's owner may assign a driver to it.
        if (!booking.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException(
                    "You can only assign a driver to your own booking");
        }
        // Only a CONFIRMED booking can get a driver. A PENDING booking
        // has no agreed job yet; a REJECTED / CANCELLED one never will.
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only a CONFIRMED booking can be assigned a driver, current: "
                            + booking.getStatus());
        }
        // One trip per booking — the database unique constraint on
        // trips.booking_id is the backstop; this check gives a clear error.
        if (tripRepository.existsByBookingId(booking.getId())) {
            throw new ConflictException(
                    "Booking " + booking.getId() + " already has a trip");
        }

        Driver driver = driverRepository
                .findByIdAndOwnerId(req.driverId(), owner.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found: " + req.driverId()));
        if (!driver.isActive()) {
            throw new IllegalStateException(
                    "Driver " + driver.getName() + " is inactive");
        }
        // One driver, one active trip at a time (409 if busy).
        if (tripRepository.existsByDriverIdAndStatusIn(driver.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException(
                    "Driver " + driver.getName() + " already has an active trip");
        }

        Trip trip = new Trip();
        trip.setBooking(booking);
        trip.setTruck(booking.getTruck());
        trip.setDriver(driver);
        trip.setStatus(TripStatus.ASSIGNED);
        // --- Flagged lines (Part 0): if Module 4 named the address fields
        // differently, change only these two lines. ---
        trip.setPickup(booking.getPickup());
        trip.setDestination(booking.getDestination());
        trip.setStartedAt(LocalDateTime.now());
        trip = tripRepository.save(trip);

        // The BookingService hook (Part 9): CONFIRMED -> DRIVER_ASSIGNED.
        bookingService.markDriverAssigned(booking.getId());

        writeEvent(trip, TripEventType.ASSIGNED,
                "Driver " + driver.getName() + " assigned", owner.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Status updates ----------

    @Transactional
    public TripResponse updateStatus(User actor, Long tripId,
                                     TripStatusUpdateRequest req) {
        Trip trip = findTrip(tripId);
        checkCanManageTrip(actor, trip);

        TripStatus target = req.status();
        if (target == TripStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "A trip cannot go back to ASSIGNED; use reassign instead");
        }
        if (target == TripStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Use POST /api/trips/{id}/cancel to cancel a trip");
        }
        TripStatus expected = NEXT_STEP.get(trip.getStatus());
        if (expected == null || expected != target) {
            throw new IllegalStateException(
                    "Cannot move trip from " + trip.getStatus()
                            + " to " + target
                            + (expected == null
                                ? " — the trip is already finished"
                                : " — the next step is " + expected));
        }

        // Delivery proof (Part 4): DELIVERED needs a note or a link,
        // either sent now or already saved on the trip.
        if (req.proofNote() != null && !req.proofNote().isBlank()) {
            trip.setProofNote(req.proofNote().trim());
        }
        if (req.proofUrl() != null && !req.proofUrl().isBlank()) {
            trip.setProofUrl(req.proofUrl().trim());
        }
        if (target == TripStatus.DELIVERED && !hasProof(trip)) {
            throw new IllegalArgumentException(
                    "Delivery proof is required before DELIVERED: "
                            + "send proofNote or proofUrl");
        }

        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case PICKED_UP -> trip.setPickedUpAt(now);
            case DELIVERED -> trip.setDeliveredAt(now);
            case COMPLETED -> {
                trip.setCompletedAt(now);
                // The job is done: free the truck for the next booking.
                trip.getTruck().setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
                truckRepository.save(trip.getTruck());
            }
            default -> { /* IN_TRANSIT records no extra timestamp */ }
        }
        trip.setStatus(target);
        trip = tripRepository.save(trip);

        // Booking status moves in step (Part 2 map). The hook method in
        // BookingService does the booking half; the trip half is above.
        bookingService.updateBookingStatus(
                trip.getBooking().getId(), BookingStatus.valueOf(target.name()));

        writeEvent(trip, TripEventType.valueOf(target.name()),
                req.note(), actor.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Reassignment ----------

    @Transactional
    public TripResponse reassignDriver(User owner, Long tripId,
                                       ReassignDriverRequest req) {
        Trip trip = findTrip(tripId);
        checkCanManageTrip(owner, trip);
        // Only before pickup: once the load is on the truck, swapping the
        // driver mid-journey is a different (and riskier) operation.
        if (trip.getStatus() != TripStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Driver can only be reassigned before PICKED_UP, current: "
                            + trip.getStatus());
        }
        Driver newDriver = driverRepository
                .findByIdAndOwnerId(req.driverId(), owner.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found: " + req.driverId()));
        if (!newDriver.isActive()) {
            throw new IllegalStateException(
                    "Driver " + newDriver.getName() + " is inactive");
        }
        if (tripRepository.existsByDriverIdAndStatusIn(newDriver.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException(
                    "Driver " + newDriver.getName() + " already has an active trip");
        }
        String oldName = trip.getDriver().getName();
        trip.setDriver(newDriver);
        trip = tripRepository.save(trip);
        writeEvent(trip, TripEventType.NOTE,
                "Driver reassigned from " + oldName + " to " + newDriver.getName(),
                owner.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Cancellation ----------

    @Transactional
    public TripResponse cancelTrip(User actor, Long tripId, String reason) {
        Trip trip = findTrip(tripId);
        checkCanManageTrip(actor, trip);
        if (trip.getStatus() == TripStatus.DELIVERED
                || trip.getStatus() == TripStatus.COMPLETED
                || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel a trip in status " + trip.getStatus());
        }
        trip.setStatus(TripStatus.CANCELLED);
        trip = tripRepository.save(trip);
        bookingService.updateBookingStatus(
                trip.getBooking().getId(), BookingStatus.CANCELLED);
        // Free the truck: the job will not happen.
        trip.getTruck().setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        truckRepository.save(trip.getTruck());
        writeEvent(trip, TripEventType.NOTE,
                "Trip cancelled" + (reason == null || reason.isBlank()
                        ? "" : ": " + reason.trim()),
                actor.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Reads ----------

    @Transactional(readOnly = true)
    public TripResponse getTrip(User actor, Long tripId) {
        Trip trip = findTrip(tripId);
        checkCanViewTrip(actor, trip);
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse getTripForBooking(User actor, Long bookingId) {
        Trip trip = tripRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No trip found for booking: " + bookingId));
        checkCanViewTrip(actor, trip);
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripEventResponse> timeline(User actor, Long tripId) {
        Trip trip = findTrip(tripId);
        checkCanViewTrip(actor, trip);
        return eventRepository.findByTripIdOrderByCreatedAtAsc(tripId)
                .stream().map(TripEventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> myTrips(User owner) {
        return tripRepository.findByTruck_OwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(TripResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> myTripsAsCustomer(User customer) {
        return tripRepository.findByBooking_CustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(TripResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> allTrips() {
        return tripRepository.findAll().stream().map(TripResponse::from).toList();
    }

    // ---------- Helpers ----------

    private Trip findTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trip not found: " + tripId));
    }

    /** Who may change a trip: its owner (the driver belongs to them and
        the truck belongs to them) or an ADMIN. In this MVP there is no
        driver login, so the DRIVER role is not allowed here — see Part 1. */
    private void checkCanManageTrip(User actor, Trip trip) {
        boolean isOwner = trip.getDriver().getOwner().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You cannot manage this trip");
        }
    }

    /** Who may see a trip: its owner, its customer, or an ADMIN. */
    private void checkCanViewTrip(User actor, Trip trip) {
        boolean isOwner = trip.getDriver().getOwner().getId().equals(actor.getId());
        boolean isCustomer = trip.getBooking().getCustomer().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isOwner && !isCustomer && !isAdmin) {
            throw new AccessDeniedException("You cannot view this trip");
        }
    }

    private boolean hasProof(Trip trip) {
        return (trip.getProofNote() != null && !trip.getProofNote().isBlank())
                || (trip.getProofUrl() != null && !trip.getProofUrl().isBlank());
    }

    /** Every status change writes its event in the SAME transaction as
        the change itself. If the event write failed, the status change
        would roll back too — the timeline can never miss a step. */
    private void writeEvent(Trip trip, TripEventType type, String note, Role role) {
        eventRepository.save(new TripEvent(trip, type, note, role));
    }
}
