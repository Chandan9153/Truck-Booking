package com.truckconnect.settlement;

import com.truckconnect.booking.Booking;
import com.truckconnect.payment.Payment;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "owner_settlements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id"}))
public class OwnerSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "owner_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal ownerAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(length = 500)
    private String note;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OwnerSettlement() { }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User o) { this.owner = o; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking b) { this.booking = b; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment p) { this.payment = p; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal v) { this.grossAmount = v; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal v) { this.platformFee = v; }
    public BigDecimal getOwnerAmount() { return ownerAmount; }
    public void setOwnerAmount(BigDecimal v) { this.ownerAmount = v; }
    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus s) { this.status = s; }
    public String getNote() { return note; }
    public void setNote(String n) { this.note = n; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime t) { this.settledAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
