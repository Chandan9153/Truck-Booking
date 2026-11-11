package com.truckconnect.admin;

import com.truckconnect.admin.dto.DisputeResolveRequest;
import com.truckconnect.booking.Booking;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class DisputeController {

    private final BookingRepository bookingRepository;
    private final AuditService auditService;

    public DisputeController(BookingRepository bookingRepository, AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<Booking>> listDisputed() {
        return ResponseEntity.ok(bookingRepository.findByStatus(BookingStatus.DISPUTED));
    }

    @PostMapping("/{bookingId}/resolve")
    public ResponseEntity<Booking> resolve(
            @PathVariable Long bookingId,
            @Valid @RequestBody DisputeResolveRequest req,
            @AuthenticationPrincipal User admin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.DISPUTED) {
            throw new IllegalStateException(
                    "Only a DISPUTED booking can be resolved. Current status: "
                    + booking.getStatus());
        }
        BookingStatus resolution;
        try {
            resolution = BookingStatus.valueOf(req.resolution());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "resolution must be COMPLETED or CANCELLED, not: " + req.resolution());
        }
        if (resolution != BookingStatus.COMPLETED
                && resolution != BookingStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "resolution must be COMPLETED or CANCELLED, not: " + req.resolution());
        }
        booking.setStatus(resolution);
        // Store the admin's reason on the booking if Module 4 gave it a field
        // for it (for example adminNote). If your Booking has no such field,
        // the note still survives in the audit log line below — do not add a
        // column just for this; the audit log is the record.
        bookingRepository.save(booking);
        auditService.log(admin, "DISPUTE_RESOLVED_" + resolution.name(),
                "BOOKING", booking.getId());
        return ResponseEntity.ok(booking);
    }
}
