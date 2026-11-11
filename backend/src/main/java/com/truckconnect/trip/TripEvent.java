package com.truckconnect.trip;

import com.truckconnect.user.Role;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_events")
public class TripEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private TripEventType eventType;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", nullable = false, length = 20)
    private Role createdByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public TripEvent() { }

    public TripEvent(Trip trip, TripEventType eventType, String note, Role createdByRole) {
        this.trip = trip;
        this.eventType = eventType;
        this.note = note;
        this.createdByRole = createdByRole;
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public TripEventType getEventType() { return eventType; }
    public void setEventType(TripEventType eventType) { this.eventType = eventType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Role getCreatedByRole() { return createdByRole; }
    public void setCreatedByRole(Role createdByRole) { this.createdByRole = createdByRole; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
