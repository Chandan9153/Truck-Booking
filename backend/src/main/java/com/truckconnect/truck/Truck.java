package com.truckconnect.truck;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trucks")
public class Truck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TruckCategory category;

    @Column(nullable = false)
    private int tyreCount;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal permittedPayloadTons;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal grossVehicleWeightTons;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BodyType bodyType;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal lengthFeet;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal widthFeet;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal heightFeet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.UNAVAILABLE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Truck() { }

    public Long getId() { return id; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String v) { this.registrationNumber = v; }
    public TruckCategory getCategory() { return category; }
    public void setCategory(TruckCategory category) { this.category = category; }
    public int getTyreCount() { return tyreCount; }
    public void setTyreCount(int tyreCount) { this.tyreCount = tyreCount; }
    public BigDecimal getPermittedPayloadTons() { return permittedPayloadTons; }
    public void setPermittedPayloadTons(BigDecimal v) { this.permittedPayloadTons = v; }
    public BigDecimal getGrossVehicleWeightTons() { return grossVehicleWeightTons; }
    public void setGrossVehicleWeightTons(BigDecimal v) { this.grossVehicleWeightTons = v; }
    public BodyType getBodyType() { return bodyType; }
    public void setBodyType(BodyType bodyType) { this.bodyType = bodyType; }
    public BigDecimal getLengthFeet() { return lengthFeet; }
    public void setLengthFeet(BigDecimal v) { this.lengthFeet = v; }
    public BigDecimal getWidthFeet() { return widthFeet; }
    public void setWidthFeet(BigDecimal v) { this.widthFeet = v; }
    public BigDecimal getHeightFeet() { return heightFeet; }
    public void setHeightFeet(BigDecimal v) { this.heightFeet = v; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus s) { this.availabilityStatus = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
