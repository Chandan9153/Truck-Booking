package com.truckconnect.payment;

import com.truckconnect.payment.dto.*;
import com.truckconnect.refund.RefundService;
import com.truckconnect.refund.dto.RefundRequest;
import com.truckconnect.refund.dto.RefundResponse;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InitiatePaymentResponse> initiate(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody InitiatePaymentRequest request) {
        try {
            return ResponseEntity.ok(paymentService.initiatePayment(customer, request));
        } catch (UnsupportedOperationException ex) {
            // Razorpay stub: online payment is not connected yet.
            // 503 (not 400, not 500) — the service is unavailable for now, not broken.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    new InitiatePaymentResponse(null, request.bookingId(), null,
                            request.method(), null, false,
                            "Online payment is not connected yet. Please pay by cash on delivery (COD)."));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> getOne(
            @AuthenticationPrincipal User customer,
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getMyPayment(customer, id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<PaymentResponse>> mine(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(paymentService.myPayments(customer));
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<RefundResponse> requestRefund(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundService.requestRefund(customer, request));
    }
}
