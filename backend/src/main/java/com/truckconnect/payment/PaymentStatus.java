package com.truckconnect.payment;

public enum PaymentStatus {
    PENDING,        // COD slip created, waiting for delivery + owner confirm
    INITIATED,      // Online payment started with the gateway, waiting for its answer (later only)
    SUCCESS,        // Money verified as received (owner confirm for COD; webhook for online later)
    FAILED,         // Gateway said the online payment failed (later only)
    REFUNDED,       // Full amount refunded by admin after a SUCCESS payment
    PARTIAL_REFUND, // Part of the amount refunded by admin after a SUCCESS payment
    CANCELLED       // Booking was cancelled while the payment was still PENDING; no money moved
}
