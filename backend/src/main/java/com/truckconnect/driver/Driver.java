package com.truckconnect.driver;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "licence_number", nullable = false, length = 30)
    private String licenceNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // --- Later step: driver login ---
    // When a driver gets their own login, add ONE field here and nothing else:
    //   @ManyToOne(fetch = FetchType.LAZY)
    //   @JoinColumn(name = "user_id")
    //   private User user;   // the driver's own User account (null until then)
    // The owner still manages the record; the linked account only lets the
    // driver see and update their own trips. Do not add it in this module.

    public Driver() { }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLicenceNumber() { return licenceNumber; }
    public void setLicenceNumber(String licenceNumber) { this.licenceNumber = licenceNumber; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
