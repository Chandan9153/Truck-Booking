# TruckConnect Module 2 — Truck Categories and Truck Registration

**For:** A beginner learning step by step, one module at a time
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8
**Builds on:** Module 1 — Authentication and Security (roles, JWT, Spring Security)
**This module only:** Truck categories and truck registration with admin verification. No booking, pricing, payment, or tracking yet — those come in later modules.

## How to use this guide

Read it in order, part by part. Do not skip ahead to the code.

1. Read Parts 1 to 4 first so you understand *why* the code is shaped this way.
2. Then add one file at a time in VS Code, in the order given in Part 5.
3. Start the app, seed the categories, and work through the test checklist in Part 12.
4. Module 1 must already be working — you need a login token before you can test anything here.

> **The one rule of this module:** A truck is **not** bookable just because an owner added it. It becomes bookable only after an admin verifies it. Unverified trucks cannot be booked — later modules must check this, and this module already stores the status that makes that check possible.

---

## Part 1 — What this module does, and why it comes second

### What this module does

Module 1 answered *who are you?* Module 2 answers *what trucks exist on TruckConnect, and can we trust them?*

This module gives TruckConnect:

- A `truck_categories` table — categories stored as data in MySQL, created and edited by an admin, not written as fixed values inside Java code
- A `trucks` table — every truck an owner registers, with its real, verified carrying capacity
- A `truck_documents` table — the Registration Certificate (RC), insurance, fitness certificate, and permit for each truck, with expiry dates
- Endpoints for an owner to add, see, update, and delete **only their own** trucks
- Endpoints for an admin to list pending trucks and **approve or reject** them
- A status system, so a truck is clearly pending, approved, rejected, available, or unavailable

### Why it comes second

A booking (a later module) needs a real truck to book. If owners could add any truck and it went live instantly, a customer could book a truck that does not exist, has expired insurance, or cannot legally carry the load. So registration and verification come before booking — always in that order.

### What this module does not do

- No booking or load posting — Module 3 onwards
- No price calculation — pricing uses the verified payload from this module, later
- No file upload to cloud storage — this module stores a document *reference* (file name / URL string). Real file upload can be added later without changing the tables
- No driver assignment — a truck is registered here; who drives a trip is a later decision

---
## Part 2 — The big picture in simple words

Think of TruckConnect like a truck stand with a checking officer.

1. **The officer keeps a category board.** The board lists the kinds of trucks allowed at the stand: 12 tyre, 14 tyre, and so on. The board can be changed — that is the admin managing categories in the database, not a developer changing code.
2. **An owner brings a truck.** The owner fills a form: registration number, category, body type, dimensions, and documents.
3. **The truck waits in a side lane.** Its status is `PENDING_VERIFICATION`. Customers cannot book it. It is saved, but not trusted yet.
4. **The officer checks the papers.** The admin compares the RC, insurance, fitness certificate, and permit with what the owner typed — especially the real carrying capacity.
5. **Decision.** Approved: status becomes `APPROVED` and the truck can be made `AVAILABLE` for booking. Rejected: status becomes `REJECTED`, with a reason the owner can read and fix.

### The flow, step by step

```text
OWNER logs in (Module 1 JWT)
  -> POST /api/trucks            creates truck, status = PENDING_VERIFICATION
  -> POST /api/trucks/{id}/documents   adds RC / insurance / fitness / permit

ADMIN logs in (Module 1 JWT)
  -> GET  /api/admin/trucks/pending    sees waiting trucks
  -> PUT  /api/admin/trucks/{id}/approve   status = APPROVED
     or PUT /api/admin/trucks/{id}/reject  status = REJECTED + reason

Later modules (booking):
  -> only trucks where verificationStatus = APPROVED
     AND availabilityStatus = AVAILABLE can be booked
```

### Who can do what

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | :---: | :---: | :---: | :---: | :---: |
| See active categories | Yes | Yes | Yes | Yes | Yes |
| Create / edit / delete a category | No | No | No | No | Yes |
| Add a truck | No | Yes, own only | No | No* | No |
| See my own trucks | — | Yes | — | — | Yes, all |
| Add documents to a truck | No | Yes, own only | No | No | No |
| Approve / reject a truck | No | No | No | No | Yes |
| Book a truck | Later module | — | — | — | — |

\* In TruckConnect a transporter who owns vehicles registers as OWNER for those vehicles. Keeping "add truck" to OWNER only makes the ownership check simple and safe in this module.

---
## Part 3 — Categories are data, not hardcoded

### The target marketplace categories

These four are the starting categories for TruckConnect. They are **seed data** — rows inserted into the database once — not constants buried in code.

| Category name | Tyre count | Target payload |
| --- | ---: | ---: |
| 12 Tyre Truck | 12 | 20 tons |
| 14 Tyre Truck | 14 | 25 tons |
| 18 Tyre Truck | 18 | 30 tons |
| 22 Tyre Truck | 22 | 36 tons |

An admin can later add a category (for example a smaller truck), change a name, or deactivate one — all through the category endpoints in Part 7, with no code change and no restart. Deactivated categories stay in the database (old trucks still point to them) but cannot be chosen for a new truck.

### The important correction: tyre count alone does not decide payload

Two trucks can both have 12 tyres and still be legally allowed to carry different loads. What decides the real limit is on the vehicle's papers:

- **Gross Vehicle Weight (GVW)** — the maximum total weight of truck + load, printed on the RC
- **Unladen (empty) weight** — the weight of the truck with no load
- **Axle configuration and the permitted load on the RC**

So this module stores **two different numbers**, and you must not mix them up:

| Number | Where it comes from | What it means |
| --- | --- | --- |
| Category target payload | The category row, e.g. 20 tons | A guide for searching and grouping |
| Verified payload (`permittedPayloadTons`) | That one truck's RC: GVW − unladen weight, checked by admin | The legal limit for *this* truck |

**Rule for every later module:** eligibility for a load is decided by the truck's **verified payload**, never by tyre count alone. A 12-tyre truck whose RC allows only 18 tons must not be given a 20-ton load, even though its category target says 20.

When an owner registers a truck, they enter the payload claimed from the RC. When the admin approves the truck, the admin is confirming that claim against the RC document. If the claim is wrong, the admin rejects it and says why.

---
## Part 4 — The truck, its body types, statuses, and documents

### Truck fields

| Field | Example | Meaning |
| --- | --- | --- |
| `registrationNumber` | `KA05MN1234` | Number plate, unique — no two trucks can share it |
| `categoryId` | `1` | Which category row this truck belongs to |
| `tyreCount` | `12` | Actual tyres on this truck; should match its category |
| `permittedPayloadTons` | `19.5` | Verified legal payload from the RC (see Part 3) |
| `grossVehicleWeightTons` | `28.0` | GVW from the RC |
| `bodyType` | `OPEN` | Shape of the loading body (list below) |
| `lengthFeet` / `widthFeet` / `heightFeet` | `24` / `8` / `8` | Loading-space dimensions, used later for load matching |
| `ownerId` | from the JWT | The logged-in owner — never typed by the user |
| `verificationStatus` | `PENDING_VERIFICATION` | Admin decision (list below) |
| `availabilityStatus` | `UNAVAILABLE` | Can it take work right now? (list below) |
| `rejectionReason` | `Insurance expired` | Filled only when an admin rejects |

### Body types — the only five allowed values

| Value in code | Meaning |
| --- | --- |
| `OPEN` | Open body, loaded from the top / sides, usually covered with tarpaulin |
| `CLOSED_CONTAINER` | Closed box container, goods protected from weather |
| `FLATBED` | Flat platform, for machines, pipes, and oversized items |
| `TRAILER` | Trailer / articulated vehicle for long or heavy loads |
| `REFRIGERATED` | Cold-storage body for food and medicine |

These five are an **enum** in Java (Part 6). Unlike categories, body types are a small fixed list that changes how loads are matched, so a fixed enum is the right tool here. That difference is deliberate: categories change with the business, so they are data; body types are part of the logic, so they are code.

### Two statuses — do not merge them

A beginner mistake is to use one status for everything. This module uses two, because they answer two different questions:

- **Verification status — can we trust this truck?** `PENDING_VERIFICATION` → `APPROVED` or `REJECTED`. Only an admin changes this.
- **Availability status — can it take work now?** `AVAILABLE` or `UNAVAILABLE`. The owner changes this, and only on an approved truck.

A new truck starts as `PENDING_VERIFICATION` + `UNAVAILABLE`. A booking check in a later module is therefore simple: `APPROVED` **and** `AVAILABLE`, otherwise stop.

### Documents

Each truck should have four documents, each stored as its own row so each can have its own number and expiry date:

| Document type | Why it is checked |
| --- | --- |
| `RC` | Proves the truck exists, its owner, GVW, and permitted load |
| `INSURANCE` | The truck must be insured while it carries someone's goods |
| `FITNESS_CERTIFICATE` | Proves the truck was certified roadworthy |
| `PERMIT` | Permission to carry goods, including across state borders |

`expiryDate` matters: an already-expired insurance or fitness certificate is a reason for the admin to reject the truck.

---
## Part 5 — VS Code folder, tables, and setup

### Where the new files go

You are adding to the **same project** you built in Module 1. Nothing from Module 1 is replaced — in VS Code your folder should now look like this (only new Module 2 files are marked):

```text
src/main/java/com/truckconnect/
  TruckConnectApplication.java          (Module 1)
  config/        SecurityConfig.java    (Module 1 — one small change, Part 11)
  security/      JwtService, JwtAuthFilter ...   (Module 1, unchanged)
  user/          User, Role, UserRepository ...  (Module 1, unchanged)
  truck/                                       <-- NEW package for Module 2
    BodyType.java
    VerificationStatus.java
    AvailabilityStatus.java
    DocumentType.java
    TruckCategory.java
    TruckCategoryRepository.java
    TruckCategoryService.java
    TruckCategoryController.java
    Truck.java
    TruckDocument.java
    TruckRepository.java
    TruckDocumentRepository.java
    TruckService.java
    TruckController.java
    AdminTruckController.java
    TruckCategorySeeder.java
    dto/
      TruckCategoryRequest.java
      TruckCategoryResponse.java
      TruckRequest.java
      TruckResponse.java
      TruckDocumentRequest.java
      TruckDocumentResponse.java
      RejectTruckRequest.java
```

Keeping all truck code in one `truck` package is a beginner-friendly choice: when something breaks, you know which folder to open.

### No new dependencies

Module 1's `pom.xml` already has everything this module needs: Spring Web, Spring Data JPA, MySQL driver, Validation, and Spring Security. Do not add anything.

### The three tables Hibernate will create

With `spring.jpa.hibernate.ddl-auto=update` (as in Module 1), Hibernate creates these tables from the entities in Part 6 and 7:

| Table | One row is | Key columns |
| --- | --- | --- |
| `truck_categories` | One category | `id`, `name` (unique), `tyre_count`, `target_payload_tons`, `active` |
| `trucks` | One truck | `id`, `registration_number` (unique), `category_id`, `owner_id`, `permitted_payload_tons`, statuses |
| `truck_documents` | One document of one truck | `id`, `truck_id`, `document_type`, `document_number`, `file_url`, `expiry_date` |

> As Module 1 explained: `ddl-auto=update` is fine while learning. For a real production launch, use Flyway migrations instead, so table changes are reviewed, not automatic.

---
## Part 6 — The enums: four small fixed lists

**Simple meaning:** An enum is a list where only the given values are allowed. If a request sends `bodyType: "FLYING"`, validation fails instead of saving nonsense into the database. Create all four files in the `truck` package.

### File 1 of 23 — `BodyType.java`

```java
package com.truckconnect.truck;

public enum BodyType {
    OPEN,
    CLOSED_CONTAINER,
    FLATBED,
    TRAILER,
    REFRIGERATED
}
```

### File 2 of 23 — `VerificationStatus.java`

```java
package com.truckconnect.truck;

public enum VerificationStatus {
    PENDING_VERIFICATION,
    APPROVED,
    REJECTED
}
```

### File 3 of 23 — `AvailabilityStatus.java`

```java
package com.truckconnect.truck;

public enum AvailabilityStatus {
    AVAILABLE,
    UNAVAILABLE
}
```

### File 4 of 23 — `DocumentType.java`

```java
package com.truckconnect.truck;

public enum DocumentType {
    RC,
    INSURANCE,
    FITNESS_CERTIFICATE,
    PERMIT
}
```

---
## Part 7 — Truck categories: entity, repository, service, controller

### File 5 of 23 — `TruckCategory.java` (the entity)

**Simple meaning:** This class *is* the `truck_categories` table. Each object is one row. Note `targetPayloadTons` is only the category guide — Part 3 explained why a single truck's verified payload is separate.

```java
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
```

### File 6 of 23 — `TruckCategoryRepository.java`

**Simple meaning:** Spring Data JPA writes the SQL for you. The method names *are* the queries — `existsByName` asks "is this name already used?", `findByActiveTrue` returns only active categories.

```java
package com.truckconnect.truck;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TruckCategoryRepository extends JpaRepository<TruckCategory, Long> {
    boolean existsByName(String name);
    List<TruckCategory> findByActiveTrueOrderByTyreCountAsc();
}
```

---
### Files 7 and 8 of 23 — DTOs for categories: `dto/TruckCategoryRequest.java` and `dto/TruckCategoryResponse.java`

**Simple meaning:** A Data Transfer Object (DTO) is the shape of the JSON that comes in and goes out. We never send the entity directly, so the database shape and the API shape can stay separate. The annotations are the validation rules — they run automatically because the controller uses `@Valid`.

```java
package com.truckconnect.truck.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TruckCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 60, message = "Name must be 60 characters or less")
        String name,

        @Min(value = 4, message = "Tyre count must be at least 4")
        @Max(value = 30, message = "Tyre count must be 30 or less")
        int tyreCount,

        @NotNull(message = "Target payload is required")
        @DecimalMin(value = "1.0", message = "Target payload must be at least 1 ton")
        @DecimalMax(value = "60.0", message = "Target payload must be 60 tons or less")
        BigDecimal targetPayloadTons,

        @Size(max = 255, message = "Description must be 255 characters or less")
        String description
) { }
```

```java
package com.truckconnect.truck.dto;

import com.truckconnect.truck.TruckCategory;
import java.math.BigDecimal;

public record TruckCategoryResponse(
        Long id, String name, int tyreCount,
        BigDecimal targetPayloadTons, String description, boolean active
) {
    public static TruckCategoryResponse from(TruckCategory c) {
        return new TruckCategoryResponse(c.getId(), c.getName(), c.getTyreCount(),
                c.getTargetPayloadTons(), c.getDescription(), c.isActive());
    }
}
```

### File 9 of 23 — `TruckCategoryService.java`

**Simple meaning:** The service holds the *rules*: no duplicate names, deactivating instead of deleting, and "not found" errors with a clear message. The controller (next file) only receives requests and passes them here. Module 1's `GlobalExceptionHandler` will turn an `IllegalArgumentException` into a clean error response — if your Module 1 handler does not cover it, add the handler shown in Part 11, which returns HTTP 400 for `IllegalArgumentException`.

```java
package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TruckCategoryService {

    private final TruckCategoryRepository repository;

    public TruckCategoryService(TruckCategoryRepository repository) {
        this.repository = repository;
    }

    public List<TruckCategoryResponse> listActive() {
        return repository.findByActiveTrueOrderByTyreCountAsc()
                .stream().map(TruckCategoryResponse::from).toList();
    }

    public List<TruckCategoryResponse> listAll() {
        return repository.findAll().stream()
                .map(TruckCategoryResponse::from).toList();
    }

    @Transactional
    public TruckCategoryResponse create(TruckCategoryRequest req) {
        if (repository.existsByName(req.name())) {
            throw new IllegalArgumentException("Category name already exists: " + req.name());
        }
        TruckCategory saved = repository.save(new TruckCategory(
                req.name(), req.tyreCount(), req.targetPayloadTons(), req.description()));
        return TruckCategoryResponse.from(saved);
    }
```
```java
    @Transactional
    public TruckCategoryResponse update(Long id, TruckCategoryRequest req) {
        TruckCategory category = getOrThrow(id);
        if (!category.getName().equals(req.name()) && repository.existsByName(req.name())) {
            throw new IllegalArgumentException("Category name already exists: " + req.name());
        }
        category.setName(req.name());
        category.setTyreCount(req.tyreCount());
        category.setTargetPayloadTons(req.targetPayloadTons());
        category.setDescription(req.description());
        return TruckCategoryResponse.from(repository.save(category));
    }

    @Transactional
    public void deactivate(Long id) {
        TruckCategory category = getOrThrow(id);
        category.setActive(false);
        repository.save(category);
    }

    // Used by TruckService: a new truck may only use an existing, active category.
    public TruckCategory getActiveOrThrow(Long id) {
        TruckCategory category = getOrThrow(id);
        if (!category.isActive()) {
            throw new IllegalArgumentException("Category is not active: " + id);
        }
        return category;
    }

    private TruckCategory getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Category not found: " + id));
    }
}
```

### File 10 of 23 — `TruckCategoryController.java`

**Simple meaning:** These are the doors. Anyone logged in can *see* active categories (a customer needs them to search later). Only `ADMIN` can create, edit, or deactivate — `@PreAuthorize` is the lock on the door, checked before the method runs. For `@PreAuthorize` to work, Module 1's `SecurityConfig` needs `@EnableMethodSecurity` — Part 11 shows that one-line change.

```java
package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/truck-categories")
public class TruckCategoryController {

    private final TruckCategoryService service;

    public TruckCategoryController(TruckCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<TruckCategoryResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TruckCategoryResponse> listAll() {
        return service.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TruckCategoryResponse> create(
            @Valid @RequestBody TruckCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TruckCategoryResponse update(@PathVariable Long id,
            @Valid @RequestBody TruckCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
```

---
### File 11 of 23 — Seeding the four target categories: `TruckCategorySeeder.java`

**Simple meaning:** This runs once when the app starts. If the category table is empty, it inserts the four target categories from Part 3. If rows already exist, it does nothing — so restarting the app never creates duplicates. This is how "configurable data" gets its starting values.

```java
package com.truckconnect.truck;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class TruckCategorySeeder implements CommandLineRunner {

    private final TruckCategoryRepository repository;

    public TruckCategorySeeder(TruckCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(List.of(
            new TruckCategory("12 Tyre Truck", 12, new BigDecimal("20.00"),
                    "Target category - verified payload is per vehicle, from its RC"),
            new TruckCategory("14 Tyre Truck", 14, new BigDecimal("25.00"),
                    "Target category - verified payload is per vehicle, from its RC"),
            new TruckCategory("18 Tyre Truck", 18, new BigDecimal("30.00"),
                    "Target category - verified payload is per vehicle, from its RC"),
            new TruckCategory("22 Tyre Truck", 22, new BigDecimal("36.00"),
                    "Target category - verified payload is per vehicle, from its RC")
        ));
    }
}
```

Try it: start the app, then in MySQL run `SELECT * FROM truck_categories;` — you should see exactly four rows. Start the app again: still four rows.

---
## Part 8 — The truck and its documents: entities and repositories

### File 12 of 23 — `Truck.java` (the entity)

**Simple meaning:** This class *is* the `trucks` table. Three things to notice while you type it:

- `owner` is a link to Module 1's `User` — a truck always belongs to a real, logged-in user.
- `category` is a link to `TruckCategory` — a truck always belongs to a real category row.
- The two statuses have safe starting values in the field declarations, so a new truck can never accidentally start as approved or available.

```java
package com.truckconnect.truck;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @Column(nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.UNAVAILABLE;

    @Column(length = 500)
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "truck", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TruckDocument> documents = new ArrayList<>();

    public Truck() { }
```
```java
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
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus s) { this.verificationStatus = s; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus s) { this.availabilityStatus = s; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<TruckDocument> getDocuments() { return documents; }
}
```

> **Check your Module 1 `User` class:** this code uses `com.truckconnect.user.User` and `user.getId()`, `user.getEmail()`, `user.getFullName()`. If your Module 1 package or getter names differ slightly, change only the import and getter names here to match your Module 1 code — do not create a second User class.

### File 13 of 23 — `TruckDocument.java` (the entity)

**Simple meaning:** One row is one paper document of one truck. `fileUrl` is a reference to where the file is kept (for now, a string the owner provides); checking the *dates and numbers* is the admin's job at approval time.

```java
package com.truckconnect.truck;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "truck_documents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"truck_id", "document_type"}))
public class TruckDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(nullable = false, length = 60)
    private String documentNumber;

    @Column(nullable = false, length = 500)
    private String fileUrl;

    private LocalDate expiryDate;

    public TruckDocument() { }

    public TruckDocument(Truck truck, DocumentType documentType, String documentNumber,
                         String fileUrl, LocalDate expiryDate) {
        this.truck = truck;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.fileUrl = fileUrl;
        this.expiryDate = expiryDate;
    }

    public Long getId() { return id; }
    public Truck getTruck() { return truck; }
    public DocumentType getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getFileUrl() { return fileUrl; }
    public LocalDate getExpiryDate() { return expiryDate; }
}
```

The `uniqueConstraints` line means one truck can have only one RC row, one insurance row, and so on — adding the same type again is rejected instead of creating a confusing duplicate.
### Files 14 and 15 of 23 — `TruckRepository.java` and `TruckDocumentRepository.java`

**Simple meaning:** Again, method names are the queries. Notice `findByOwnerId` — every "my trucks" query is filtered by owner, so one owner can never list another owner's trucks. And `findByVerificationStatusAndAvailabilityStatus` is exactly the query a later booking module will use to find bookable trucks.

```java
package com.truckconnect.truck;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TruckRepository extends JpaRepository<Truck, Long> {
    boolean existsByRegistrationNumber(String registrationNumber);
    List<Truck> findByOwnerId(Long ownerId);
    Optional<Truck> findByIdAndOwnerId(Long id, Long ownerId);
    List<Truck> findByVerificationStatus(VerificationStatus status);
    List<Truck> findByVerificationStatusAndAvailabilityStatus(
            VerificationStatus verificationStatus, AvailabilityStatus availabilityStatus);
}
```

```java
package com.truckconnect.truck;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TruckDocumentRepository extends JpaRepository<TruckDocument, Long> {
    List<TruckDocument> findByTruckId(Long truckId);
    boolean existsByTruckIdAndDocumentType(Long truckId, DocumentType documentType);
}
```

---
## Part 9 — Truck DTOs: what comes in, what goes out

### File 16 of 23 — `dto/TruckRequest.java` — what an owner sends to add or update a truck

Notice what is **not** in this request: no `ownerId`, no statuses. The owner comes from the JWT, and the statuses are decided by the system and the admin — never by the person adding the truck.

```java
package com.truckconnect.truck.dto;

import com.truckconnect.truck.BodyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TruckRequest(
        @NotBlank(message = "Registration number is required")
        @Pattern(regexp = "^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{4}$",
                 message = "Registration number format looks wrong, e.g. KA05MN1234")
        String registrationNumber,

        @NotNull(message = "Category is required")
        Long categoryId,

        @Min(value = 4, message = "Tyre count must be at least 4")
        @Max(value = 30, message = "Tyre count must be 30 or less")
        int tyreCount,

        @NotNull(message = "Permitted payload is required")
        @DecimalMin(value = "0.5", message = "Permitted payload must be at least 0.5 tons")
        @DecimalMax(value = "60.0", message = "Permitted payload must be 60 tons or less")
        BigDecimal permittedPayloadTons,

        @NotNull(message = "Gross vehicle weight is required")
        @DecimalMin(value = "1.0", message = "GVW must be at least 1 ton")
        @DecimalMax(value = "100.0", message = "GVW must be 100 tons or less")
        BigDecimal grossVehicleWeightTons,

        @NotNull(message = "Body type is required")
        BodyType bodyType,

        @NotNull @DecimalMin(value = "1.0", message = "Length must be at least 1 foot")
        BigDecimal lengthFeet,

        @NotNull @DecimalMin(value = "1.0", message = "Width must be at least 1 foot")
        BigDecimal widthFeet,

        @NotNull @DecimalMin(value = "1.0", message = "Height must be at least 1 foot")
        BigDecimal heightFeet
) { }
```

The service adds one rule validation cannot express on a single field: **permitted payload must be smaller than GVW** (payload is only part of the total weight — the truck itself weighs something too). If `permittedPayloadTons >= grossVehicleWeightTons`, the request is rejected.
### File 17 of 23 — `dto/TruckResponse.java` — what the API sends back

```java
package com.truckconnect.truck.dto;

import com.truckconnect.truck.Truck;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TruckResponse(
        Long id, String registrationNumber, Long categoryId, String categoryName,
        int tyreCount, BigDecimal permittedPayloadTons, BigDecimal grossVehicleWeightTons,
        String bodyType, BigDecimal lengthFeet, BigDecimal widthFeet, BigDecimal heightFeet,
        Long ownerId, String ownerEmail,
        String verificationStatus, String availabilityStatus, String rejectionReason,
        LocalDateTime createdAt, List<TruckDocumentResponse> documents
) {
    public static TruckResponse from(Truck t) {
        return new TruckResponse(
            t.getId(), t.getRegistrationNumber(),
            t.getCategory().getId(), t.getCategory().getName(),
            t.getTyreCount(), t.getPermittedPayloadTons(), t.getGrossVehicleWeightTons(),
            t.getBodyType().name(), t.getLengthFeet(), t.getWidthFeet(), t.getHeightFeet(),
            t.getOwner().getId(), t.getOwner().getEmail(),
            t.getVerificationStatus().name(), t.getAvailabilityStatus().name(),
            t.getRejectionReason(), t.getCreatedAt(),
            t.getDocuments().stream().map(TruckDocumentResponse::from).toList());
    }
}
```

### Files 18 and 19 of 23 — `dto/TruckDocumentRequest.java` and `dto/TruckDocumentResponse.java`

```java
package com.truckconnect.truck.dto;

import com.truckconnect.truck.DocumentType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record TruckDocumentRequest(
        @NotNull(message = "Document type is required")
        DocumentType documentType,

        @NotBlank(message = "Document number is required")
        @Size(max = 60) String documentNumber,

        @NotBlank(message = "File URL / reference is required")
        @Size(max = 500) String fileUrl,

        @FutureOrPresent(message = "Document is already expired")
        LocalDate expiryDate
) { }
```

```java
package com.truckconnect.truck.dto;

import com.truckconnect.truck.TruckDocument;
import java.time.LocalDate;

public record TruckDocumentResponse(
        Long id, String documentType, String documentNumber,
        String fileUrl, LocalDate expiryDate
) {
    public static TruckDocumentResponse from(TruckDocument d) {
        return new TruckDocumentResponse(d.getId(), d.getDocumentType().name(),
                d.getDocumentNumber(), d.getFileUrl(), d.getExpiryDate());
    }
}
```

### File 20 of 23 — `dto/RejectTruckRequest.java` — an admin must give a reason

```java
package com.truckconnect.truck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectTruckRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 500) String reason
) { }
```

---
## Part 10 — Truck service and controllers: where the rules live

### File 21 of 23 — `TruckService.java`

**Simple meaning:** This is the heart of the module. Read the checks in `createTruck` slowly — every line is one safety rule from Parts 2 to 4:

- The owner is taken from the login, never from the request body.
- The registration number is cleaned (spaces removed, upper case) and must be unique.
- The category must exist and be active.
- Payload must be less than GVW.
- A new truck is forced to `PENDING_VERIFICATION` + `UNAVAILABLE`, whatever the owner hoped for.
- Editing a truck that was approved sends it **back to pending** — otherwise an owner could get a truck approved, then silently change its details.

```java
package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TruckService {

    private final TruckRepository truckRepository;
    private final TruckDocumentRepository documentRepository;
    private final TruckCategoryService categoryService;
    private final UserRepository userRepository;

    public TruckService(TruckRepository truckRepository,
                        TruckDocumentRepository documentRepository,
                        TruckCategoryService categoryService,
                        UserRepository userRepository) {
        this.truckRepository = truckRepository;
        this.documentRepository = documentRepository;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    // The logged-in user, found from the email inside the JWT (Module 1).
    private User currentUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() ->
                new IllegalArgumentException("Logged-in user not found: " + email));
    }

    @Transactional
    public TruckResponse createTruck(String ownerEmail, TruckRequest req) {
        User owner = currentUser(ownerEmail);
        String regNo = req.registrationNumber().replaceAll("\\s+", "").toUpperCase();
        if (truckRepository.existsByRegistrationNumber(regNo)) {
            throw new IllegalArgumentException("Registration number already exists: " + regNo);
        }
        if (req.permittedPayloadTons().compareTo(req.grossVehicleWeightTons()) >= 0) {
            throw new IllegalArgumentException(
                "Permitted payload must be less than gross vehicle weight");
        }
        Truck truck = new Truck();
        applyRequest(truck, req, regNo);
        truck.setOwner(owner);
        truck.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        truck.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        truck.setRejectionReason(null);
        return TruckResponse.from(truckRepository.save(truck));
    }
```
```java
    private void applyRequest(Truck truck, TruckRequest req, String regNo) {
        truck.setRegistrationNumber(regNo);
        truck.setCategory(categoryService.getActiveOrThrow(req.categoryId()));
        truck.setTyreCount(req.tyreCount());
        truck.setPermittedPayloadTons(req.permittedPayloadTons());
        truck.setGrossVehicleWeightTons(req.grossVehicleWeightTons());
        truck.setBodyType(req.bodyType());
        truck.setLengthFeet(req.lengthFeet());
        truck.setWidthFeet(req.widthFeet());
        truck.setHeightFeet(req.heightFeet());
    }

    public List<TruckResponse> myTrucks(String ownerEmail) {
        User owner = currentUser(ownerEmail);
        return truckRepository.findByOwnerId(owner.getId())
                .stream().map(TruckResponse::from).toList();
    }

    // Ownership check used by every owner action: wrong owner = "not found".
    private Truck myTruckOrThrow(Long truckId, String ownerEmail) {
        User owner = currentUser(ownerEmail);
        return truckRepository.findByIdAndOwnerId(truckId, owner.getId()).orElseThrow(() ->
                new IllegalArgumentException("Truck not found for this owner: " + truckId));
    }

    @Transactional
    public TruckResponse updateMyTruck(Long truckId, String ownerEmail, TruckRequest req) {
        Truck truck = myTruckOrThrow(truckId, ownerEmail);
        String regNo = req.registrationNumber().replaceAll("\\s+", "").toUpperCase();
        if (!truck.getRegistrationNumber().equals(regNo)
                && truckRepository.existsByRegistrationNumber(regNo)) {
            throw new IllegalArgumentException("Registration number already exists: " + regNo);
        }
        if (req.permittedPayloadTons().compareTo(req.grossVehicleWeightTons()) >= 0) {
            throw new IllegalArgumentException(
                "Permitted payload must be less than gross vehicle weight");
        }
        applyRequest(truck, req, regNo);
        // Changed details must be checked again before the truck can be booked.
        truck.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        truck.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        truck.setRejectionReason(null);
        return TruckResponse.from(truckRepository.save(truck));
    }

    @Transactional
    public void deleteMyTruck(Long truckId, String ownerEmail) {
        truckRepository.delete(myTruckOrThrow(truckId, ownerEmail));
    }

    @Transactional
    public TruckDocumentResponse addDocument(Long truckId, String ownerEmail,
                                             TruckDocumentRequest req) {
        Truck truck = myTruckOrThrow(truckId, ownerEmail);
        if (documentRepository.existsByTruckIdAndDocumentType(truckId, req.documentType())) {
            throw new IllegalArgumentException(
                "This truck already has a document of type: " + req.documentType());
        }
        TruckDocument saved = documentRepository.save(new TruckDocument(
                truck, req.documentType(), req.documentNumber(),
                req.fileUrl(), req.expiryDate()));
        return TruckDocumentResponse.from(saved);
    }
```
```java
    @Transactional
    public TruckResponse setAvailability(Long truckId, String ownerEmail, boolean available) {
        Truck truck = myTruckOrThrow(truckId, ownerEmail);
        if (available && truck.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new IllegalArgumentException(
                "Only an APPROVED truck can be made available. Current status: "
                + truck.getVerificationStatus());
        }
        truck.setAvailabilityStatus(available
                ? AvailabilityStatus.AVAILABLE : AvailabilityStatus.UNAVAILABLE);
        return TruckResponse.from(truckRepository.save(truck));
    }

    // ---- Admin actions ----

    public List<TruckResponse> pendingTrucks() {
        return truckRepository.findByVerificationStatus(VerificationStatus.PENDING_VERIFICATION)
                .stream().map(TruckResponse::from).toList();
    }

    public List<TruckResponse> allTrucks() {
        return truckRepository.findAll().stream().map(TruckResponse::from).toList();
    }

    private Truck truckOrThrow(Long truckId) {
        return truckRepository.findById(truckId).orElseThrow(() ->
                new IllegalArgumentException("Truck not found: " + truckId));
    }

    @Transactional
    public TruckResponse approve(Long truckId) {
        Truck truck = truckOrThrow(truckId);
        if (truck.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException(
                "Only a pending truck can be approved. Current status: "
                + truck.getVerificationStatus());
        }
        truck.setVerificationStatus(VerificationStatus.APPROVED);
        truck.setRejectionReason(null);
        return TruckResponse.from(truckRepository.save(truck));
    }

    @Transactional
    public TruckResponse reject(Long truckId, String reason) {
        Truck truck = truckOrThrow(truckId);
        if (truck.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException(
                "Only a pending truck can be rejected. Current status: "
                + truck.getVerificationStatus());
        }
        truck.setVerificationStatus(VerificationStatus.REJECTED);
        truck.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        truck.setRejectionReason(reason);
        return TruckResponse.from(truckRepository.save(truck));
    }

    // The single check later modules must use before creating a booking.
    public boolean isBookable(Long truckId) {
        Truck truck = truckOrThrow(truckId);
        return truck.getVerificationStatus() == VerificationStatus.APPROVED
                && truck.getAvailabilityStatus() == AvailabilityStatus.AVAILABLE;
    }
}
```

> **Check your Module 1 `UserRepository`:** this code uses `findByEmail(...)` returning an `Optional<User>`, exactly as in Module 1. If your method name differs, match it here — do not duplicate the repository.
### File 22 of 23 — `TruckController.java` (owner doors)

**Simple meaning:** Every method here has `@PreAuthorize("hasRole('OWNER')")` — a customer, driver, or even an admin token is turned away. `principal.getName()` is the email inside the JWT that Module 1 put there; the service then loads that owner and only ever touches *their* trucks.

```java
package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@PreAuthorize("hasRole('OWNER')")
public class TruckController {

    private final TruckService service;

    public TruckController(TruckService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TruckResponse> create(@Valid @RequestBody TruckRequest request,
                                                Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTruck(principal.getName(), request));
    }

    @GetMapping("/my")
    public List<TruckResponse> myTrucks(Principal principal) {
        return service.myTrucks(principal.getName());
    }

    @PutMapping("/{id}")
    public TruckResponse update(@PathVariable Long id,
                                @Valid @RequestBody TruckRequest request,
                                Principal principal) {
        return service.updateMyTruck(id, principal.getName(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        service.deleteMyTruck(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<TruckDocumentResponse> addDocument(
            @PathVariable Long id, @Valid @RequestBody TruckDocumentRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addDocument(id, principal.getName(), request));
    }

    @PutMapping("/{id}/availability")
    public TruckResponse setAvailability(@PathVariable Long id,
                                         @RequestParam boolean available,
                                         Principal principal) {
        return service.setAvailability(id, principal.getName(), available);
    }
}
```
### File 23 of 23 — `AdminTruckController.java` (admin doors)

**Simple meaning:** A separate controller under `/api/admin/...` makes the admin area easy to spot and easy to lock: the whole class requires `ADMIN`. Owners cannot call these doors at all — they get HTTP 403 Forbidden.

```java
package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/trucks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTruckController {

    private final TruckService service;

    public AdminTruckController(TruckService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<TruckResponse> pending() {
        return service.pendingTrucks();
    }

    @GetMapping
    public List<TruckResponse> all() {
        return service.allTrucks();
    }

    @PutMapping("/{id}/approve")
    public TruckResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PutMapping("/{id}/reject")
    public TruckResponse reject(@PathVariable Long id,
                                @Valid @RequestBody RejectTruckRequest request) {
        return service.reject(id, request.reason());
    }
}
```

### The remaining files — the DTO files

The remaining files are the seven DTO records, already given in full where they are explained: the two category DTOs in Part 7, and `TruckRequest`, `TruckResponse`, `TruckDocumentRequest`, `TruckDocumentResponse`, and `RejectTruckRequest` in Part 9. Counting entities, enums, repositories, services, controllers, DTOs, and the seeder, you have added **23 new files** in this module — no Module 1 file is deleted.

---
## Part 11 — The one Module 1 change, and error handling

### Turn on method security in `SecurityConfig.java`

`@PreAuthorize` only works if method security is switched on. In Module 1's `SecurityConfig`, add the import and the annotation on the class — nothing else in that file changes:

```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// ... on the existing class:
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // everything from Module 1 stays exactly as it was
}
```

Also confirm Module 1's URL rules still require authentication for `/api/**` (except the public auth endpoints). Do **not** add `/api/trucks/**` to any `permitAll` list — trucks are never public.

How the three layers protect you together:

1. **No token / expired token** — Module 1's JWT filter rejects the request (HTTP 401).
2. **Wrong role** — `@PreAuthorize` rejects the request (HTTP 403). A CUSTOMER token cannot add a truck; an OWNER token cannot approve one.
3. **Right role, wrong owner** — the service's `findByIdAndOwnerId` check finds nothing, so an owner cannot see, edit, or delete another owner's truck even by guessing its ID.

### Error handling

This module throws `IllegalArgumentException` for rule failures (duplicate registration number, wrong owner, not pending, payload not less than GVW). If Module 1's `GlobalExceptionHandler` does not already handle it, add this method inside that class:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<Map<String, String>> handleIllegalArgument(
        IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", ex.getMessage()));
}
```

Validation failures (`@Valid`) are already handled by Module 1's handler and return HTTP 400 with the field message, for example "Permitted payload must be at least 0.5 tons".

### Endpoint summary

| Method and path | Who | What it does |
| --- | --- | --- |
| `GET /api/truck-categories` | Any logged-in user | Active categories |
| `GET /api/truck-categories/all` | ADMIN | All categories, including inactive |
| `POST /api/truck-categories` | ADMIN | Create a category |
| `PUT /api/truck-categories/{id}` | ADMIN | Update a category |
| `DELETE /api/truck-categories/{id}` | ADMIN | Deactivate a category |
| `POST /api/trucks` | OWNER | Add my truck (starts pending) |
| `GET /api/trucks/my` | OWNER | My trucks only |
| `PUT /api/trucks/{id}` | OWNER | Update my truck (returns to pending) |
| `DELETE /api/trucks/{id}` | OWNER | Delete my truck |
| `POST /api/trucks/{id}/documents` | OWNER | Add a document to my truck |
| `PUT /api/trucks/{id}/availability?available=true` | OWNER | Make my approved truck available / unavailable |
| `GET /api/admin/trucks/pending` | ADMIN | Trucks waiting for verification |
| `GET /api/admin/trucks` | ADMIN | All trucks |
| `PUT /api/admin/trucks/{id}/approve` | ADMIN | Approve a pending truck |
| `PUT /api/admin/trucks/{id}/reject` | ADMIN | Reject a pending truck, with a reason |

---
## Part 12 — Test checklist

You need three Module 1 accounts before you start: one `OWNER`, one `CUSTOMER`, and one `ADMIN` (promote the admin directly in MySQL, as Module 1 explained — admin cannot be chosen at registration). Log in with each and save the three tokens as `$OWNER`, `$CUSTOMER`, and `$ADMIN`. Replace `localhost:8080` if your app runs elsewhere.

### A sample truck, used in the tests

```json
{
  "registrationNumber": "KA05MN1234",
  "categoryId": 1,
  "tyreCount": 12,
  "permittedPayloadTons": 19.50,
  "grossVehicleWeightTons": 28.00,
  "bodyType": "OPEN",
  "lengthFeet": 24, "widthFeet": 8, "heightFeet": 8
}
```

Note the payload: category 1's target is 20 tons, but *this* truck's RC-based payload is 19.5 — exactly the Part 3 rule in action.

### The checklist — tick each one only when you see it work

- [ ] **1. Categories are seeded.** `GET /api/truck-categories` with any token returns 4 categories: 12 tyre / 20 tons, 14 / 25, 18 / 30, 22 / 36. Restart the app and check again — still 4, no duplicates.
- [ ] **2. Only admin manages categories.** `POST /api/truck-categories` with `$ADMIN` returns 201. The same request with `$OWNER` returns 403. With no token it returns 401.
- [ ] **3. Duplicate category is rejected.** Repeating the admin create with the same name returns 400 with "already exists".
- [ ] **4. Owner adds a truck.** `POST /api/trucks` with `$OWNER` and the sample JSON returns 201, with `verificationStatus: PENDING_VERIFICATION` and `availabilityStatus: UNAVAILABLE`.
- [ ] **5. Customer cannot add a truck.** The same `POST /api/trucks` with `$CUSTOMER` returns 403.
- [ ] **6. Duplicate registration number is rejected.** The owner posts the same `registrationNumber` again — 400 with "already exists".
- [ ] **7. Bad payload is rejected.** Change `permittedPayloadTons` to `30.00` (more than the GVW of 28) — 400 with "must be less than gross vehicle weight".
- [ ] **8. Documents can be added, once per type.** `POST /api/trucks/{id}/documents` with `$OWNER` and `{"documentType":"RC","documentNumber":"RC-KA05MN1234","fileUrl":"rc-ka05mn1234.pdf","expiryDate":"2030-01-31"}` returns 201. Adding a second `RC` for the same truck returns 400. An already-expired `expiryDate` also returns 400.
- [ ] **9. Owners see only their own trucks.** Register a second OWNER, add a different truck, then call `GET /api/trucks/my` with the first `$OWNER` — only the first truck appears. Updating the second owner's truck with the first owner's token returns 400 (not found for this owner).
- [ ] **10. Unverified truck cannot be made available.** `PUT /api/trucks/{id}/availability?available=true` on the pending truck returns 400 — this is the "unverified trucks cannot be booked" rule, enforced already in this module.
- [ ] **11. Owner cannot approve their own truck.** `PUT /api/admin/trucks/{id}/approve` with `$OWNER` returns 403.
- [ ] **12. Admin sees and approves the pending truck.** `GET /api/admin/trucks/pending` with `$ADMIN` lists the truck. `PUT /api/admin/trucks/{id}/approve` returns `APPROVED`. Approving it a second time returns 400 (only pending trucks can be approved).
- [ ] **13. Approved truck can become available.** Repeat step 10 now — it succeeds, and the truck is `APPROVED` + `AVAILABLE`. In MySQL, check: `SELECT registration_number, verification_status, availability_status FROM trucks;`
- [ ] **14. Rejection needs a reason and blocks availability.** Add a second truck, then reject it as admin with `{"reason":"Insurance document expired"}` — status is `REJECTED`, the reason is saved, and making it available returns 400.
- [ ] **15. Editing an approved truck sends it back to pending.** `PUT /api/trucks/{id}` on the approved truck returns `PENDING_VERIFICATION` + `UNAVAILABLE`. It must be approved again before it can be booked.

If all 15 pass, Module 2 is working. Keep the app and database as they are — the next module builds on these tables.

---
## Part 13 — Common beginner mistakes

- **Hardcoding the categories in Java.** If you write `if (tyreCount == 12) payload = 20` in code, stop — that is exactly what this module avoids. Categories live in the `truck_categories` table; code only reads them.
- **Using tyre count as the payload.** A 12-tyre truck is not automatically a 20-ton truck. Always use `permittedPayloadTons`, verified from the RC / GVW, for any load decision.
- **Trusting an `ownerId` sent in the request body.** Never accept it. The owner is the user inside the JWT — `principal.getName()` — and nothing else.
- **One status instead of two.** Verification (trust) and availability (free now) change at different times and by different people. Keep both fields.
- **Forgetting `@EnableMethodSecurity`.** Without it, `@PreAuthorize` is silently ignored and role checks do not run. Test 2, 5, and 11 in the checklist exist to catch exactly this.
- **Forgetting to re-verify after an edit.** If an update leaves a truck `APPROVED`, an owner can change the payload after approval and nobody checks it. That is why `updateMyTruck` resets the status to pending.
- **Sending an enum value in the wrong case.** JSON values must match exactly: `OPEN`, not `open`; `RC`, not `rc`. A wrong value returns HTTP 400.
- **Registration number with spaces or small letters in the database.** The service cleans it to upper case with no spaces before saving and before the duplicate check, so `ka 05 mn 1234` and `KA05MN1234` are correctly treated as the same truck.

## What you have finished, and what comes next

You now have configurable truck categories, owner truck registration with documents and dimensions, and an admin verification gate. No truck can reach a booking in an approved-and-available state without an admin checking it first, and every load decision later can use the truck's verified payload instead of a guess from its tyre count.

**Stop here.** Do not build Module 3 yet. Work through the Part 12 checklist first, and tell Bodhi your result — which tests passed, and the exact error for any that failed. The next module will be built part by part in the same style, on top of these tables.
