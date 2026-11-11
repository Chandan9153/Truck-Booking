package com.truckconnect.settlement;

import com.truckconnect.payment.Payment;
import com.truckconnect.payment.PaymentStatus;
import com.truckconnect.pricing.PlatformSettingsRepository;
import com.truckconnect.settlement.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettlementService {

    private final OwnerSettlementRepository settlementRepository;
    private final PlatformSettingsRepository settingsRepository;

    public SettlementService(OwnerSettlementRepository settlementRepository,
                             PlatformSettingsRepository settingsRepository) {
        this.settlementRepository = settlementRepository;
        this.settingsRepository = settingsRepository;
    }

    /**
     * Create the settlement for a SUCCESSFUL payment.
     * gross  = payment amount (e.g. 33,280) — the snapshot from the booking.
     * fee    = platform fee from Module 3 settings (0) — never hardcoded here.
     * owner  = gross - fee (e.g. 33,280). That is the whole arithmetic.
     * Called only from PaymentService.confirmCashPayment (and, later,
     * from the Razorpay webhook handler when a payment becomes SUCCESS).
     */
    @Transactional
    public OwnerSettlement createSettlement(Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Settlement can only be created for a SUCCESSFUL payment");
        }
        // One settlement per booking: if it already exists, return it.
        var existing = settlementRepository.findByBookingId(payment.getBooking().getId());
        if (existing.isPresent()) return existing.get();

        BigDecimal fee = settingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Platform settings not found"))
                .getPlatformFee();

        OwnerSettlement settlement = new OwnerSettlement();
        settlement.setOwner(payment.getOwner());
        settlement.setBooking(payment.getBooking());
        settlement.setPayment(payment);
        settlement.setGrossAmount(payment.getAmount());
        settlement.setPlatformFee(fee);
        settlement.setOwnerAmount(payment.getAmount().subtract(fee));
        settlement.setStatus(SettlementStatus.PENDING);
        return settlementRepository.save(settlement);
    }

    /**
     * Admin marks a settlement PAID after doing the manual bank transfer
     * OUTSIDE the app (for online payments later; for COD there is no
     * transfer — the owner already holds the cash, see Part 4). This method
     * moves no money; it records that the money already moved.
     */
    @Transactional
    public SettlementResponse markPaid(Long settlementId, String note) {
        OwnerSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));
        if (settlement.getStatus() == SettlementStatus.PAID) {
            throw new IllegalStateException("Settlement is already PAID");
        }
        settlement.setStatus(SettlementStatus.PAID);
        settlement.setSettledAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) settlement.setNote(note);
        return SettlementResponse.from(settlementRepository.save(settlement));
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> mySettlements(Long ownerId) {
        return settlementRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(SettlementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> allSettlements() {
        return settlementRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(SettlementResponse::from).toList();
    }
}
