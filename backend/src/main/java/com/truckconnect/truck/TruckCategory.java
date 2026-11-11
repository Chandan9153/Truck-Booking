package com.truckconnect.truck;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "truck_categories")
public class TruckCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false)
    private int tyreCount;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPayloadTons;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public TruckCategory() { }

    public TruckCategory(String name, int tyreCount,
                         BigDecimal targetPayloadTons, String description) {
        this.name = name;
        this.tyreCount = tyreCount;
        this.targetPayloadTons = targetPayloadTons;
        this.description = description;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTyreCount() { return tyreCount; }
    public void setTyreCount(int tyreCount) { this.tyreCount = tyreCount; }
    public BigDecimal getTargetPayloadTons() { return targetPayloadTons; }
    public void setTargetPayloadTons(BigDecimal t) { this.targetPayloadTons = t; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
