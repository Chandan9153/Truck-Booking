package com.truckconnect.pricing;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

    @Id
    private Long id = 1L;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee = new BigDecimal("0");

    @Column(name = "minimum_load_fraction", nullable = false, precision = 3, scale = 2)
    private BigDecimal minimumLoadFraction = new BigDecimal("0.50");

    public PlatformSettings() { }

    public Long getId() { return id; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal f) { this.platformFee = f; }
    public BigDecimal getMinimumLoadFraction() { return minimumLoadFraction; }
    public void setMinimumLoadFraction(BigDecimal f) { this.minimumLoadFraction = f; }
}
