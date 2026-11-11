# Truck Booking — Module 5: Driver & Trip Management

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1–4 keep working; rename the package later only if you want to)
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8, Spring Security + JWT, no new dependencies
**Builds on:** Module 1 — Authentication & Security (roles, JWT) · Module 2 — Truck Categories & Registration (slim version) · Module 3 — Search, Matching & Pricing (quotes, rates) · Module 4 — Booking (assumed contract, see Part 0)
**This module only:** Drivers (as people records managed by the owner), assigning a driver to a confirmed booking, the trip itself, trip status updates in order, delivery proof, and the trip timeline. No payment, no GPS live tracking, no driver login yet — Module 6 is payments, and driver self-service login is a later step (Part 1 explains why).

## How to use this guide

1. Read Part 0 first — it states exactly what this module assumes about Module 4's `Booking`. Module 4 is being built in parallel, so if its real file names a field differently, change only the lines flagged there. Do not wait for Module 4 to be finished before reading the rest.
2. Read Parts 1 to 4 so you understand *why* the code is shaped this way.
3. Add one file at a time in VS Code, in the order given in Part 5.
4. Add the one hook method to Module 4's `BookingService` (Part 9) — it is a small edit to an existing file, not a new file.
5. Start the app and work through the test checklist in Part 11.
6. Modules 1–4 must already be working — you need a login token, a truck, a quote and a CONFIRMED booking before you can test anything here.

> **The one rule of this module:** a trip is a **record of what actually happened**, not a plan. Every status change is written down the moment it happens, with who did it and when — in the `trip_events` table. If it is not in the timeline, it did not happen, as far as the customer is concerned.

---

## Part 0 — What this module assumes from Module 4 (read this first)

Module 4 is being built at the same time as this module, so this guide does not guess silently — it states its assumptions in one place. If Module 4's real file differs, change only the flagged lines; nothing else in this module needs to change.

| Assumption | What this module uses | If Module 4 differs, change this |
| --- | --- | --- |
| Package | `com.truckconnect.booking` | The `import com.truckconnect.booking.Booking;` lines in `Trip.java`, `TripService.java` and `TripController.java` |
| Entity | `Booking` with `getId()`, `getCustomer()`, `getTruck()`, `getOwner()`, `getStatus()`, `setStatus(...)`, `getQuoteId()`, `getTotalFare()` | Only the getter/setter calls in `TripService.java` (assign, status update, cancel) |
| Status enum | `BookingStatus` in the same package, with `PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, COMPLETED, REJECTED, CANCELLED, PAYMENT_PENDING, PAYMENT_FAILED, DISPUTED` | The `BookingStatus.X` names in `TripService.java` and in the `BookingService` hook in Part 9 |
| Snapshot addresses | `booking.getPickup()` and `booking.getDestination()` return the pickup and delivery addresses as text (copied from the quote at booking time, exactly as Module 3's `Quote` stores `pickup` / `destination`) | **Flagged lines:** in `TripService.assignDriver`, the two lines `trip.setPickup(booking.getPickup());` and `trip.setDestination(booking.getDestination());` — if Module 4 named them `getPickupAddress()` / `getDeliveryAddress()`, change only those two lines |
| Truck becomes ON_TRIP | When a booking is confirmed, Module 4 sets the truck's `availabilityStatus` to `ON_TRIP` | Nothing in this module — but the test in Part 11, test 15, checks that the truck returns to `AVAILABLE` when the trip completes, which is this module's job |
| Status rules | `BookingService` enforces allowed booking transitions | This module only ever asks `BookingService` to move a booking along the trip path (`DRIVER_ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED → COMPLETED`, or `CANCELLED`) — it never sets a booking status directly |

### Two more names to check (from Modules 1–3)

- **User:** `com.truckconnect.user.User`, with `getId()`, `getEmail()`, `getFullName()`, `getRole()` (Module 1). If your Module 1 `@AuthenticationPrincipal` parameter is a `UserDetails` instead of the `User` entity, use whatever your Module 1 controllers already use, and change only that parameter — exactly as Module 3 noted.
- **Truck payload field:** Module 2 as first delivered named the truck's own tonnage `permittedPayloadTons` (`getPermittedPayloadTons()`), while Module 3 and the slim registration use `payloadTons` (`getPayloadTons()`). **This module does not touch the payload at all** — it only reads the truck's id, registration number, owner and availability status — so either name works here. If you are following Module 3, use `payloadTons` everywhere; the flag above is only so you are not surprised when you re-read Module 2.

### The three final decisions, and what they mean for this module

- **Both B2B + B2C:** a customer may be a business or an individual. Nothing in this module changes for that — a trip belongs to a booking, and a booking belongs to a customer, whoever they are.
- **Instant booking + owner call/chat:** the customer books instantly at the quoted price (Module 4). The owner then picks a driver — that is this module. The owner call/chat is how the customer and owner talk if something goes wrong on the road; it is not a feature in this module.
- **All India launch:** pickup and destination are free-text addresses anywhere in India. This module never restricts them to one state or corridor.

---

## Part 1 — What this module does, and why it comes fifth

### What this module does

Module 1 answered *who are you?* Module 2 answered *what trucks exist?* Module 3 answered *what will it cost?* Module 4 answered *is the booking confirmed?* Module 5 answers *who is driving it, and where is it now?*

This module gives Truck Booking:

- A `drivers` table — the people an owner employs to drive their trucks. Name, phone, licence number as plain text the owner types, and an active/inactive switch.
- A `trips` table — one trip per booking. It records the booking, the truck, the driver, the status, the pickup and delivery addresses copied from the booking, and the time each step happened.
- A `trip_events` table — the audit history of every trip. Every status change writes one row here, in the same transaction, so the timeline can never disagree with the trip.
- Driver assignment — the owner picks one of their own active drivers for a CONFIRMED booking. One driver cannot have two active trips at once.
- Trip status updates in a fixed order — `ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED → COMPLETED` — with the booking status moving in step.
- Delivery proof — a short note and/or a link, typed or pasted, that must exist before a trip can be marked DELIVERED.
- Reassignment — the owner can swap the driver before pickup, and only before pickup.

### The honest part: drivers have no login in this MVP

Read this carefully, because it is the most important design decision in the module.

**A driver in this module is a person record, not an account.** The owner creates the record ("Ramesh, 98765 43210, licence KA05 20210012345"). Ramesh does **not** log in to Truck Booking in this MVP. There is no driver app screen, no driver token, and no endpoint that a driver calls.

That means, in this MVP, **only the trip's owner (or ADMIN) updates the trip status** — in practice the owner does it after talking to the driver on the phone, which is how small transport businesses actually work today. The `DRIVER` role from Module 1 already exists and can log in, but it has no trip endpoints yet. Driver self-service — a driver logging in, seeing only their own trips, and updating status themselves — is a later step, after payments. It is marked as such in the code and in Part 10, not silently left out.

Why do it this way instead of building driver login now? Because driver login needs its own decisions (does a driver register themselves, or does the owner create their login? what happens when a driver leaves?), and answering those wrong would block the trip flow that customers and owners need first. The `Driver` record is deliberately shaped so that linking it to a `User` account later is one new field (`user_id`), not a redesign — see Part 6.

### Why it comes fifth

A trip only makes sense once a booking is confirmed. Before Module 4 there was no booking to attach a trip to, and no truck that was `ON_TRIP`. Assigning a driver to a booking that might still be rejected would create trips for jobs that never happen. So Module 5 waits until Module 4 has said "yes, this job is real".

### What this module does not do

- No driver login or driver self-service status updates (see above — later step)
- No licence document upload and no licence verification paperwork — the licence number is a text field the owner types, consistent with the slim registration in Module 2. There is nothing to verify against, and no admin paper-check.
- No file upload for delivery proof — proof is a text note and/or a URL string the owner types or pastes. Cloud storage for photos is a later step.
- No GPS live tracking — the customer sees the trip timeline (events with times), not a moving dot on a map. Live location comes in Module 7.
- No payment — payments are Module 6, after the trip is complete.
- No driver rating or review — that belongs with completed trips, after payments.

---

## Part 2 — The big picture in simple words

Think of a trip like a parcel's journey slip — the paper slip stuck to a parcel that gets stamped at every stop.

1. **The owner hires drivers.** Before any trip, the owner writes down who drives for them: name, phone, licence number. That list is the `drivers` table. A driver who leaves is not deleted — they are switched off (`active = false`), so old trips still show who drove them.
2. **A booking is confirmed.** A customer booked a truck (Module 4). The truck is `ON_TRIP` and the booking is `CONFIRMED`. Nothing has moved yet.
3. **The owner assigns a driver.** The owner picks one driver from their own list. In that one moment, three things happen together, or none of them do: a `trips` row is created, the booking becomes `DRIVER_ASSIGNED`, and the first `trip_events` row ("ASSIGNED") is written. "Together or none" is a database transaction — see Part 8.
4. **The driver moves the load, step by step.** Pickup → on the road → delivered → completed. Each step stamps the slip: the trip status changes, the booking status changes with it, the time is recorded, and a new timeline event is written.
5. **Delivery needs proof.** Before the "DELIVERED" stamp, someone must write down proof — a note ("Delivered to warehouse gate, signed by Mr. Sharma") or a link to a photo. No proof, no DELIVERED stamp. That is the rule that stops a trip being marked delivered when the goods are still on the truck.
6. **The trip ends.** When the trip is `COMPLETED`, the truck becomes `AVAILABLE` again, ready for the next booking. The timeline stays forever, so the customer can always see what happened and when.

### The flow, step by step

```text
OWNER logs in (Module 1 JWT)
  -> POST /api/drivers
       { name, phone, licenceNumber }        driver record created (no login)
  -> GET  /api/drivers/mine                  owner sees only their own drivers

OWNER assigns a driver to a CONFIRMED booking
  -> POST /api/trips/assign
       { bookingId, driverId }
  -> server, in ONE transaction:
       (1) checks booking is CONFIRMED and belongs to this owner
       (2) checks driver belongs to this owner and is active
       (3) checks driver has no other active trip (else 409)
       (4) creates Trip (status ASSIGNED, startedAt = now)
       (5) booking CONFIRMED -> DRIVER_ASSIGNED (via BookingService hook)
       (6) writes TripEvent ASSIGNED
  -> returns the trip

OWNER updates the trip as the job progresses
  -> PATCH /api/trips/{id}/status   { status: "PICKED_UP", note: "..." }
  -> PATCH /api/trips/{id}/status   { status: "IN_TRANSIT", note: "..." }
  -> PATCH /api/trips/{id}/status   { status: "DELIVERED",
                                      proofNote: "Signed by Mr. Sharma",
                                      proofUrl: "https://..." }
  -> PATCH /api/trips/{id}/status   { status: "COMPLETED", note: "..." }
  -> each step: Trip status + Booking status move together,
     timestamp recorded, TripEvent written, truck -> AVAILABLE on COMPLETED

CUSTOMER checks progress (no GPS dot — a timeline instead)
  -> GET /api/trips/booking/{bookingId}       my trip for my booking
  -> GET /api/trips/{id}/timeline            every event, in order, with times
```

### Who can do what

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | :---: | :---: | :---: | :---: | --- |
| Add / edit / deactivate own drivers | No | Yes | No | Yes | No (views all) |
| Assign a driver to a booking | No | Yes, own bookings only | No | Yes, own bookings only | No |
| Update a trip's status | No | Yes, own trips only | No (later step) | Yes, own trips only | Yes, all |
| Reassign a driver (before pickup) | No | Yes, own trips only | No | Yes, own trips only | No |
| Cancel a trip | No | Yes, own trips only | No | Yes, own trips only | Yes, all |
| View a trip / timeline | Yes, own bookings only | Yes, own trips only | No (later step) | Yes, own trips only | Yes, all |

> **Why TRANSPORTER is included:** Module 1's role table gives transporters driver management ("Manage own drivers: TRANSPORTER — Yes"). A transporter is an owner of many trucks, so they manage drivers and trips exactly like an OWNER. If you want only OWNER, delete `TRANSPORTER` from the `@PreAuthorize` lines in Part 9 — nothing else changes.

### The status maps (the heart of the module)

**Trip status** — one trip, one status at a time:

```text
ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED -> COMPLETED
   |           |             |
   +-----------+-------------+--> CANCELLED   (only before DELIVERED)
```

**Booking status moves in step** (Module 4's enum, same names on purpose):

```text
Trip ASSIGNED   -> Booking DRIVER_ASSIGNED
Trip PICKED_UP  -> Booking PICKED_UP
Trip IN_TRANSIT -> Booking IN_TRANSIT
Trip DELIVERED  -> Booking DELIVERED
Trip COMPLETED  -> Booking COMPLETED
Trip CANCELLED  -> Booking CANCELLED
```

The two statuses always agree. If they ever disagree, one of the two updates was missed — and because both happen in the same transaction (Part 8), they cannot disagree.

---

## Part 3 — The three tables, in plain words

### `drivers` — who drives for this owner

One row is one person the owner employs. `licenceNumber` is just text the owner types — for example `KA05 20210012345`. There is no upload, no photo of the licence, and no check against any government database. The number is there so that, if there is ever a question about a trip, the owner and the customer can see which licence the driver was driving under. `active` is the on/off switch: a driver who stops working for the owner is set to `active = false` and can never be assigned a new trip — but their old trips keep showing their name, because history is never rewritten.

### `trips` — one trip per booking

One row is one job being carried out. It points at the booking (the job), the truck (the vehicle) and the driver (the person). The pickup and delivery addresses are **copied** from the booking when the trip is created — copied, not just linked — so that if anyone ever edits the booking later, the trip still shows the addresses the driver actually drove to. The four time fields are the trip's own diary: `startedAt` (when the driver was assigned), `pickedUpAt`, `deliveredAt`, `completedAt`. A time is filled in only when its step happens; the rest stay empty. `proofNote` and `proofUrl` are the delivery proof (Part 4).

### `trip_events` — the timeline, and the audit trail

One row is one thing that happened on a trip: assigned, picked up, on the road, delivered, completed, or a free-text note. Each row records the trip, the event type, a short note, **who** caused it (their role — `OWNER` or `ADMIN` in this MVP), and **when** (`createdAt`, set by the server, never by the client). Module 7 (tracking) will read this table to show the customer a timeline. Because every status change writes its event in the same transaction as the status change itself, the timeline can never be missing a step that the trip claims happened — and that is the whole point of the table.

---

## Part 4 — Delivery proof: why a note or a link, and why it is required

Delivery is the step where money and arguments live. A customer pays for goods that arrived; a driver says they arrived. If the trip can be marked DELIVERED with one click and no evidence, there is nothing to check when the customer says "nothing arrived".

So this module requires, before `DELIVERED` can be set, **at least one** of:

- `proofNote` — a short typed note, for example "Delivered to warehouse gate 2, signed by Mr. Sharma, 14 boxes counted". This is the minimum: it costs nothing and works on any phone.
- `proofUrl` — a link the owner pastes, for example a link to a photo stored in Google Drive or WhatsApp. It is a plain string the owner types or pastes; the server does not fetch it, check it, or store a file.

Either one is enough, and both together is best. The proof can be supplied in the same status-update request that sets `DELIVERED`, or it can have been saved on the trip earlier (for example the owner adds the note at `IN_TRANSIT`) — the check looks at the trip's proof fields **after** applying the request, so both ways work.

What proof is **not** in this MVP: uploading a photo file to the server. A file upload needs cloud storage (where do the files live? who can see them? how big may they be?), which is a later step with its own module. A URL string keeps the MVP at ₹0 and needs no new dependency. When photo upload arrives, `proofUrl` is exactly the field that will hold the uploaded photo's link — the shape of the data does not change, only where the link comes from.


---

## Part 5 — Files to create (20 files + 2 small edits)

All under `src/main/java/com/truckconnect/`, continuing Modules 1–4. No new dependency is needed.

| # | File | What it is |
| --- | --- | --- |
| 1 | `driver/Driver.java` | Entity — one driver record |
| 2 | `driver/DriverRepository.java` | Finds an owner's drivers |
| 3 | `driver/dto/DriverRequest.java` | What the owner sends to add / edit a driver |
| 4 | `driver/dto/DriverResponse.java` | What the owner gets back |
| 5 | `driver/DriverService.java` | Add, edit, deactivate, list — with ownership checks |
| 6 | `driver/DriverController.java` | The owner-facing driver endpoints |
| 7 | `trip/TripStatus.java` | Enum — the six trip statuses |
| 8 | `trip/Trip.java` | Entity — one trip per booking |
| 9 | `trip/TripRepository.java` | Finds trips by booking, driver, owner |
| 10 | `trip/TripEventType.java` | Enum — the six event types |
| 11 | `trip/TripEvent.java` | Entity — one timeline row |
| 12 | `trip/TripEventRepository.java` | Reads a trip's timeline in order |
| 13 | `trip/dto/AssignDriverRequest.java` | What the owner sends to assign a driver |
| 14 | `trip/dto/ReassignDriverRequest.java` | What the owner sends to swap a driver |
| 15 | `trip/dto/TripStatusUpdateRequest.java` | Status + note + proof for one step |
| 16 | `trip/dto/TripResponse.java` | What a trip looks like to the outside |
| 17 | `trip/dto/TripEventResponse.java` | One timeline row, as the customer sees it |
| 18 | `trip/TripService.java` | Assign, status flow, reassign, cancel — the brain |
| 19 | `trip/TripController.java` | The owner/admin/customer trip endpoints |
| 20 | `common/ConflictException.java` | A 409 exception (one driver, one active trip) |
| — | **Edit:** `booking/BookingService.java` (Module 4) | Add the hook methods in Part 9 — do not create a second BookingService |
| — | **Edit:** `common/GlobalExceptionHandler.java` (Module 1) | Add the two handlers in Part 9 — a 409 for conflicts, a 409 for illegal state |

> **Create them in this order:** enums → entities → repositories → DTOs → services → controllers → the two edits. A service cannot compile before its repository exists, and a controller cannot compile before its service exists.

---

## Part 6 — The entities and repositories

### File 7 of 20 — `trip/TripStatus.java`

**Simple meaning:** An `enum` is a fixed list. A trip's status can only be one of these six values — a spelling mistake like `PICKEDUP` is impossible because Java will not compile it.

```java
package com.truckconnect.trip;

public enum TripStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
```

### File 10 of 20 — `trip/TripEventType.java`

**Simple meaning:** The same idea for timeline events. Note there is no `CANCELLED` value here on purpose: a cancellation is written as a `NOTE` event whose text says the trip was cancelled and why (see `TripService.cancel` in Part 8). That keeps the event list to exactly the six types this module promised.

```java
package com.truckconnect.trip;

public enum TripEventType {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    NOTE
}
```

### File 1 of 20 — `driver/Driver.java`

**Simple meaning:** This class *is* the `drivers` table. Each field becomes a column. `licenceNumber` is a plain text column — no file, no upload, no verification. `active` defaults to `true`: a new driver can be assigned immediately.

```java
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
```

### File 2 of 20 — `driver/DriverRepository.java`

```java
package com.truckconnect.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    /** All drivers of one owner, newest first. */
    List<Driver> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** Only the owner's ACTIVE drivers — the list assignment offers. */
    List<Driver> findByOwnerIdAndActiveTrue(Long ownerId);

    /** One driver, but only if they belong to this owner.
        Returns empty for another owner's driver — that is the ownership
        check, done by the database query itself. */
    Optional<Driver> findByIdAndOwnerId(Long id, Long ownerId);
}
```

**Simple meaning:** Method names are the queries. `findByIdAndOwnerId` is the important one — it makes "wrong owner" impossible to get wrong in the service, because the database itself refuses to return another owner's driver.

### File 8 of 20 — `trip/Trip.java`

**Simple meaning:** One row is one job being carried out. `@OneToOne` on `booking` with a unique column means one booking can have at most one trip — the database enforces "one trip per booking" even if the service code has a bug. The addresses are copied here as plain text (see Part 3).

```java
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
```

### File 9 of 20 — `trip/TripRepository.java`

```java
package com.truckconnect.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** One trip per booking — the lookup the customer uses. */
    Optional<Trip> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    /** The double-booking check: does this driver already have a trip
        in one of the given statuses? */
    boolean existsByDriverIdAndStatusIn(Long driverId, List<TripStatus> statuses);

    /** All trips whose truck belongs to this owner, newest first.
        (Spring Data reads "Truck_OwnerId" as trip -> truck -> owner -> id.) */
    List<Trip> findByTruck_OwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** All trips for one customer, via their bookings. */
    List<Trip> findByBooking_CustomerIdOrderByCreatedAtDesc(Long customerId);
}
```

### File 11 of 20 — `trip/TripEvent.java`

**Simple meaning:** One row is one stamp on the journey slip. `createdByRole` is the role of the person who caused the event (`OWNER` or `ADMIN` in this MVP) — stored as text, so the timeline stays readable even if that person's account is later deleted. `createdAt` is set by the server, never by the client.

```java
package com.truckconnect.trip;

import com.truckconnect.user.Role;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_events")
public class TripEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private TripEventType eventType;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", nullable = false, length = 20)
    private Role createdByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public TripEvent() { }

    public TripEvent(Trip trip, TripEventType eventType, String note, Role createdByRole) {
        this.trip = trip;
        this.eventType = eventType;
        this.note = note;
        this.createdByRole = createdByRole;
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public TripEventType getEventType() { return eventType; }
    public void setEventType(TripEventType eventType) { this.eventType = eventType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Role getCreatedByRole() { return createdByRole; }
    public void setCreatedByRole(Role createdByRole) { this.createdByRole = createdByRole; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### File 12 of 20 — `trip/TripEventRepository.java`

```java
package com.truckconnect.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    /** A trip's whole timeline, oldest first — the order the customer reads. */
    List<TripEvent> findByTripIdOrderByCreatedAtAsc(Long tripId);
}
```


---

## Part 7 — The DTOs (what goes in and out)

DTOs are records, as in Modules 1 and 3 — a short way to write a class that only carries data. Validation annotations run automatically because the controllers use `@Valid`.

### File 3 of 20 — `driver/dto/DriverRequest.java`

```java
package com.truckconnect.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverRequest(
        @NotBlank(message = "Driver name is required")
        @Size(min = 2, max = 100, message = "Driver name must be 2-100 characters")
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$",
                 message = "Phone must be a 10-digit Indian mobile number")
        String phone,

        @NotBlank(message = "Licence number is required")
        @Size(min = 5, max = 30, message = "Licence number must be 5-30 characters")
        String licenceNumber
) { }
```

The phone rule is the same Indian mobile rule Module 1 uses for users. The licence number is only checked for length — there is no format to validate against, because no verification exists in this MVP (Part 1).

### File 4 of 20 — `driver/dto/DriverResponse.java`

```java
package com.truckconnect.driver.dto;

import com.truckconnect.driver.Driver;

public record DriverResponse(
        Long id, String name, String phone,
        String licenceNumber, boolean active, Long ownerId) {

    public static DriverResponse from(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getPhone(),
                d.getLicenceNumber(), d.isActive(), d.getOwner().getId());
    }
}
```

### File 13 of 20 — `trip/dto/AssignDriverRequest.java`

```java
package com.truckconnect.trip.dto;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(
        @NotNull(message = "bookingId is required") Long bookingId,
        @NotNull(message = "driverId is required") Long driverId
) { }
```

### File 14 of 20 — `trip/dto/ReassignDriverRequest.java`

```java
package com.truckconnect.trip.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignDriverRequest(
        @NotNull(message = "driverId is required") Long driverId
) { }
```

### File 15 of 20 — `trip/dto/TripStatusUpdateRequest.java`

```java
package com.truckconnect.trip.dto;

import com.truckconnect.trip.TripStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripStatusUpdateRequest(
        @NotNull(message = "status is required")
        TripStatus status,

        @Size(max = 500, message = "Note must be at most 500 characters")
        String note,

        /** Delivery proof (Part 4). Either field may be sent with the
            DELIVERED update, or left empty if proof was saved earlier. */
        @Size(max = 500, message = "Proof note must be at most 500 characters")
        String proofNote,

        @Size(max = 500, message = "Proof URL must be at most 500 characters")
        String proofUrl
) { }
```

### File 16 of 20 — `trip/dto/TripResponse.java`

```java
package com.truckconnect.trip.dto;

import com.truckconnect.trip.Trip;
import com.truckconnect.trip.TripStatus;
import java.time.LocalDateTime;

public record TripResponse(
        Long tripId,
        Long bookingId,
        Long truckId,
        String registrationNumber,
        Long driverId,
        String driverName,
        String driverPhone,
        TripStatus status,
        String pickup,
        String destination,
        String proofNote,
        String proofUrl,
        LocalDateTime startedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt) {

    public static TripResponse from(Trip t) {
        return new TripResponse(
                t.getId(),
                t.getBooking().getId(),
                t.getTruck().getId(),
                t.getTruck().getRegistrationNumber(),
                t.getDriver().getId(),
                t.getDriver().getName(),
                t.getDriver().getPhone(),
                t.getStatus(),
                t.getPickup(),
                t.getDestination(),
                t.getProofNote(),
                t.getProofUrl(),
                t.getStartedAt(),
                t.getPickedUpAt(),
                t.getDeliveredAt(),
                t.getCompletedAt(),
                t.getCreatedAt());
    }
}
```

### File 17 of 20 — `trip/dto/TripEventResponse.java`

```java
package com.truckconnect.trip.dto;

import com.truckconnect.trip.TripEvent;
import com.truckconnect.trip.TripEventType;
import com.truckconnect.user.Role;
import java.time.LocalDateTime;

public record TripEventResponse(
        Long id, TripEventType eventType, String note,
        Role createdByRole, LocalDateTime createdAt) {

    public static TripEventResponse from(TripEvent e) {
        return new TripEventResponse(e.getId(), e.getEventType(), e.getNote(),
                e.getCreatedByRole(), e.getCreatedAt());
    }
}
```

---

## Part 8 — The services: the brain of the module

### File 20 of 20 — `common/ConflictException.java`

**Simple meaning:** A small exception that means "this request is fine, but it conflicts with something that already exists" — HTTP 409. It is used for exactly one thing in this module: a driver who already has an active trip. The handler for it is added to Module 1's `GlobalExceptionHandler` in Part 9.

```java
package com.truckconnect.common;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

### File 5 of 20 — `driver/DriverService.java`

**Simple meaning:** All driver rules live here, and every method starts from the logged-in owner — never from an id in the request body. The three ownership tricks are: `findByIdAndOwnerId` (the database refuses another owner's driver), deactivation instead of deletion (history stays), and no way to list another owner's drivers at all.

```java
package com.truckconnect.driver;

import com.truckconnect.driver.dto.DriverRequest;
import com.truckconnect.driver.dto.DriverResponse;
import com.truckconnect.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DriverService {

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public DriverResponse addDriver(User owner, DriverRequest req) {
        Driver driver = new Driver();
        driver.setOwner(owner);
        driver.setName(req.name().trim());
        driver.setPhone(req.phone().trim());
        driver.setLicenceNumber(req.licenceNumber().trim());
        driver.setActive(true);
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateDriver(User owner, Long driverId, DriverRequest req) {
        Driver driver = findOwnDriver(owner, driverId);
        driver.setName(req.name().trim());
        driver.setPhone(req.phone().trim());
        driver.setLicenceNumber(req.licenceNumber().trim());
        return DriverResponse.from(driverRepository.save(driver));
    }

    /** Switch a driver off. They can never be assigned a new trip, but
        their old trips keep showing their name. There is no delete. */
    @Transactional
    public DriverResponse deactivateDriver(User owner, Long driverId) {
        Driver driver = findOwnDriver(owner, driverId);
        driver.setActive(false);
        return DriverResponse.from(driverRepository.save(driver));
    }

    /** Switch a driver back on (for example they return after a break). */
    @Transactional
    public DriverResponse activateDriver(User owner, Long driverId) {
        Driver driver = findOwnDriver(owner, driverId);
        driver.setActive(true);
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> myDrivers(User owner) {
        return driverRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(DriverResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> myActiveDrivers(User owner) {
        return driverRepository.findByOwnerIdAndActiveTrue(owner.getId())
                .stream().map(DriverResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriver(User owner, Long driverId) {
        return DriverResponse.from(findOwnDriver(owner, driverId));
    }

    /** The ownership check, in one place. If the driver does not belong
        to this owner, the repository returns empty — and the caller gets
        "not found", never "this belongs to someone else". Returning 404
        instead of 403 here is deliberate: it does not confirm that the
        driver exists at all. */
    private Driver findOwnDriver(User owner, Long driverId) {
        return driverRepository.findByIdAndOwnerId(driverId, owner.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found: " + driverId));
    }
}
```

### File 18 of 20 — `trip/TripService.java`

**Simple meaning:** This is the brain. Read it method by method — each one does one job, and every method that changes more than one table is `@Transactional`, which means "all of these database writes happen together, or none of them do". If the event write failed after the trip was created, the whole assignment would roll back instead of leaving a trip with no history.

```java
package com.truckconnect.trip;

import com.truckconnect.booking.Booking;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingService;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.common.ConflictException;
import com.truckconnect.driver.Driver;
import com.truckconnect.driver.DriverRepository;
import com.truckconnect.trip.dto.*;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TripService {

    /** A driver with a trip in one of these statuses is busy.
        COMPLETED and CANCELLED are not here — a finished trip frees the driver. */
    private static final List<TripStatus> ACTIVE_STATUSES = List.of(
            TripStatus.ASSIGNED, TripStatus.PICKED_UP, TripStatus.IN_TRANSIT,
            TripStatus.DELIVERED);

    /** The only allowed forward step from each status. There is no entry
        for COMPLETED or CANCELLED — nothing moves forward from there. */
    private static final Map<TripStatus, TripStatus> NEXT_STEP = Map.of(
            TripStatus.ASSIGNED, TripStatus.PICKED_UP,
            TripStatus.PICKED_UP, TripStatus.IN_TRANSIT,
            TripStatus.IN_TRANSIT, TripStatus.DELIVERED,
            TripStatus.DELIVERED, TripStatus.COMPLETED);

    private final TripRepository tripRepository;
    private final TripEventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;

    public TripService(TripRepository tripRepository,
                       TripEventRepository eventRepository,
                       BookingRepository bookingRepository,
                       BookingService bookingService,
                       DriverRepository driverRepository,
                       TruckRepository truckRepository) {
        this.tripRepository = tripRepository;
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.driverRepository = driverRepository;
        this.truckRepository = truckRepository;
    }

    // ---------- Assignment ----------

    @Transactional
    public TripResponse assignDriver(User owner, AssignDriverRequest req) {
        Booking booking = bookingRepository.findById(req.bookingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Booking not found: " + req.bookingId()));

        // Only the booking's owner may assign a driver to it.
        if (!booking.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException(
                    "You can only assign a driver to your own booking");
        }
        // Only a CONFIRMED booking can get a driver. A PENDING booking
        // has no agreed job yet; a REJECTED / CANCELLED one never will.
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only a CONFIRMED booking can be assigned a driver, current: "
                            + booking.getStatus());
        }
        // One trip per booking — the database unique constraint on
        // trips.booking_id is the backstop; this check gives a clear error.
        if (tripRepository.existsByBookingId(booking.getId())) {
            throw new ConflictException(
                    "Booking " + booking.getId() + " already has a trip");
        }

        Driver driver = driverRepository
                .findByIdAndOwnerId(req.driverId(), owner.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found: " + req.driverId()));
        if (!driver.isActive()) {
            throw new IllegalStateException(
                    "Driver " + driver.getName() + " is inactive");
        }
        // One driver, one active trip at a time (409 if busy).
        if (tripRepository.existsByDriverIdAndStatusIn(driver.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException(
                    "Driver " + driver.getName() + " already has an active trip");
        }

        Trip trip = new Trip();
        trip.setBooking(booking);
        trip.setTruck(booking.getTruck());
        trip.setDriver(driver);
        trip.setStatus(TripStatus.ASSIGNED);
        // --- Flagged lines (Part 0): if Module 4 named the address fields
        // differently, change only these two lines. ---
        trip.setPickup(booking.getPickup());
        trip.setDestination(booking.getDestination());
        trip.setStartedAt(LocalDateTime.now());
        trip = tripRepository.save(trip);

        // The BookingService hook (Part 9): CONFIRMED -> DRIVER_ASSIGNED.
        bookingService.markDriverAssigned(booking.getId());

        writeEvent(trip, TripEventType.ASSIGNED,
                "Driver " + driver.getName() + " assigned", owner.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Status updates ----------

    @Transactional
    public TripResponse updateStatus(User actor, Long tripId,
                                     TripStatusUpdateRequest req) {
        Trip trip = findTrip(tripId);
        checkCanManageTrip(actor, trip);

        TripStatus target = req.status();
        if (target == TripStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "A trip cannot go back to ASSIGNED; use reassign instead");
        }
        if (target == TripStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Use POST /api/trips/{id}/cancel to cancel a trip");
        }
        TripStatus expected = NEXT_STEP.get(trip.getStatus());
        if (expected == null || expected != target) {
            throw new IllegalStateException(
                    "Cannot move trip from " + trip.getStatus()
                            + " to " + target
                            + (expected == null
                                ? " — the trip is already finished"
                                : " — the next step is " + expected));
        }

        // Delivery proof (Part 4): DELIVERED needs a note or a link,
        // either sent now or already saved on the trip.
        if (req.proofNote() != null && !req.proofNote().isBlank()) {
            trip.setProofNote(req.proofNote().trim());
        }
        if (req.proofUrl() != null && !req.proofUrl().isBlank()) {
            trip.setProofUrl(req.proofUrl().trim());
        }
        if (target == TripStatus.DELIVERED && !hasProof(trip)) {
            throw new IllegalArgumentException(
                    "Delivery proof is required before DELIVERED: "
                            + "send proofNote or proofUrl");
        }

        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case PICKED_UP -> trip.setPickedUpAt(now);
            case DELIVERED -> trip.setDeliveredAt(now);
            case COMPLETED -> {
                trip.setCompletedAt(now);
                // The job is done: free the truck for the next booking.
                trip.getTruck().setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
                truckRepository.save(trip.getTruck());
            }
            default -> { /* IN_TRANSIT records no extra timestamp */ }
        }
        trip.setStatus(target);
        trip = tripRepository.save(trip);

        // Booking status moves in step (Part 2 map). The hook method in
        // BookingService does the booking half; the trip half is above.
        bookingService.updateBookingStatus(
                trip.getBooking().getId(), BookingStatus.valueOf(target.name()));

        writeEvent(trip, TripEventType.valueOf(target.name()),
                req.note(), actor.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Reassignment ----------

    @Transactional
    public TripResponse reassignDriver(User owner, Long tripId,
                                       ReassignDriverRequest req) {
        Trip trip = findTrip(tripId);
        checkCanManageTrip(owner, trip);
        // Only before pickup: once the load is on the truck, swapping the
        // driver mid-journey is a different (and riskier) operation.
        if (trip.getStatus() != TripStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Driver can only be reassigned before PICKED_UP, current: "
                            + trip.getStatus());
        }
        Driver newDriver = driverRepository
                .findByIdAndOwnerId(req.driverId(), owner.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found: " + req.driverId()));
        if (!newDriver.isActive()) {
            throw new IllegalStateException(
                    "Driver " + newDriver.getName() + " is inactive");
        }
        if (tripRepository.existsByDriverIdAndStatusIn(newDriver.getId(), ACTIVE_STATUSES)) {
            throw new ConflictException(
                    "Driver " + newDriver.getName() + " already has an active trip");
        }
        String oldName = trip.getDriver().getName();
        trip.setDriver(newDriver);
        trip = tripRepository.save(trip);
        writeEvent(trip, TripEventType.NOTE,
                "Driver reassigned from " + oldName + " to " + newDriver.getName(),
                owner.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Cancellation ----------

    @Transactional
    public TripResponse cancelTrip(User actor, Long tripId, String reason) {
        Trip trip = findTrip(tripId);
        checkCanManageTrip(actor, trip);
        if (trip.getStatus() == TripStatus.DELIVERED
                || trip.getStatus() == TripStatus.COMPLETED
                || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel a trip in status " + trip.getStatus());
        }
        trip.setStatus(TripStatus.CANCELLED);
        trip = tripRepository.save(trip);
        bookingService.updateBookingStatus(
                trip.getBooking().getId(), BookingStatus.CANCELLED);
        // Free the truck: the job will not happen.
        trip.getTruck().setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        truckRepository.save(trip.getTruck());
        writeEvent(trip, TripEventType.NOTE,
                "Trip cancelled" + (reason == null || reason.isBlank()
                        ? "" : ": " + reason.trim()),
                actor.getRole());
        return TripResponse.from(trip);
    }

    // ---------- Reads ----------

    @Transactional(readOnly = true)
    public TripResponse getTrip(User actor, Long tripId) {
        Trip trip = findTrip(tripId);
        checkCanViewTrip(actor, trip);
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse getTripForBooking(User actor, Long bookingId) {
        Trip trip = tripRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No trip found for booking: " + bookingId));
        checkCanViewTrip(actor, trip);
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripEventResponse> timeline(User actor, Long tripId) {
        Trip trip = findTrip(tripId);
        checkCanViewTrip(actor, trip);
        return eventRepository.findByTripIdOrderByCreatedAtAsc(tripId)
                .stream().map(TripEventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> myTrips(User owner) {
        return tripRepository.findByTruck_OwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(TripResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> myTripsAsCustomer(User customer) {
        return tripRepository.findByBooking_CustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(TripResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripResponse> allTrips() {
        return tripRepository.findAll().stream().map(TripResponse::from).toList();
    }

    // ---------- Helpers ----------

    private Trip findTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trip not found: " + tripId));
    }

    /** Who may change a trip: its owner (the driver belongs to them and
        the truck belongs to them) or an ADMIN. In this MVP there is no
        driver login, so the DRIVER role is not allowed here — see Part 1. */
    private void checkCanManageTrip(User actor, Trip trip) {
        boolean isOwner = trip.getDriver().getOwner().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You cannot manage this trip");
        }
    }

    /** Who may see a trip: its owner, its customer, or an ADMIN. */
    private void checkCanViewTrip(User actor, Trip trip) {
        boolean isOwner = trip.getDriver().getOwner().getId().equals(actor.getId());
        boolean isCustomer = trip.getBooking().getCustomer().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isOwner && !isCustomer && !isAdmin) {
            throw new AccessDeniedException("You cannot view this trip");
        }
    }

    private boolean hasProof(Trip trip) {
        return (trip.getProofNote() != null && !trip.getProofNote().isBlank())
                || (trip.getProofUrl() != null && !trip.getProofUrl().isBlank());
    }

    /** Every status change writes its event in the SAME transaction as
        the change itself. If the event write failed, the status change
        would roll back too — the timeline can never miss a step. */
    private void writeEvent(Trip trip, TripEventType type, String note, Role role) {
        eventRepository.save(new TripEvent(trip, type, note, role));
    }
}
```

Three things to notice in this code:

1. **`NEXT_STEP` is the transition table.** The rule "one step at a time, in order" is a four-line map, not a pile of `if` statements. Skipping a step is impossible because the map has no entry for a jump — `ASSIGNED → IN_TRANSIT` simply is not in it. If a future step is ever added, it is added here, in one place.
2. **409 vs 400 vs 403, and which exception gives which.** `ConflictException` → 409 (driver busy, trip already exists). `IllegalStateException` → 409 after the Part 9 handler edit (wrong step in the flow). `IllegalArgumentException` → 400 (bad input, missing proof, not found — Module 1's handler already does this). `AccessDeniedException` → 403 (Spring Security's own handling). Do not swap them: a test that expects 409 and gets 500 means the handler edit in Part 9 was missed.
3. **`BookingStatus.valueOf(target.name())` works only because the two enums share names** (`PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `COMPLETED`). That shared naming is deliberate — if Module 4 named a booking status differently, this one line is the place to fix it, with a small `switch` instead of `valueOf`.


---

## Part 9 — The controllers, the BookingService hook, and the error-handler edit

### File 6 of 20 — `driver/DriverController.java`

**Simple meaning:** A controller is just a door. The owner comes from the JWT (Module 1), never from the request body — so an owner can only ever manage their own drivers. `TRANSPORTER` is included alongside `OWNER` for the reason given in Part 2.

```java
package com.truckconnect.driver;

import com.truckconnect.driver.dto.DriverRequest;
import com.truckconnect.driver.dto.DriverResponse;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public ResponseEntity<DriverResponse> add(
            @AuthenticationPrincipal User owner,
            @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverService.addDriver(owner, request));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<DriverResponse>> mine(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(driverService.myDrivers(owner));
    }

    @GetMapping("/mine/active")
    public ResponseEntity<List<DriverResponse>> myActive(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(driverService.myActiveDrivers(owner));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> get(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.getDriver(owner, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResponse> update(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(driverService.updateDriver(owner, id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<DriverResponse> deactivate(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.deactivateDriver(owner, id));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<DriverResponse> activate(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.activateDriver(owner, id));
    }
}
```

### File 19 of 20 — `trip/TripController.java`

**Simple meaning:** Three audiences use this one controller, and the role rules are per-method, not per-controller — because a customer may *view* a trip but never *change* one, and an admin may view everything but never assign a driver. The service still checks ownership for every call: the annotation checks the role, the service checks *whose* trip it is.

```java
package com.truckconnect.trip;

import com.truckconnect.trip.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
    public ResponseEntity<TripResponse> assign(
            @AuthenticationPrincipal User owner,
            @Valid @RequestBody AssignDriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripService.assignDriver(owner, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> updateStatus(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id,
            @Valid @RequestBody TripStatusUpdateRequest request) {
        return ResponseEntity.ok(tripService.updateStatus(actor, id, request));
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
    public ResponseEntity<TripResponse> reassign(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody ReassignDriverRequest request) {
        return ResponseEntity.ok(tripService.reassignDriver(owner, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> cancel(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(tripService.cancelTrip(actor, id, reason));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> get(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTrip(actor, id));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<TripResponse> getForBooking(
            @AuthenticationPrincipal User actor,
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(tripService.getTripForBooking(actor, bookingId));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<List<TripEventResponse>> timeline(
            @AuthenticationPrincipal User actor,
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.timeline(actor, id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
    public ResponseEntity<List<TripResponse>> myTrips(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(tripService.myTrips(owner));
    }

    @GetMapping("/mine/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<TripResponse>> myTripsAsCustomer(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(tripService.myTripsAsCustomer(customer));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TripResponse>> allTrips() {
        return ResponseEntity.ok(tripService.allTrips());
    }
}
```

> **Note on `GET /api/trips/all`:** it is a plain list with no paging, on purpose for the MVP — the same simplification Module 3 used for the rate list. If the trip count grows into the thousands, add paging here before anything else. And note the route order: `/mine` and `/all` are literal paths, so they must not be confused with `/{id}` — Spring matches literal segments before variables, so this works as written.

### The BookingService hook — an edit to Module 4, not a new file

`TripService` never sets a booking's status directly. It calls these two methods on Module 4's `BookingService`. **Add them to the existing `com.truckconnect.booking.BookingService`** — do not create a second class with the same name. If Module 4's real `BookingService` already has a method that changes a booking's status, use that one instead of `updateBookingStatus`, and change only the two call sites in `TripService`.

```java
// ADD these two methods inside Module 4's BookingService.
// They need the BookingRepository that Module 4 already injects.

@Transactional
public Booking markDriverAssigned(Long bookingId) {
    Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Booking not found: " + bookingId));
    if (booking.getStatus() != BookingStatus.CONFIRMED) {
        throw new IllegalStateException(
                "Only a CONFIRMED booking can move to DRIVER_ASSIGNED, current: "
                        + booking.getStatus());
    }
    booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
    return bookingRepository.save(booking);
}

@Transactional
public Booking updateBookingStatus(Long bookingId, BookingStatus newStatus) {
    Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Booking not found: " + bookingId));
    booking.setStatus(newStatus);
    return bookingRepository.save(booking);
}
```

Why the hook is shaped this way: `markDriverAssigned` repeats the CONFIRMED check because it is the one transition where the *starting* status matters most — assigning a driver to a booking that was just cancelled would be a real bug, and this check catches it even if the caller forgot. `updateBookingStatus` is deliberately small and trusting: the trip flow is the only caller, and the trip's own `NEXT_STEP` table is what enforces the order. If Module 4's transition rules are stricter, put the enforcement inside `updateBookingStatus` — it is the single chokepoint for every booking status change this module makes.

### The error-handler edit — add to Module 1's `GlobalExceptionHandler`

Module 1's handler already maps `IllegalArgumentException` → 400 and `BadCredentialsException` → 401. This module needs two more mappings, or the tests that expect 409 will get a 500 instead. Add these two methods inside the existing `GlobalExceptionHandler` class (and the import for `ConflictException`):

```java
@ExceptionHandler(ConflictException.class)
public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
            409, ex.getMessage(), Map.of(), LocalDateTime.now()));
}

@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
    // Wrong step in a flow (e.g. DELIVERED before PICKED_UP, or assigning
    // a driver to a PENDING booking) is a conflict with the current
    // state of the trip/booking — so it is a 409, not a 400.
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
            409, ex.getMessage(), Map.of(), LocalDateTime.now()));
}
```

`AccessDeniedException` needs no handler: Spring Security converts it to 403 automatically. `MethodArgumentNotValidException` (bad DTO input) is already handled by Module 1 as a 400.

---

## Part 10 — Security: who is checked, where, and how

| Check | Where it happens | What happens if it fails |
| --- | --- | --- |
| No token at all | `JwtAuthFilter` (Module 1) leaves the request unauthenticated; `SecurityConfig` blocks `/api/**` | 401 Unauthorized |
| Wrong role for the endpoint (e.g. CUSTOMER calls `POST /api/drivers`) | `@PreAuthorize` on the controller method | 403 Forbidden |
| Right role, wrong owner (e.g. Owner B assigns a driver to Owner A's booking) | `TripService` / `DriverService` ownership check (`findByIdAndOwnerId`, id comparison, `AccessDeniedException`) | 403 Forbidden |
| Right role, wrong owner, driver lookup (e.g. Owner B edits Owner A's driver) | `DriverRepository.findByIdAndOwnerId` returns empty | 404-style 400 "Driver not found" (deliberate — it does not confirm the driver exists; see `DriverService.findOwnDriver`) |
| Customer views someone else's trip | `TripService.checkCanViewTrip` | 403 Forbidden |
| Driver tries to update a trip (MVP) | `@PreAuthorize` on `PATCH /api/trips/{id}/status` does not include `DRIVER` | 403 Forbidden — driver self-service is a later step (Part 1) |

Three rules that never change, carried over from Module 1:

1. **The logged-in user always comes from the JWT, never from the request body.** There is no `ownerId` field in any request DTO in this module. If there were, a user could send someone else's id and manage their drivers.
2. **Ownership is checked in the service, not only in the controller.** The annotation checks the *role*; the service checks *whose* record it is. Either check alone is not enough.
3. **The licence number and the proof URL are typed text, not verified documents.** Never treat them as proof of identity or of delivery by themselves — the proof note is a human claim, and the timeline is the record that it was claimed, by whom, and when.

---

## Part 11 — Test checklist (do these in order)

You need: the app running, one ADMIN token, one CUSTOMER token, two OWNER tokens (Owner A and Owner B), one CONFIRMED booking belonging to Owner A (create it through Modules 3–4 first: search, quote, book, confirm), and the truck from that booking (it should be `ON_TRIP`).

| # | Test | Expected result |
| --- | --- | --- |
| 1 | `POST /api/drivers` as Owner A: `{ "name": "Ramesh Kumar", "phone": "9876543210", "licenceNumber": "KA05 20210012345" }`. | 201 Created. Driver is `active: true`, `ownerId` is Owner A's id. |
| 2 | Same request as CUSTOMER. | 403 Forbidden. |
| 3 | Same request with no token at all. | 401 Unauthorized. |
| 4 | `PUT /api/drivers/{id}` as Owner B, using Owner A's driver id. | 400 "Driver not found" — Owner B cannot edit (or even see) Owner A's driver. |
| 5 | `POST /api/trips/assign` as Owner A: `{ "bookingId": <confirmed booking>, "driverId": <Ramesh> }`. | 201 Created. Trip status `ASSIGNED`, `startedAt` filled, pickup/destination copied from the booking. Booking is now `DRIVER_ASSIGNED`. `GET /api/trips/{id}/timeline` shows exactly one event: `ASSIGNED`. |
| 6 | Repeat test 5 with a *second* driver for the same booking. | 409 Conflict — one trip per booking. |
| 7 | Create a second CONFIRMED booking for Owner A and assign Ramesh to it while the first trip is still active. | 409 Conflict — one driver cannot have two active trips. |
| 8 | Assign a driver to a PENDING booking (create one, do not confirm it). | 409 Conflict — only CONFIRMED bookings get a driver. |
| 9 | `PATCH /api/trips/{id}/status` as Owner A: `{ "status": "IN_TRANSIT" }` straight from `ASSIGNED` (skipping `PICKED_UP`). | 409 Conflict — the next step is `PICKED_UP`. Trip status unchanged. |
| 10 | Now go in order: `PICKED_UP` (note "Loaded 20 tons rice"), then `IN_TRANSIT`. | 200 both times. Booking status matches the trip at each step (`PICKED_UP`, then `IN_TRANSIT`). Timeline now has 3 events, oldest first. |
| 11 | `PATCH /api/trips/{id}/status`: `{ "status": "DELIVERED" }` with no proof fields. | 400 "Delivery proof is required" — trip stays `IN_TRANSIT`. |
| 12 | Same request with `{ "status": "DELIVERED", "proofNote": "Delivered to warehouse gate 2, signed by Mr. Sharma" }`. | 200. Trip `DELIVERED`, `deliveredAt` filled, `proofNote` saved. Booking is `DELIVERED`. Timeline has 4 events. |
| 13 | `POST /api/trips/{id}/reassign` with a different active driver — after `PICKED_UP`. | 409 Conflict — reassignment is only allowed before pickup. (To test the success path: assign a driver to a fresh CONFIRMED booking, reassign before pickup, expect 200 and a `NOTE` event "Driver reassigned from … to …".) |
| 14 | Finish the first trip: `PATCH …/status { "status": "COMPLETED" }`. | 200. Trip `COMPLETED`, `completedAt` filled, booking `COMPLETED`. **Check the truck: `availabilityStatus` is `AVAILABLE` again.** Timeline has 5 events. Now assign Ramesh to a new CONFIRMED booking — it succeeds, because the old trip is finished. |
| 15 | `GET /api/trips/{id}/timeline` as the CUSTOMER who made the booking. | 200 — all events, in order, with `createdByRole` and times. Same request as a different customer: 403. |
| 16 | `PATCH /api/trips/{id}/status` as the DRIVER role. | 403 Forbidden — drivers have no trip endpoints in this MVP (Part 1). |
| 17 | `POST /api/trips/{id}/cancel` on a fresh ASSIGNED trip, then check the booking and the truck. | 200, trip `CANCELLED`, booking `CANCELLED`, truck `AVAILABLE`, timeline has a `NOTE` event "Trip cancelled…". Cancelling the same trip again: 409. |
| 18 | `PATCH /api/drivers/{id}/deactivate` as Owner A for Ramesh, then try to assign him to a new CONFIRMED booking. | 409 — inactive drivers cannot be assigned. His old (completed) trips still show his name. Reactivate him and the assignment works again. |

### Worked example (check these by hand)

Bengaluru → Hyderabad, 570 km, 20-ton load of rice, 12-tyre truck — the corridor from Module 3. Quote total was ₹33,280 (Module 3, Example 1). The booking is CONFIRMED at that price. Owner A assigns Ramesh. Timeline after the full job:

```text
10:05  ASSIGNED    Driver Ramesh Kumar assigned            (OWNER)
11:20  PICKED_UP   Loaded 20 tons rice                     (OWNER)
11:45  IN_TRANSIT  Left Bengaluru                          (OWNER)
22:10  DELIVERED   Delivered to warehouse gate 2,
                   signed by Mr. Sharma                    (OWNER)
22:15  COMPLETED   Trip complete                           (OWNER)
```

The customer sees exactly this — five stamps, no GPS dot, no missing step. If the 22:10 DELIVERED stamp exists, the proof note exists with it; if the proof note is missing, the stamp cannot exist, because test 11 proved the server refuses it.

### Common mistakes

- **Forgetting `@Transactional` on `assignDriver` or `updateStatus`.** Without it, the trip could be saved but the event lost (or the booking changed but the trip not created). Every method that writes more than one table must be transactional — check the annotation before debugging anything else.
- **Trusting a `driverId` or `ownerId` from the request body.** The owner always comes from the JWT. A `driverId` in the body is fine — but it must be looked up with `findByIdAndOwnerId`, never `findById`, or any owner can assign anyone's driver.
- **Letting the client send the status it wants.** The status flow is enforced by the `NEXT_STEP` map in the service, never by the frontend. If the frontend could set any status, a trip could jump from `ASSIGNED` to `COMPLETED` with no pickup and no proof.
- **Forgetting the proof check on DELIVERED.** The check must look at the trip's proof fields *after* applying the request (so proof saved earlier counts), not only at the request itself. Test 11 and test 12 together catch both wrong versions.
- **Deleting a driver instead of deactivating them.** Deleting breaks old trips (the driver link points nowhere) and erases history. Always deactivate. There is no delete endpoint on purpose.
- **Checking the driver is free with only one status.** The busy check must cover all four active statuses (`ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED`). Checking only `ASSIGNED` would let a driver be assigned a second trip while already on the road.
- **Forgetting to free the truck.** If `COMPLETED` does not set the truck back to `AVAILABLE`, the truck stays `ON_TRIP` forever and can never be booked again. Test 14 checks this explicitly.
- **Adding the two `GlobalExceptionHandler` methods in the wrong class, or not at all.** Without them, every 409 in the checklist comes back as a 500 — the logic is right and the test still fails. Do the Part 9 handler edit before running the tests.

---

## What changes in the PRD, and what is next

**PRD note (v1.1):** no PRD change in this module. The three open decisions from the PRD are now all decided — Both (B2B + B2C), Instant booking + owner call/chat, All India launch — and this module follows them: the trip flow is the same for a business customer and an individual customer, the owner assigns the driver after an instant booking, and addresses are free text anywhere in India.

**Module 6 — Payments (next):** pay for a completed trip — who pays, when, how much (the booking's agreed `totalFare`, never recalculated), payment status on the booking (`PAYMENT_PENDING → COMPLETED`, `PAYMENT_FAILED`), and what happens when a payment fails after delivery. No payment gateway is chosen yet, and no real money moves until that module says so.
