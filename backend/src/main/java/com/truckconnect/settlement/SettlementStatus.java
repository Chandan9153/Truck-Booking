package com.truckconnect.settlement;

public enum SettlementStatus {
    PENDING, // Settlement created at payment SUCCESS; admin has not settled it yet
    PAID     // Admin has done the manual transfer / received the fee and marked it settled
}
