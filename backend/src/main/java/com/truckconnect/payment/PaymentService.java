package com.truckconnect.payment;

import com.truckconnect.booking.Booking;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.payment.dto.ConfirmCashPaymentRequest;
import com.truckconnect.payment.dto.InitiatePaymentRequest;
import com.truckconnect.payment.dto.InitiatePaymentResponse;
import com.truckconnect.payment.dto.PaymentResponse;
import com.truckconnect.settlement.SettlementService;
import com.truckconnect.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ManualPaymentGateway manualGateway;
    private final RazorpayPaymentGateway razorpayGateway;
    private final SettlementService settlementService;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          ManualPaymentGateway manualGateway,
                          RazorpayPaymentGateway razorpayGateway,
                          SettlementService settlementService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.manualGateway = manualGateway;
        this.razorpayGateway = razorpayGateway;
        this.settlementService = settlementService;
    }

    /**
     * Customer starts a payment for one of their own bookings.
     * Rule 1: the amount comes ONLY from booking.getTotalFare().
     * Rule 2: a second call for the same booking returns the existing
     * payment (idempotency key = booking id). No duplicate is created.
     * Rule 3: online methods return 503 today — the Razorpay stub throws,
     * and we turn that into "gateway not connected yet" with NO row created.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(User customer, InitiatePaymentRequest req) {
        Booking booking = bookingRepository.findById(req.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + req.bookingId()));

        // Ownership: a customer pays only for their own booking.
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This booking does not belong to you");
        }

        // Idempotency (code guard): return the existing payment, do not create another.
        var existing = paymentRepository.findByBookingId(booking.getId());
        if (existing.isPresent()) {
            Payment p = existing.get();
            return new InitiatePaymentResponse(p.getId(), booking.getId(), p.getAmount(),
                    p.getMethod(), p.getStatus(), true,
                    "Payment already exists for this booking — returning the existing payment.");
        }

        // Booking must be confirmed (or further along, up to DELIVERED) before anyone pays.
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not confirmed yet — the owner has not accepted it");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REJECTED) {
            throw new IllegalStateException("Booking is already " + booking.getStatus() + " — it cannot be paid");
        }

        // Online methods: gateway is not connected yet. No row is created.
        if (req.method() != PaymentMethod.COD) {
            // The stub throws UnsupportedOperationException on purpose.
            razorpayGateway.initiate(null);
            // Unreachable — the line above always throws. It is here so the
            // intent is visible: online goes through the gateway, never past it.
            throw new IllegalStateException("Online payment is not connected yet");
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setCustomer(booking.getCustomer());
        // If your Module 4 Booking has a direct getOwner(), use it here instead:
        //   payment.setOwner(booking.getOwner());
        payment.setOwner(booking.getTruck().getOwner());
        payment.setAmount(booking.getTotalFare()); // snapshot — never from the client
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setGatewayReference(null); // COD has no gateway reference

        manualGateway.initiate(payment); // returns PENDING; no money moves

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            // Race guard: two requests slipped past the lookup at the same
            // instant and the database's unique constraint on booking_id
            // refused the second row. Return the payment that did get saved.
            Payment saved = paymentRepository.findByBookingId(booking.getId())
                    .orElseThrow(() -> ex);
            return new InitiatePaymentResponse(saved.getId(), booking.getId(), saved.getAmount(),
                    saved.getMethod(), saved.getStatus(), true,
                    "Payment already exists for this booking — returning the existing payment.");
        }

        return new InitiatePaymentResponse(payment.getId(), booking.getId(), payment.getAmount(),
                payment.getMethod(), payment.getStatus(), false,
                "COD payment created. Pay in cash on delivery; the owner will confirm receipt.");
    }

    /**
     * Owner confirms cash was received, in person, on delivery.
     * Only allowed when: the caller owns the truck, the booking is
     * DELIVERED, the payment is PENDING, and the method is COD.
     * On success: payment SUCCESS + paidAt, booking DELIVERED -> COMPLETED,
     * and a settlement row is created (gross 33,280 / fee 0 / owner 33,280).
     */
    @Transactional
    public PaymentResponse confirmCashPayment(User owner, Long paymentId,
                                              ConfirmCashPaymentRequest req) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!payment.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("This payment does not belong to your truck");
        }
        if (payment.getMethod() != PaymentMethod.COD) {
            throw new IllegalStateException("Only COD payments can be confirmed in cash");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Payment is already successful");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is " + payment.getStatus() + " — it cannot be confirmed");
        }
        Booking booking = payment.getBooking();
        if (booking.getStatus() != BookingStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Booking is not delivered yet (it is " + booking.getStatus() + ") — "
                    + "cash can only be confirmed after delivery");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(java.time.LocalDateTime.now());
        payment = paymentRepository.save(payment);

        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        // The settlement is created here, and only here — at SUCCESS.
        settlementService.createSettlement(payment);

        return PaymentResponse.from(payment);
    }

    /**
     * Called by Module 4 when a booking is cancelled (before pickup).
     * A PENDING COD payment becomes CANCELLED — no money moved, so there
     * is no refund. A SUCCESS payment is left alone: the customer must
     * request a refund through RefundService (Part 5), and the admin
     * decides. Do NOT auto-refund here.
     */
    @Transactional
    public void cancelPendingPaymentForBooking(Long bookingId) {
        paymentRepository.findByBookingId(bookingId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        });
    }

    /** Customer reads one of their own payments. */
    @Transactional(readOnly = true)
    public PaymentResponse getMyPayment(User customer, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (!payment.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This payment does not belong to you");
        }
        return PaymentResponse.from(payment);
    }

    /** Customer lists their own payments, newest first. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> myPayments(User customer) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(PaymentResponse::from).toList();
    }
}
