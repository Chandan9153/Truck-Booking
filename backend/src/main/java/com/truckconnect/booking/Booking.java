package com.truckconnect.booking;

import com.truckconnect.loadpost.LoadPost;
import com.truckconnect.quote.Quote;
import com.truckconnect.truck.BodyType;
import com.truckconnect.truck.Truck;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Links: who, which truck, which quote ---
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    // Module 9: the load post this booking was made from (null for a normal booking).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_post_id")
    private LoadPost loadPost;

    public LoadPost getLoadPost() { return loadPost; }
    public void setLoadPost(LoadPost p) { this.loadPost = p; }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    // --- Snapshot: the trip, copied from the quote at creation ---
    @Column(nullable = false, length = 120)
    private String pickup;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "load_tons", nullable = false, precision = 10, scale = 2)
    private BigDecimal loadTons;

    @Column(name = "goods_type", length = 120)
    private String goodsType;

    // --- Snapshot: the truck as it was at booking time ---
    @Column(name = "truck_registration_number", nullable = false, length = 20)
    private String truckRegistrationNumber;

    @Column(name = "category_name", nullable = false, length = 60)
    private String categoryName;

    @Column(name = "tyre_count", nullable = false)
    private int tyreCount;

    @Column(name = "payload_tons_at_booking", nullable = false, precision = 10, scale = 2)
    private BigDecimal payloadTonsAtBooking;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", length = 20)
    private BodyType bodyType;

    // --- Snapshot: the money, copied from the quote at creation ---
    @Column(name = "rate_per_ton_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerTonKm;

    @Column(name = "chargeable_tons", nullable = false, precision = 10, scale = 2)
    private BigDecimal chargeableTons;

    @Column(name = "minimum_charge_applied", nullable = false)
    private boolean minimumChargeApplied;

    @Column(name = "base_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "toll_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal tollCharges;

    @Column(name = "loading_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal loadingCharges;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "total_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalFare;

    // --- Lifecycle notes ---
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "owner_note", length = 500)
    private String ownerNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Module 8 assumed this field exists (the dashboard revenue query was
    // written against updatedAt as a fallback). It records when a booking
    // actually completed; null until then.
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Booking() { }

    // Getters and setters for every field (same pattern as Modules 1-3).
    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public void setCustomer(User c) { this.customer = c; }
    public User getOwner() { return owner; }
    public void setOwner(User o) { this.owner = o; }
    public Truck getTruck() { return truck; }
    public void setTruck(Truck t) { this.truck = t; }
    public Quote getQuote() { return quote; }
    public void setQuote(Quote q) { this.quote = q; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus s) { this.status = s; }
    public String getPickup() { return pickup; }
    public void setPickup(String p) { this.pickup = p; }
    public String getDestination() { return destination; }
    public void setDestination(String d) { this.destination = d; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal v) { this.distanceKm = v; }
    public BigDecimal getLoadTons() { return loadTons; }
    public void setLoadTons(BigDecimal v) { this.loadTons = v; }
    public String getGoodsType() { return goodsType; }
    public void setGoodsType(String g) { this.goodsType = g; }
    public String getTruckRegistrationNumber() { return truckRegistrationNumber; }
    public void setTruckRegistrationNumber(String v) { this.truckRegistrationNumber = v; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String v) { this.categoryName = v; }
    public int getTyreCount() { return tyreCount; }
    public void setTyreCount(int v) { this.tyreCount = v; }
    public BigDecimal getPayloadTonsAtBooking() { return payloadTonsAtBooking; }
    public void setPayloadTonsAtBooking(BigDecimal v) { this.payloadTonsAtBooking = v; }
    public BodyType getBodyType() { return bodyType; }
    public void setBodyType(BodyType b) { this.bodyType = b; }
    public BigDecimal getRatePerTonKm() { return ratePerTonKm; }
    public void setRatePerTonKm(BigDecimal v) { this.ratePerTonKm = v; }
    public BigDecimal getChargeableTons() { return chargeableTons; }
    public void setChargeableTons(BigDecimal v) { this.chargeableTons = v; }
    public boolean isMinimumChargeApplied() { return minimumChargeApplied; }
    public void setMinimumChargeApplied(boolean b) { this.minimumChargeApplied = b; }
    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal v) { this.baseFare = v; }
    public BigDecimal getTollCharges() { return tollCharges; }
    public void setTollCharges(BigDecimal v) { this.tollCharges = v; }
    public BigDecimal getLoadingCharges() { return loadingCharges; }
    public void setLoadingCharges(BigDecimal v) { this.loadingCharges = v; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal v) { this.platformFee = v; }
    public BigDecimal getTotalFare() { return totalFare; }
    public void setTotalFare(BigDecimal v) { this.totalFare = v; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String r) { this.cancelReason = r; }
    public String getOwnerNote() { return ownerNote; }
    public void setOwnerNote(String n) { this.ownerNote = n; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }
}
