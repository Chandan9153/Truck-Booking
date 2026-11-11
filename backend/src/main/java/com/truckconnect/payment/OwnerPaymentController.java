package com.truckconnect.payment;

import com.truckconnect.payment.dto.ConfirmCashPaymentRequest;
import com.truckconnect.payment.dto.PaymentResponse;
import com.truckconnect.settlement.SettlementService;
import com.truckconnect.settlement.dto.SettlementResponse;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/payments")
public class OwnerPaymentController {

    private final PaymentService paymentService;
    private final SettlementService settlementService;

    public OwnerPaymentController(PaymentService paymentService,
                                  SettlementService settlementService) {
        this.paymentService = paymentService;
        this.settlementService = settlementService;
    }

    @PostMapping("/{paymentId}/confirm-cash")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PaymentResponse> confirmCash(
            @AuthenticationPrincipal User owner,
            @PathVariable Long paymentId,
            @Valid @RequestBody ConfirmCashPaymentRequest request) {
        return ResponseEntity.ok(
                paymentService.confirmCashPayment(owner, paymentId, request));
    }

    @GetMapping("/settlements")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<SettlementResponse>> mySettlements(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(settlementService.mySettlements(owner.getId()));
    }
}
