package com.truckconnect.booking;

import com.truckconnect.booking.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(customer, request));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<BookingResponse>> myBookings(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(bookingService.myBookings(customer));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<BookingResponse> getOne(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBooking(user, id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingResponse> cancel(
            @AuthenticationPrincipal User customer,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BookingStatusUpdateRequest req) {
        String reason = req == null ? null : req.reason();
        return ResponseEntity.ok(
                bookingService.cancelByCustomer(customer, id, reason));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<List<BookingMessageResponse>> messages(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getMessages(user, id));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')")
    public ResponseEntity<BookingMessageResponse> sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody BookingMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.sendMessage(user, id, request));
    }
}
