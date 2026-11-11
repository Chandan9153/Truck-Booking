package com.truckconnect.loadpost;

import com.truckconnect.truck.BodyType;
import com.truckconnect.truck.TruckCategory;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "load_posts")
public class LoadPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posted_by_id")
    private User postedBy;

    @Column(name = "goods_description", nullable = false, length = 200)
    private String goodsDescription;

    @Column(name = "total_tons", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTons;

    @Column(name = "trucks_needed", nullable = false)
    private int trucksNeeded;

    // Calculated once at posting: totalTons / trucksNeeded. Never typed by the client.
    @Column(name = "tons_per_truck", nullable = false, precision = 10, scale = 2)
    private BigDecimal tonsPerTruck;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private TruckCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", length = 20)
    private BodyType bodyType; // null = any body

    @Column(nullable = false, length = 120)
    private String pickup;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "toll_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal tollCharges = BigDecimal.ZERO;

    @Column(name = "loading_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal loadingCharges = BigDecimal.ZERO;

    @Column(name = "contact_phone", length = 15)
    private String contactPhone; // null = use postedBy's phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoadPostStatus status = LoadPostStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public LoadPost() { }

    // Getters and setters for every field (same pattern as Modules 1-4).
    public Long getId() { return id; }
    public User getPostedBy() { return postedBy; }
    public void setPostedBy(User u) { this.postedBy = u; }
    public String getGoodsDescription() { return goodsDescription; }
    public void setGoodsDescription(String v) { this.goodsDescription = v; }
    public BigDecimal getTotalTons() { return totalTons; }
    public void setTotalTons(BigDecimal v) { this.totalTons = v; }
    public int getTrucksNeeded() { return trucksNeeded; }
    public void setTrucksNeeded(int v) { this.trucksNeeded = v; }
    public BigDecimal getTonsPerTruck() { return tonsPerTruck; }
    public void setTonsPerTruck(BigDecimal v) { this.tonsPerTruck = v; }
    public TruckCategory getCategory() { return category; }
    public void setCategory(TruckCategory c) { this.category = c; }
    public BodyType getBodyType() { return bodyType; }
    public void setBodyType(BodyType b) { this.bodyType = b; }
    public String getPickup() { return pickup; }
    public void setPickup(String p) { this.pickup = p; }
    public String getDestination() { return destination; }
    public void setDestination(String d) { this.destination = d; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal v) { this.distanceKm = v; }
    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate d) { this.pickupDate = d; }
    public BigDecimal getTollCharges() { return tollCharges; }
    public void setTollCharges(BigDecimal v) { this.tollCharges = v; }
    public BigDecimal getLoadingCharges() { return loadingCharges; }
    public void setLoadingCharges(BigDecimal v) { this.loadingCharges = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String p) { this.contactPhone = p; }
    public LoadPostStatus getStatus() { return status; }
    public void setStatus(LoadPostStatus s) { this.status = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
