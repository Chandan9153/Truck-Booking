package com.truckconnect.booking;

import com.truckconnect.booking.dto.*;
import com.truckconnect.quote.Quote;
import com.truckconnect.loadpost.LoadPostService;
import com.truckconnect.quote.QuoteRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMessageRepository messageRepository;
    private final QuoteRepository quoteRepository;
    private final TruckRepository truckRepository;
    // Module 9: @Lazy breaks the circle (LoadPostService also uses BookingService).
    private final LoadPostService loadPostService;

    public BookingService(BookingRepository bookingRepository,
                          BookingMessageRepository messageRepository,
                          QuoteRepository quoteRepository,
                          TruckRepository truckRepository,
                          @Lazy LoadPostService loadPostService) {
        this.bookingRepository = bookingRepository;
        this.messageRepository = messageRepository;
        this.quoteRepository = quoteRepository;
        this.truckRepository = truckRepository;
        this.loadPostService = loadPostService;
    }

    // ---------- Create: instant booking from a quote ----------

    /**
     * Creates a PENDING booking from a saved quote, at the quoted fixed price.
     * The whole method is ONE transaction, and the truck row is locked first:
     * a second request for the same truck waits, then sees the first booking
     * and fails with 409 instead of creating a double booking.
     */
    @Transactional
    public BookingResponse createBooking(User customer, CreateBookingRequest req) {
        Quote quote = quoteRepository.findById(req.quoteId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Quote not found: " + req.quoteId()));

        // The quote must belong to this customer — never book someone else's quote.
        if (!quote.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This quote does not belong to you");
        }
        // The quote must still be usable: ACTIVE, not expired, not already BOOKED.
        if (!"ACTIVE".equals(quote.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quote is no longer active (status: " + quote.getStatus() + ")");
        }
        if (quote.getValidUntil().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quote has expired. Please search again for a fresh quote.");
        }

        // Lock the truck row FIRST, then check — never check, then lock.
        Truck truck = truckRepository.findByIdForUpdate(quote.getTruck().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Truck not found"));

        // Guard 1: the truck itself must be free right now.
        if (truck.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Truck is not available right now");
        }
        // Guard 2: no other live booking may exist for this truck, even a
        // PENDING one (a PENDING booking reserves the truck — see Part 4).
        boolean alreadyBooked = bookingRepository.existsByTruckIdAndStatusIn(
                truck.getId(), BookingStatus.ACTIVE);
        if (alreadyBooked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Truck is already booked");
        }

        // Snapshot: copy EVERYTHING from the quote and the truck, right now.
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setOwner(truck.getOwner());
        booking.setTruck(truck);
        booking.setQuote(quote);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickup(quote.getPickup());
        booking.setDestination(quote.getDestination());
        booking.setDistanceKm(quote.getDistanceKm());
        booking.setLoadTons(quote.getLoadTons());
        booking.setGoodsType(quote.getGoodsType());
        booking.setTruckRegistrationNumber(truck.getRegistrationNumber());
        booking.setCategoryName(truck.getCategory().getName());
        booking.setTyreCount(truck.getCategory().getTyreCount());
        booking.setPayloadTonsAtBooking(truck.getPayloadTons());
        booking.setBodyType(truck.getBodyType());
        booking.setRatePerTonKm(quote.getRatePerTonKm());
        booking.setChargeableTons(quote.getChargeableTons());
        booking.setMinimumChargeApplied(quote.isMinimumChargeApplied());
        booking.setBaseFare(quote.getBaseFare());
        booking.setTollCharges(quote.getTollCharges());
        booking.setLoadingCharges(quote.getLoadingCharges());
        booking.setPlatformFee(quote.getPlatformFee());
        booking.setTotalFare(quote.getTotalFare());

        booking = bookingRepository.save(booking);

        // The quote is now used. It can never be booked a second time.
        quote.setStatus("BOOKED");
        quoteRepository.save(quote);

        return BookingMapper.toResponse(booking);
    }

    // ---------- Read: my bookings, one booking ----------

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(User customer) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(BookingMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> ownerBookings(User owner) {
        return bookingRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(BookingMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(User user, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireParty(user, booking);
        return BookingMapper.toResponse(booking);
    }

    // ---------- Owner: accept / reject ----------

    @Transactional
    public BookingResponse acceptBooking(User owner, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireOwner(owner, booking);
        // changeStatus checks the state machine: only PENDING -> CONFIRMED.
        changeStatus(booking, BookingStatus.CONFIRMED, null, null);
        // The truck is now committed: it leaves search until the trip ends.
        Truck truck = booking.getTruck();
        truck.setAvailabilityStatus(AvailabilityStatus.ON_TRIP);
        truckRepository.save(truck);
        return BookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse rejectBooking(User owner, Long bookingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required when rejecting a booking");
        }
        Booking booking = loadBooking(bookingId);
        requireOwner(owner, booking);
        changeStatus(booking, BookingStatus.REJECTED, reason, reason);
        // Truck was never taken, so it stays AVAILABLE. Nothing else to undo.
        // Module 9: a rejected booking frees its slot on the load post again.
        if (booking.getLoadPost() != null) {
            loadPostService.refreshPostStatus(booking.getLoadPost());
        }
        return BookingMapper.toResponse(booking);
    }

    // ---------- Cancel: the Part 4 table, in code ----------

    @Transactional
    public BookingResponse cancelByCustomer(User customer, Long bookingId, String reason) {
        Booking booking = loadBooking(bookingId);
        requireCustomer(customer, booking);
        switch (booking.getStatus()) {
            case PENDING, CONFIRMED -> {
                // Free cancel. The truck is released below.
            }
            case DRIVER_ASSIGNED, PICKED_UP -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking cannot be cancelled by the customer after a driver "
                    + "is assigned. Please contact the truck owner.");
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking in status " + booking.getStatus()
                    + " cannot be cancelled by the customer");
        }
        return doCancel(booking, reason);
    }

    @Transactional
    public BookingResponse cancelByOwnerOrAdmin(User user, Long bookingId, String reason) {
        Booking booking = loadBooking(bookingId);
        if (user.getRole() == Role.ADMIN) {
            // Admin may cancel any non-final booking; ownership does not apply.
        } else {
            requireOwner(user, booking);
        }
        if (BookingStatus.isFinal(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking in status " + booking.getStatus() + " cannot be cancelled");
        }
        return doCancel(booking, reason);
    }

    private BookingResponse doCancel(Booking booking, String reason) {
        changeStatus(booking, BookingStatus.CANCELLED, reason, null);
        // Release the truck so it can be booked again.
        Truck truck = booking.getTruck();
        if (truck.getAvailabilityStatus() == AvailabilityStatus.ON_TRIP) {
            truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            truckRepository.save(truck);
        }
        // Module 9: a cancelled booking frees its slot on the load post again.
        if (booking.getLoadPost() != null) {
            loadPostService.refreshPostStatus(booking.getLoadPost());
        }
        return BookingMapper.toResponse(booking);
    }

    // ---------- Move to the next trip status ----------

    @Transactional
    public BookingResponse updateStatus(User user, Long bookingId,
                                        BookingStatusUpdateRequest req) {
        Booking booking = loadBooking(bookingId);
        // In Module 4 the owner (or admin) moves the trip forward.
        // Module 5 hands the trip statuses to the driver.
        if (user.getRole() == Role.ADMIN) {
            // Admin may move any booking, except out of DISPUTED (handled below).
        } else {
            requireOwner(user, booking);
        }
        if (booking.getStatus() == BookingStatus.DISPUTED
                && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an admin can resolve a disputed booking");
        }
        if (req.status() == BookingStatus.CANCELLED
                || req.status() == BookingStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use the cancel / reject endpoint for "
                    + req.status() + ", not the status endpoint");
        }
        changeStatus(booking, req.status(), null, req.note());

        // Completing the trip frees the truck for the next booking.
        if (req.status() == BookingStatus.COMPLETED) {
            Truck truck = booking.getTruck();
            truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            truckRepository.save(truck);
        }
        return BookingMapper.toResponse(booking);
    }

    // ---------- Internal hooks used by TripService (Module 5) ----------
    // These move a booking's status without a user object: the trip service
    // already checked that the caller is the owner/driver. They reuse the
    // single changeStatus() rule above, so no invalid move is possible.

    @Transactional
    public void markDriverAssigned(Long bookingId) {
        Booking booking = loadBooking(bookingId);
        changeStatus(booking, BookingStatus.DRIVER_ASSIGNED, null, null);
    }

    @Transactional
    public void updateBookingStatus(Long bookingId, BookingStatus to) {
        Booking booking = loadBooking(bookingId);
        changeStatus(booking, to, null, null);
        if (to == BookingStatus.COMPLETED) {
            Truck truck = booking.getTruck();
            truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            truckRepository.save(truck);
        }
    }

    // ---------- Messages: booking chat ----------

    @Transactional
    public BookingMessageResponse sendMessage(User user, Long bookingId,
                                              BookingMessageRequest req) {
        Booking booking = loadBooking(bookingId);
        requireParty(user, booking);
        if (BookingStatus.isFinal(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot message on a booking that is " + booking.getStatus());
        }
        BookingMessage msg = new BookingMessage();
        msg.setBooking(booking);
        msg.setSender(user);
        msg.setMessage(req.message().trim());
        msg = messageRepository.save(msg);
        return toMessageResponse(msg);
    }

    @Transactional(readOnly = true)
    public List<BookingMessageResponse> getMessages(User user, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireParty(user, booking);
        return messageRepository.findByBookingIdOrderByCreatedAtAsc(bookingId)
                .stream().map(this::toMessageResponse).toList();
    }

    private BookingMessageResponse toMessageResponse(BookingMessage m) {
        return new BookingMessageResponse(
                m.getId(), m.getBooking().getId(), m.getSender().getId(),
                m.getSender().getFullName(), m.getSender().getRole().name(),
                m.getMessage(), m.getCreatedAt());
    }

    // ---------- Helpers: the one status-change method, and ownership ----------

    /** The ONLY place a booking's status changes. Every move is checked here. */
    private void changeStatus(Booking booking, BookingStatus to,
                              String reason, String note) {
        if (!BookingStatus.canMoveTo(booking.getStatus(), to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid status change from " + booking.getStatus()
                    + " to " + to);
        }
        booking.setStatus(to);
        if (to == BookingStatus.COMPLETED) {
            booking.setCompletedAt(LocalDateTime.now());
        }
        if (reason != null) booking.setCancelReason(reason);
        if (note != null) booking.setOwnerNote(note);
        bookingRepository.save(booking);
    }

    private Booking loadBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));
    }

    /** Customer, owner, or admin — anyone else gets 403. */
    private void requireParty(User user, Booking booking) {
        boolean isCustomer = booking.getCustomer().getId().equals(user.getId());
        boolean isOwner = booking.getOwner().getId().equals(user.getId());
        if (!isCustomer && !isOwner && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not a party to this booking");
        }
    }

    private void requireCustomer(User customer, Booking booking) {
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This is not your booking");
        }
    }

    private void requireOwner(User owner, Booking booking) {
        if (!booking.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This booking is not for one of your trucks");
        }
    }
}
