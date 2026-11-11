package com.truckconnect.refund;

import com.truckconnect.payment.Payment;
import com.truckconnect.payment.PaymentRepository;
import com.truckconnect.payment.PaymentStatus;
import com.truckconnect.refund.dto.RefundRequest;
import com.truckconnect.refund.dto.RefundResponse;
import com.truckconnect.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Customer requests a refund for one of their own SUCCESSFUL payments. */
    @Transactional
    public RefundResponse requestRefund(User customer, RefundRequest req) {
        Payment payment = paymentRepository.findById(req.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + req.paymentId()));
        if (!payment.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This payment does not belong to you");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Only a SUCCESSFUL payment can be refunded (this one is " + payment.getStatus() + "). "
                    + "A PENDING COD payment on a cancelled booking is simply cancelled — no refund is needed.");
        }
        if (req.amount().compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund amount cannot be more than the payment amount");
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setAmount(req.amount());
        refund.setReason(req.reason());
        refund.setStatus(RefundStatus.PENDING);
        return RefundResponse.from(refundRepository.save(refund));
    }

    /**
     * Admin processes a refund. approve = true means "I have ALREADY
     * refunded this money manually outside the app (UPI / bank transfer)".
     * The payment becomes REFUNDED (full amount) or PARTIAL_REFUND (less).
     * approve = false means REJECTED; the payment stays SUCCESS.
     */
    @Transactional
    public RefundResponse processRefund(Long refundId, boolean approve, String adminNote) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("Refund is already " + refund.getStatus());
        }
        Payment payment = refund.getPayment();
        if (approve) {
            refund.setStatus(RefundStatus.PROCESSED);
            payment.setStatus(refund.getAmount().compareTo(payment.getAmount()) == 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIAL_REFUND);
            paymentRepository.save(payment);
        } else {
            refund.setStatus(RefundStatus.REJECTED);
        }
        refund.setAdminNote(adminNote);
        refund.setProcessedAt(LocalDateTime.now());
        return RefundResponse.from(refundRepository.save(refund));
    }
}
