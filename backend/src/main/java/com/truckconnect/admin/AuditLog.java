package com.truckconnect.admin;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;               // who did it (the logged-in admin / owner / system user)

    @Column(nullable = false, length = 60)
    private String action;            // e.g. BOOKING_CONFIRMED, PAYMENT_CONFIRMED

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;        // e.g. BOOKING, USER, PAYMENT, SETTLEMENT

    @Column(name = "target_id", nullable = false)
    private Long targetId;            // id of the booking / user / payment / settlement

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditLog() { }

    public AuditLog(User actor, String action, String targetType, Long targetId) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public Long getId() { return id; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
