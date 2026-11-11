package com.truckconnect.payment;

import org.springframework.stereotype.Component;

@Component
public class ManualPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.COD;
    }

    @Override
    public GatewayResult initiate(Payment payment) {
        // Cash changes hands in person on delivery. There is no gateway
        // order, no reference, and no SUCCESS here — the owner confirms
        // receipt later through PaymentService.confirmCashPayment().
        return new GatewayResult(PaymentStatus.PENDING, null,
                "Pay in cash on delivery. The owner will confirm receipt after delivery.");
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        // Manual (cash) payments have no webhook. There is nothing to verify.
        return false;
    }
}
