package com.truckconnect.refund;

import com.truckconnect.payment.Payment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public Refund() { }

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment p) { this.payment = p; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }
    public String getReason() { return reason; }
    public void setReason(String r) { this.reason = r; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus s) { this.status = s; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String n) { this.adminNote = n; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime t) { this.processedAt = t; }
}
