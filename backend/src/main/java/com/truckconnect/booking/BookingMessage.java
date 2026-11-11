package com.truckconnect.booking;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_messages")
public class BookingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public BookingMessage() { }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking b) { this.booking = b; }
    public User getSender() { return sender; }
    public void setSender(User s) { this.sender = s; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
