package com.truckconnect.trip;

import com.truckconnect.trip.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
    public ResponseEntity<TripResponse> assign(
            @AuthenticationPrincipal User owner,
            @Valid @RequestBody AssignDriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripService.assignDriver(owner, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> updateStatus(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id,
            @Valid @RequestBody TripStatusUpdateRequest request) {
        return ResponseEntity.ok(tripService.updateStatus(actor, id, request));
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
    public ResponseEntity<TripResponse> reassign(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody ReassignDriverRequest request) {
        return ResponseEntity.ok(tripService.reassignDriver(owner, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> cancel(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(tripService.cancelTrip(actor, id, reason));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> get(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTrip(actor, id));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> getForBooking(
            @AuthenticationPrincipal User actor,
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(tripService.getTripForBooking(actor, bookingId));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<List<TripEventResponse>> timeline(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.timeline(actor, id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
    public ResponseEntity<List<TripResponse>> myTrips(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(tripService.myTrips(owner));
    }

    @GetMapping("/mine/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TripResponse>> myTripsAsCustomer(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(tripService.myTripsAsCustomer(customer));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TripResponse>> allTrips() {
        return ResponseEntity.ok(tripService.allTrips());
    }
}
