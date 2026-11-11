package com.truckconnect.booking;

import com.truckconnect.booking.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/bookings")
@PreAuthorize("hasRole('OWNER')")
public class OwnerBookingController {

    private final BookingService bookingService;

    public OwnerBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Incoming bookings for this owner's trucks, newest first. */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> incoming(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(bookingService.ownerBookings(owner));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<BookingResponse> accept(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.acceptBooking(owner, id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<BookingResponse> reject(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest req) {
        return ResponseEntity.ok(
                bookingService.rejectBooking(owner, id, req.reason()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BookingStatusUpdateRequest req) {
        String reason = req == null ? null : req.reason();
        return ResponseEntity.ok(
                bookingService.cancelByOwnerOrAdmin(owner, id, reason));
    }

    /** Move the trip forward (CONFIRMED -> DRIVER_ASSIGNED -> ... -> COMPLETED). */
    @PostMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateStatus(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest req) {
        return ResponseEntity.ok(bookingService.updateStatus(owner, id, req));
    }
}
