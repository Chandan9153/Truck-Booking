package com.truckconnect.pricing;

import com.truckconnect.truck.TruckCategory;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pricing_rates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"category_id"}))
public class PricingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private TruckCategory category;

    @Column(name = "rate_per_ton_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerTonKm;

    @Column(nullable = false)
    private boolean active = true;

    public PricingRate() { }

    public PricingRate(TruckCategory category, BigDecimal ratePerTonKm) {
        this.category = category;
        this.ratePerTonKm = ratePerTonKm;
    }

    public Long getId() { return id; }
    public TruckCategory getCategory() { return category; }
    public void setCategory(TruckCategory c) { this.category = c; }
    public BigDecimal getRatePerTonKm() { return ratePerTonKm; }
    public void setRatePerTonKm(BigDecimal r) { this.ratePerTonKm = r; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
}
