package com.truckconnect.payment;

import org.springframework.stereotype.Component;

/**
 * RAZORPAY — LATER / NOT IN THIS MODULE.
 *
 * No Razorpay account, no key id, no key secret, no SDK, and no real
 * money exist in this module. This stub exists only so PaymentService
 * can ask for "the gateway for UPI/CARD/NETBANKING" and get a clear
 * "not connected yet" answer instead of a NullPointerException.
 *
 * When Razorpay IS added (a later module, with a real account):
 *  1. Add the Razorpay Java SDK to pom.xml (not now).
 *  2. Store the key id and key secret in environment variables only
 *     (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET) — never in code, never in Git.
 *  3. Replace the bodies below: initiate() creates a Razorpay order and
 *     returns its id as gatewayReference with status INITIATED; and
 *     verifyWebhookSignature() recomputes the HMAC-SHA256 signature
 *     over the webhook payload with the secret and compares it with
 *     the signature Razorpay sent. Only if they match may the payment
 *     become SUCCESS. See PaymentService for where that check is called.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod supportedMethod() {
        // This stub claims UPI; PaymentService only ever asks for ONE
        // gateway per method, so CARD/NETBANKING also route here for now
        // and get the same "not connected yet" answer.
        return PaymentMethod.UPI;
    }

    @Override
    public GatewayResult initiate(Payment payment) {
        throw new UnsupportedOperationException(
                "Online payment is not connected yet. Razorpay will be added in a later module. "
                + "Please pay by cash on delivery (COD).");
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        throw new UnsupportedOperationException(
                "Online payment is not connected yet. Webhook verification will be added "
                + "with Razorpay in a later module.");
    }

    /**
     * STUB — where webhook signature verification will live later.
     * In words, the real version will: take the raw webhook body
     * (payload), the signature Razorpay sent in its header, and the
     * webhook secret (from an environment variable, never hardcoded);
     * compute HMAC-SHA256 of the payload with the secret; and return
     * true only if the computed value equals the sent signature.
     * A payment must NEVER be marked SUCCESS unless this returns true.
     */
    public boolean verifyWebhookSignature(String payload, String signature, String webhookSecret) {
        throw new UnsupportedOperationException(
                "Online payment is not connected yet. Webhook signature verification "
                + "will be added with Razorpay in a later module.");
    }
}
