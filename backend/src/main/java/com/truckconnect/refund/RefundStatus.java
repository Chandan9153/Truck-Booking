package com.truckconnect.refund;

public enum RefundStatus {
    PENDING,   // Customer asked; admin has not decided yet
    PROCESSED, // Admin approved AND refunded the money manually outside the app
    REJECTED   // Admin refused; the payment stays SUCCESS
}
