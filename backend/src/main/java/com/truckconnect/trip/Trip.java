package com.truckconnect.trip;

import com.truckconnect.booking.Booking;
import com.truckconnect.driver.Driver;
import com.truckconnect.truck.Truck;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.ASSIGNED;

    /** Copied from the booking when the trip is created (Part 3). */
    @Column(nullable = false, length = 200)
    private String pickup;

    @Column(nullable = false, length = 200)
    private String destination;

    /** Delivery proof (Part 4): a note and/or a pasted link. Both may be
        empty until DELIVERED — the service enforces that at least one is
        filled before DELIVERED is allowed. No file upload in this MVP. */
    @Column(name = "proof_note", length = 500)
    private String proofNote;

    @Column(name = "proof_url", length = 500)
    private String proofUrl;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Trip() { }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public Truck getTruck() { return truck; }
    public void setTruck(Truck truck) { this.truck = truck; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }
    public String getPickup() { return pickup; }
    public void setPickup(String pickup) { this.pickup = pickup; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getProofNote() { return proofNote; }
    public void setProofNote(String proofNote) { this.proofNote = proofNote; }
    public String getProofUrl() { return proofUrl; }
    public void setProofUrl(String proofUrl) { this.proofUrl = proofUrl; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(LocalDateTime pickedUpAt) { this.pickedUpAt = pickedUpAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
