package com.truckconnect.quote;

import com.truckconnect.loadpost.LoadPost;
import com.truckconnect.truck.Truck;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    // Module 9: the load post this quote was made for (null for a normal search).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_post_id")
    private LoadPost loadPost;

    public LoadPost getLoadPost() { return loadPost; }
    public void setLoadPost(LoadPost p) { this.loadPost = p; }

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

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE"; // ACTIVE, BOOKED, EXPIRED

    public Quote() { }

    // Getters and setters for every field (same pattern as Module 2 entities).
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getCustomer() { return customer; }
    public void setCustomer(User c) { this.customer = c; }
    public Truck getTruck() { return truck; }
    public void setTruck(Truck t) { this.truck = t; }
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
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime v) { this.validUntil = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
}
