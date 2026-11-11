package com.truckconnect.payment;

/**
 * A payment gateway is anything that can take money for a Payment.
 * Today only ManualPaymentGateway (COD) is real. RazorpayPaymentGateway
 * is a stub that says "not connected yet". A later module replaces
 * the stub with a real Razorpay implementation — this interface,
 * PaymentService, and the tables do not change.
 */
public interface PaymentGateway {

    /** Which method this gateway handles (COD, UPI, CARD, NETBANKING). */
    PaymentMethod supportedMethod();

    /**
     * Start a payment. For COD this just confirms the PENDING slip.
     * For a real online gateway (later) this creates the gateway order
     * and returns its reference. Never marks a payment SUCCESS by itself.
     */
    GatewayResult initiate(Payment payment);

    /**
     * Verify a gateway callback/webhook. For COD there is no webhook,
     * so the manual gateway returns false. For Razorpay (later) this
     * verifies the webhook signature — see RazorpayPaymentGateway.
     * A payment is NEVER marked SUCCESS unless this returns true
     * (or, for COD, unless the owner confirms cash in person).
     */
    boolean verifyWebhook(String payload, String signature);

    /** The result of starting a payment: what happened, and the gateway's reference (if any). */
    record GatewayResult(PaymentStatus status, String gatewayReference, String message) { }
}
