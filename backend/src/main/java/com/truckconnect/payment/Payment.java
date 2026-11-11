package com.truckconnect.payment;

import com.truckconnect.booking.Booking;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id"}))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference; // null for COD; Razorpay order/payment id later

    @Column(name = "paid_at")
    private LocalDateTime paidAt; // null until SUCCESS

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Payment() { }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking b) { this.booking = b; }
    public User getCustomer() { return customer; }
    public void setCustomer(User c) { this.customer = c; }
    public User getOwner() { return owner; }
    public void setOwner(User o) { this.owner = o; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod m) { this.method = m; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus s) { this.status = s; }
    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String g) { this.gatewayReference = g; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime p) { this.paidAt = p; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
