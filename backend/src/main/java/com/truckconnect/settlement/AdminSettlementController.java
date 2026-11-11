package com.truckconnect.settlement;

import com.truckconnect.refund.RefundService;
import com.truckconnect.refund.dto.RefundResponse;
import com.truckconnect.settlement.dto.SettlementResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private final SettlementService settlementService;
    private final RefundService refundService;

    public AdminSettlementController(SettlementService settlementService,
                                     RefundService refundService) {
        this.settlementService = settlementService;
        this.refundService = refundService;
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<SettlementResponse>> allSettlements() {
        return ResponseEntity.ok(settlementService.allSettlements());
    }

    /** Admin has done the manual bank transfer outside the app — record it. */
    @PostMapping("/settlements/{id}/mark-paid")
    public ResponseEntity<SettlementResponse> markPaid(
            @PathVariable Long id,
            @RequestBody(required = false) MarkPaidRequest request) {
        String note = request == null ? null : request.note();
        return ResponseEntity.ok(settlementService.markPaid(id, note));
    }

    /** Admin processes a refund: approve = "I already refunded it by hand". */
    @PostMapping("/refunds/{id}/process")
    public ResponseEntity<RefundResponse> processRefund(
            @PathVariable Long id,
            @Valid @RequestBody ProcessRefundRequest request) {
        return ResponseEntity.ok(
                refundService.processRefund(id, request.approve(), request.adminNote()));
    }

    public record MarkPaidRequest(String note) { }

    public record ProcessRefundRequest(boolean approve, String adminNote) { }
}
