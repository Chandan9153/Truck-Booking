package com.truckconnect.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> list() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<AuditLog>> forBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("BOOKING", bookingId));
    }
}
