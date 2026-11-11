# Truck Booking — Module 4: Booking

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1–3 keep working; rename the package later only if you want to)
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8, Spring Security + JWT
**Builds on:** Module 1 — Authentication & Security (roles, JWT) · Module 2 — Truck Categories & Registration (slim version) · Module 3 — Search, Matching & Pricing (quotes, rates, platform fee ₹0)
**This module only:** Turn a saved quote into a booking, run it through its lifecycle, stop two customers booking the same truck, let the owner accept or reject, let the customer cancel under clear rules, and let the two parties call and message each other. No payment (Module 6), no driver assignment or live trip tracking (Module 5), no real-time chat — messages are read by refreshing, not by websocket.

## How to use this guide

1. Read Parts 1 to 4 so you understand *why* the code is shaped this way.
2. Add one file at a time in VS Code, in the order given in Part 5. Modules 1–3 must already be working — you need a login token, at least one `AVAILABLE` truck, and a saved quote before you can test anything here.
3. Make the two small edits in Part 9 first (one new method in `TruckRepository`, and finishing `GET /api/quotes/mine` from Module 3) — Module 4 will not compile or behave correctly without them.
4. Start the app and work through the test checklist in Part 10.
5. Do not skip the state machine in Part 3. It is the whole point of this module: if any code path can move a booking from any status to any other status, the booking will end up in an impossible state sooner or later, and no amount of frontend checking will save you.

> **The one rule of this module:** the price on a booking is a **photograph, not a live feed**. The moment a booking is created, every price and truck detail from the quote is copied onto the booking row itself. After that, changing a rate, renaming a category, or editing a truck can never change an old booking. If the customer agreed to ₹33,280, the booking says ₹33,280 forever.

---

## Part 1 — What this module does, and why it comes fourth

### What this module does

Module 1 answered *who are you?* Module 2 answered *what trucks exist?* Module 3 answered *which truck fits my load, and what will it cost?* Module 4 answers *I have chosen — lock it in.*

This module gives Truck Booking:

- A `bookings` table — one row per agreed trip, with the full price and the full truck details copied onto it at creation time (the snapshot)
- A booking lifecycle — 12 statuses and a strict list of which status can follow which, enforced in the service, never by the frontend
- Instant booking — `POST /api/bookings { quoteId }` creates a `PENDING` booking at once, at the quoted fixed price
- Owner accept / reject — the owner says yes (`CONFIRMED`) or no (`REJECTED` + a reason)
- No-double-booking — a database lock on the truck row inside one transaction, so two customers clicking at the same second cannot both win the same truck
- Cancel rules — written down once, in one place, so the customer, the owner and the admin all see the same rule
- Owner call / chat after booking — a `tel:` link with the owner's phone, and a simple message thread on the booking, visible only to the two parties and the admin

### Why it comes fourth

A booking needs a quote to book from. Without Module 3 there would be no agreed price to snapshot, and the booking would have to recalculate the price — which is exactly the mistake this module exists to prevent. And booking needs real trucks (Module 2) and real logged-in users (Module 1) to attach the booking to.

### What this module does not do

- No payment — `PAYMENT_PENDING` and `PAYMENT_FAILED` exist as statuses so Module 6 has somewhere to land, but no money moves in this module
- No driver assignment or trip tracking — `DRIVER_ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED` exist as statuses, and the owner can move a booking through them for now, but Module 5 owns driver assignment and live updates
- No real-time chat — messages are stored rows read by `GET`; the frontend refreshes to see new ones (Part 6 explains why)
- No overlapping-date check — a truck is blocked while it has *any* active booking. Checking "is the truck free on 14 November?" needs trip dates, which do not exist yet. That is out of scope for the MVP and is said plainly in Part 4, not hidden
- No price negotiation after booking — the booked price is final unless the booking is cancelled and a new quote is made

---

## Part 2 — The big picture in simple words

Think of Truck Booking like booking a hotel room.

1. **The quote is the price on the website.** You searched, you saw ₹33,280 for a 12-tyre truck, Bengaluru → Hyderabad. That price is written on the quote (Module 3).
2. **Booking is pressing "Book now".** You press the button and the site *photocopies* the price, the room, the dates — everything — onto your reservation. That photocopy is the snapshot. If the hotel changes its prices tomorrow, your reservation still says ₹33,280, because your reservation holds its own copy.
3. **PENDING is "waiting for the hotel to confirm".** Instant booking does not mean the truck is already on its way. It means *your request was placed instantly, at the fixed price*. The owner still has to say yes. Until then the booking is `PENDING` — reserved, but not confirmed. This is why `PENDING` still exists for instant booking: the truck has an owner, and only the owner can promise their truck and driver for your date. Skipping this step would let a customer force a truck onto a trip its owner never agreed to.
4. **The owner is the receptionist.** They see the incoming booking, check their truck and driver, and press Accept (`CONFIRMED`) or Reject (`REJECTED` + a reason, so the customer is not left guessing).
5. **One room, one guest.** While your booking is active, nobody else can book the same truck. The database lock in Part 4 is the receptionist holding the key while they write your name in the register — nobody else can grab the same key in that second.
6. **The trip is a journey with stations.** `CONFIRMED → DRIVER_ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED → COMPLETED`. A booking can only move to the *next* station, never jump from `PENDING` straight to `DELIVERED`. And from some stations there is a side exit: `CANCELLED`, `REJECTED`, or `DISPUTED`.
7. **Call and chat are the hotel phone in the room.** Once you have a booking, you can call the owner and message them about *this* trip. A stranger cannot — they have no booking, so they never see the number or the messages.

### The flow, step by step

```text
CUSTOMER logs in (Module 1 JWT)
  -> POST /api/quotes/search          (Module 3) gets a quote, e.g. id 7, total ₹33,280
  -> POST /api/bookings { "quoteId": 7 }
       server: quote must be ACTIVE and not expired, and not already BOOKED
       server: lock the truck row, check no active booking exists for it
       server: COPY every quote field onto the booking (the snapshot)
       server: quote.status = "BOOKED", booking.status = PENDING
       -> returns the booking (201 Created)

OWNER logs in
  -> GET /api/owner/bookings          sees the incoming PENDING booking
  -> POST /api/owner/bookings/{id}/accept     booking -> CONFIRMED, truck -> ON_TRIP
  ->   or POST /api/owner/bookings/{id}/reject { "reason": "..." }
       booking -> REJECTED, truck stays AVAILABLE

CUSTOMER (after accept)
  -> GET /api/bookings/mine           sees the booking, owner phone, chat link
  -> POST /api/bookings/{id}/messages { "message": "Please come at 8 am" }
  -> <a href="tel:+919876543210">Call owner</a>   (only shown to the two parties)

Trip (owner moves it for now; Module 5 gives this to the driver)
  -> CONFIRMED -> DRIVER_ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED -> COMPLETED
  -> truck -> AVAILABLE again when the booking COMPLETES or is CANCELLED
```

### Who can do what

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| Create a booking from a quote | Yes (own quote only) | No | No | No | No |
| See my own bookings | Yes | Yes (own trucks' bookings) | No | No | Yes, all |
| Accept / reject a booking | No | Yes (own trucks only) | No | No | No |
| Cancel a booking | Yes (PENDING / CONFIRMED only — Part 4) | Yes (own bookings, any non-final status) | No | No | Yes |
| Move a booking to the next trip status | No | Yes (own bookings, for now) | No — Module 5 | No | Yes |
| Call / message on a booking | Yes (own bookings) | Yes (own bookings) | No | No | Yes (read messages) |
| Touch a booking that is not theirs | No | No | No | No | Yes (read + cancel) |

---

## Part 3 — The lifecycle: 12 statuses and one state machine

### The statuses

| Status | Simple meaning | Is it final? |
| --- | --- | --- |
| `PENDING` | Booking created instantly from a quote; waiting for the owner to accept | No |
| `CONFIRMED` | Owner accepted; the truck is committed to this trip | No |
| `DRIVER_ASSIGNED` | A driver has been given this trip (owner does this in Module 4; Module 5 gives it to the driver flow) | No |
| `PICKED_UP` | Goods loaded onto the truck | No |
| `IN_TRANSIT` | Truck is on the road | No |
| `DELIVERED` | Goods reached the destination | No |
| `COMPLETED` | Trip finished and closed — the truck is free again | **Yes** |
| `REJECTED` | Owner said no, with a reason | **Yes** |
| `CANCELLED` | Booking called off (customer, owner, or admin — under the rules in Part 4) | **Yes** |
| `PAYMENT_PENDING` | Waiting for payment (Module 6 will use this; in Module 4 a booking only passes through it if you wire payment later) | No |
| `PAYMENT_FAILED` | A payment attempt failed (Module 6; see the note below) | No |
| `DISPUTED` | Something went wrong and the booking is frozen until an admin sorts it out | No |

**A note on `PAYMENT_PENDING` and `PAYMENT_FAILED`.** They exist now so that Module 6 does not have to change this enum, this table, or this state machine. In Module 4 no code moves a booking into them — there is no payment yet, so there is nothing to fail. If you see one of these statuses in the database during Module 4 testing, something is wrong; the only statuses Module 4 actually produces are `PENDING`, `CONFIRMED`, `REJECTED`, `DRIVER_ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `COMPLETED`, `CANCELLED`, and `DISPUTED`.

### The transition table — the only moves allowed

This table lives in code as a `Map` in `BookingStatus` (Part 5, File 1). The service checks every status change against it. If a move is not in the table, the service refuses with `409 Conflict` and the message "Invalid status change from X to Y". The frontend never decides this — a customer cannot send `{"status": "COMPLETED"}` and skip the trip, because the service checks the *current* status in the database, not what the client claims.

```text
PENDING         -> CONFIRMED, REJECTED, CANCELLED
CONFIRMED       -> DRIVER_ASSIGNED, PAYMENT_PENDING, CANCELLED, DISPUTED
PAYMENT_PENDING -> CONFIRMED, PAYMENT_FAILED
PAYMENT_FAILED  -> CONFIRMED, CANCELLED
DRIVER_ASSIGNED -> PICKED_UP, CANCELLED, DISPUTED
PICKED_UP       -> IN_TRANSIT, DISPUTED
IN_TRANSIT      -> DELIVERED, DISPUTED
DELIVERED       -> COMPLETED, DISPUTED
COMPLETED       -> (nothing — final)
REJECTED        -> (nothing — final)
CANCELLED       -> (nothing — final)
DISPUTED        -> CONFIRMED, CANCELLED
```

Three things to notice:

1. **There is no way back from a final status.** `COMPLETED`, `REJECTED` and `CANCELLED` have no outgoing moves. A cancelled booking is never "un-cancelled" — the customer books again from a new quote.
2. **`DISPUTED` is a freeze, not a dead end.** An admin resolves it by moving the booking back to `CONFIRMED` (the trip goes ahead) or to `CANCELLED` (the trip is called off). Nobody else may move a booking out of `DISPUTED`.
3. **Payment is a side loop, not a step forward.** `CONFIRMED → PAYMENT_PENDING → CONFIRMED` returns to where it started: payment happens *while* the booking is confirmed, and a failed payment does not un-confirm the booking — it goes to `PAYMENT_FAILED`, from which the customer retries (`→ CONFIRMED`) or cancels. That loop is dormant until Module 6.

### Why the state machine lives in the service, not in the controller

The controller is a door — it receives the request and passes it on. If the rule "PENDING cannot become DELIVERED" lived in the controller, a second controller (say, an admin controller added later) could forget it. In the service, there is exactly one method that changes a status — `changeStatus(...)` — and every endpoint (accept, reject, cancel, advance) goes through it. One rule, one place, no exceptions.

---

## Part 4 — The four hard rules, in plain words

### Rule 1 — The snapshot: copy everything at creation

When a booking is created, the service copies **all** of these from the quote (and from the truck, at that moment) onto the booking row:

- Trip: `pickup`, `destination`, `distanceKm`, `loadTons`, `goodsType`
- Truck at booking time: `truckRegistrationNumber`, `categoryName`, `tyreCount`, `payloadTonsAtBooking`, `bodyType`
- Money: `ratePerTonKm`, `chargeableTons`, `minimumChargeApplied`, `baseFare`, `tollCharges`, `loadingCharges`, `platformFee`, `totalFare`

It also keeps the links — `customer`, `owner`, `truck`, and `quoteId` — so you can always trace a booking back to its quote and its truck. But the *values* the customer agreed to live on the booking itself. After creation, the booking never reads the quote again for a price, and never reads the truck again for its registration or payload.

**Worked example (the snapshot test).** A customer books quote 7: 12-tyre truck, 20 tons, Bengaluru → Hyderabad, 570 km, total **₹33,280**. Then the admin changes the 12-tyre rate from ₹2.70 to ₹2.80 (Module 3, test 10). A new search now quotes ₹34,420 for the same trip. The old booking still says **₹33,280** — check it in the database, not in the frontend. If the booking price ever moves when a rate moves, the snapshot is broken and this module is not done.

### Rule 2 — Instant booking still waits for the owner

`POST /api/bookings` returns `201 Created` immediately — no waiting, no quotation round-trip, the fixed quoted price. That is the "instant" in instant booking. But the booking is born `PENDING`, and the truck is not yet committed: the owner must accept before a driver is assigned or the truck leaves. If the owner rejects, the booking becomes `REJECTED`, the customer sees the reason, and the quote stays `BOOKED` (it was used; a rejected booking does not free the quote for a second booking — the customer searches again and gets a fresh quote). This is the decision you made: **instant booking + owner call/chat**, not booking without the owner.

### Rule 3 — No double-booking: one lock, one transaction, one check

Two customers can click "Book now" on the same truck in the same second. Without protection, both requests read "truck is free", both create a booking, and one truck has two trips. This module stops that with three things working together, all inside one `@Transactional` method (`BookingService.createBooking`):

1. **Pessimistic lock on the truck row.** The service loads the truck with `findByIdForUpdate(...)`, which runs `SELECT ... FOR UPDATE`. The database locks that one truck row until the transaction ends. The second request waits at that line — it does not read a stale "free" — and when it finally gets the lock, it sees the world *after* the first booking. A pessimistic lock is the right choice here because a booking is a short, rare, high-stakes write: two people fighting over one truck is exactly the case where waiting a moment for the truth is better than guessing and colliding. (Optimistic locking with `@Version` would also work — it lets both requests run and makes the loser retry — but for a beginner the pessimistic lock is easier to reason about: the loser simply sees the booking that already exists and gets a clean `409`.)
2. **The availability check happens *inside* the transaction, after the lock.** Check-then-act must be one unbroken step. Checking availability in the controller and creating the booking in the service would leave a gap where another request slips in between — that gap is the whole bug.
3. **The active-booking check.** Even if the truck row still says `AVAILABLE` (it does during `PENDING` — see below), the service asks: does this truck already have a booking in `PENDING`, `CONFIRMED`, `DRIVER_ASSIGNED`, `PICKED_UP`, or `IN_TRANSIT`? If yes, the second request fails with `409 Conflict` and the message "Truck is already booked". The first booking wins; the second customer searches again.

**Truck status through the lifecycle:**

| Booking status | Truck `availabilityStatus` |
| --- | --- |
| `PENDING` | Stays `AVAILABLE` in the column — but the active-booking check above still blocks a second booking. The truck *looks* free in a list, yet cannot be double-booked. (If you would rather show it as busy, Module 5 can add a "reserved" display; the protection does not depend on the display.) |
| `CONFIRMED` / `DRIVER_ASSIGNED` | `ON_TRIP` — the truck is committed and disappears from search, because Module 3 only searches `AVAILABLE` trucks |
| `PICKED_UP` / `IN_TRANSIT` / `DELIVERED` | Stays `ON_TRIP` |
| `COMPLETED` / `CANCELLED` | Back to `AVAILABLE` — the truck can be booked again |
| `REJECTED` | Was never taken — stays `AVAILABLE` |

**Out of scope, said plainly:** there is **no overlapping-date check** in this MVP. The module does not know trip dates yet, so it blocks a truck for the whole time it has *any* active booking, even if the two trips are on different days. That is stricter than a finished product needs to be, and it is deliberate: over-blocking loses a little business, under-blocking sends one truck to two places. Date-based availability comes with trip dates in a later module.

### Rule 4 — Cancel rules: one rule, written down once

| Booking status | Customer cancel | Owner / admin cancel |
| --- | --- | --- |
| `PENDING` | Yes — free cancel | Yes |
| `CONFIRMED` | Yes — free cancel | Yes |
| `DRIVER_ASSIGNED` | **No — blocked.** The driver is already assigned and may already be travelling to the pickup. The customer must contact the owner or admin, who can cancel from their side. The API returns `409` with "Booking cannot be cancelled by the customer after a driver is assigned. Please contact the truck owner." | Yes |
| `PICKED_UP` | **No — blocked**, same as above | Yes |
| `IN_TRANSIT` and later | **No — cannot cancel.** The goods are on the road. The customer must contact the owner/admin; only the owner or admin can cancel or mark it disputed. | Yes (owner/admin only; use `DISPUTED` if the goods are lost or damaged rather than silently cancelling a moving truck) |
| `DELIVERED` / `COMPLETED` | No — the trip is done; cancelling a delivered load makes no sense | No — use `DISPUTED` if something is wrong |
| `REJECTED` / `CANCELLED` | No — already final | No |

**What cancellation does:** the booking becomes `CANCELLED` with a `cancelReason` (the customer may send one; the owner must send one), the truck goes back to `AVAILABLE` (unless it is on another booking, which the active-booking check would have prevented), and the quote stays `BOOKED` — a used quote is never reused. If the customer wants the trip after all, they search again and book a fresh quote at the current rate.

### Rule 5 — Call and chat belong to the booking, not to the public site

- **Call:** the owner's phone number comes from Module 1 (`User.phone` — a 10-digit Indian mobile). The frontend shows it as `<a href="tel:+91XXXXXXXXXX">Call owner</a>`. The number is **never** on a public truck listing and never in a search result. It appears only inside `BookingResponse`, and every booking endpoint checks that the caller is the booking's customer, its owner, or an admin *before* returning anything — so the phone number is only ever sent to a party to that booking. Nobody else can ask the API for it, because the service throws `403` first.
- **Chat:** a `BookingMessage` row holds `booking`, `sender`, `message`, `createdAt`. `POST /api/bookings/{id}/messages` writes one; `GET /api/bookings/{id}/messages` reads the thread, oldest first. Only the booking's customer, owner, and ADMIN may read or write. A customer from another booking gets `403`. There are **no websockets and no push in this MVP** — the frontend polls (calls `GET` again every few seconds, or when the user opens the chat) to see new messages. That is a deliberate MVP choice: polling is simple, debuggable, and works everywhere; real-time delivery is a later polish step, not a booking rule.

---

## Part 5 — Files to create (15 files + 2 small edits)

All under `src/main/java/com/truckconnect/`, continuing Modules 1–3. **No new dependency is needed.**

| # | File | What it is |
| --- | --- | --- |
| 1 | `booking/BookingStatus.java` | Enum — the 12 statuses + the transition table |
| 2 | `booking/Booking.java` | Entity — one booking, with the full snapshot |
| 3 | `booking/BookingRepository.java` | Finds a customer's / owner's / truck's bookings |
| 4 | `booking/BookingMessage.java` | Entity — one chat message on a booking |
| 5 | `booking/BookingMessageRepository.java` | Reads a booking's thread |
| 6 | `booking/dto/CreateBookingRequest.java` | What the customer sends: just the quote id |
| 7 | `booking/dto/BookingResponse.java` | What everyone gets back: snapshot + names + owner phone (parties only) |
| 8 | `booking/dto/BookingStatusUpdateRequest.java` | New status + optional note/reason |
| 9 | `booking/dto/BookingMessageRequest.java` | One chat message in |
| 10 | `booking/dto/BookingMessageResponse.java` | One chat message out |
| 11 | `booking/BookingMapper.java` | One place that turns a `Booking` into a `BookingResponse` (no mapping in controllers) |
| 12 | `booking/BookingService.java` | The brain: create, accept, reject, cancel, status, messages, ownership checks |
| 13 | `booking/BookingController.java` | Customer endpoints + shared booking details and messages |
| 14 | `booking/OwnerBookingController.java` | Owner endpoints: incoming list, accept, reject, cancel, advance |
| 15 | `quote/QuoteMapper.java` | Turns a saved `Quote` into a `QuoteResponse` — finishes Module 3's `GET /api/quotes/mine` |

**Edits (not new files):**

- **Edit A — `truck/TruckRepository.java` (Module 2):** add `findByIdForUpdate(Long id)` with a pessimistic write lock (Part 9).
- **Edit B — `quote/QuoteController.java` (Module 3):** replace the empty-list `myQuotes` body with a real call to the service (Part 9), and add a `myQuotes(User)` method to `SearchService` that uses `QuoteMapper`.

> **Check Module 1 first:** this module imports `com.truckconnect.user.User` and uses `@AuthenticationPrincipal User` exactly as Module 1's `AuthController.me` does — the JWT filter puts the `User` entity itself into the security context. If your Module 1 stores something else there (for example a `UserDetails` that is not the entity), change only the `@AuthenticationPrincipal` parameter type in the two controllers to whatever your Module 1 controllers already use, and load the `User` from `UserRepository` by email inside the service.

---

## Part 6 — The entities, repositories and the state machine

### File 1 of 15 — `booking/BookingStatus.java`

**Simple meaning:** The list of statuses, plus the table from Part 3 written as data. `canMoveTo(from, to)` is the one question the whole lifecycle asks.

```java
package com.truckconnect.booking;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    DRIVER_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    DISPUTED;

    /** The transition table from Part 3. A move not listed here is refused. */
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED =
            new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(CONFIRMED, REJECTED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(DRIVER_ASSIGNED, PAYMENT_PENDING,
                CANCELLED, DISPUTED));
        ALLOWED.put(PAYMENT_PENDING, EnumSet.of(CONFIRMED, PAYMENT_FAILED));
        ALLOWED.put(PAYMENT_FAILED, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(DRIVER_ASSIGNED, EnumSet.of(PICKED_UP, CANCELLED, DISPUTED));
        ALLOWED.put(PICKED_UP, EnumSet.of(IN_TRANSIT, DISPUTED));
        ALLOWED.put(IN_TRANSIT, EnumSet.of(DELIVERED, DISPUTED));
        ALLOWED.put(DELIVERED, EnumSet.of(COMPLETED, DISPUTED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(DISPUTED, EnumSet.of(CONFIRMED, CANCELLED));
    }

    public static boolean canMoveTo(BookingStatus from, BookingStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Statuses in which the truck is still committed to this booking. */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(
            PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT);

    /** Statuses from which nobody can move any more. */
    public static boolean isFinal(BookingStatus status) {
        return status == COMPLETED || status == REJECTED || status == CANCELLED;
    }
}
```

### File 2 of 15 — `booking/Booking.java`

**Simple meaning:** One row is one agreed trip. The top half is links (who, which truck, which quote). The middle is the snapshot — plain columns copied from the quote at creation, so the booking never needs the quote or the truck again to know its price. The bottom is the lifecycle: status, why it was cancelled or rejected, and when it changed.

```java
package com.truckconnect.booking;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

> **Getter names to check:** this module calls `truck.getOwner()`, `truck.getRegistrationNumber()`, `truck.getCategory().getName()`, `truck.getCategory().getTyreCount()`, `truck.getPayloadTons()`, `truck.getBodyType()`, `truck.getAvailabilityStatus()` / `setAvailabilityStatus(...)`, and `user.getPhone()` / `user.getFullName()` / `user.getId()`. All of these exist in Modules 1 and 2 as written. If you renamed any of them, change only those calls — the design does not change.

### File 3 of 15 — `booking/BookingRepository.java`

**Simple meaning:** Method names are the queries. The last one is the double-booking guard: "does this truck already have a booking in one of these statuses?"

```java
package com.truckconnect.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Booking> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<Booking> findByTruckIdOrderByCreatedAtDesc(Long truckId);

    /** True if the truck already has a live booking — the no-double-booking check. */
    boolean existsByTruckIdAndStatusIn(Long truckId, java.util.Collection<BookingStatus> statuses);
}
```

### File 4 of 15 — `booking/BookingMessage.java`

**Simple meaning:** One row is one chat message about one booking. Nothing fancy: who sent it, what it says, when. There is no "read/unread" flag in the MVP — the frontend shows the whole thread.

```java
package com.truckconnect.booking;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_messages")
public class BookingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public BookingMessage() { }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking b) { this.booking = b; }
    public User getSender() { return sender; }
    public void setSender(User s) { this.sender = s; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### File 5 of 15 — `booking/BookingMessageRepository.java`

```java
package com.truckconnect.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingMessageRepository extends JpaRepository<BookingMessage, Long> {
    /** Oldest first, so the frontend can render the thread top to bottom. */
    List<BookingMessage> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
```

---

## Part 7 — The DTOs and the mapper

### File 6 of 15 — `booking/dto/CreateBookingRequest.java`

**Simple meaning:** The customer sends exactly one thing — which quote they are booking. No price, no truck id, no dates. The price and the truck both come from the saved quote in the database, so a customer cannot book a ₹33,280 quote and claim it cost ₹1, and cannot swap in a different truck by editing the request.

```java
package com.truckconnect.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "quoteId is required") Long quoteId
) { }
```

### File 7 of 15 — `booking/dto/BookingResponse.java`

**Simple meaning:** Everything the frontend needs to show one booking: who is involved, the snapshot (so the price shown is the booked price, not a recalculation), the status, and — only because every endpoint checks the caller is a party first — the owner's phone number for the `tel:` link, and the customer's phone for the owner. If you ever add an endpoint that returns this response *without* the party check, remove the phone fields from it first.

```java
package com.truckconnect.booking.dto;

import com.truckconnect.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        Long quoteId,
        BookingStatus status,
        Long customerId,
        String customerName,
        String customerPhone,
        Long ownerId,
        String ownerName,
        String ownerPhone,
        Long truckId,
        String truckRegistrationNumber,
        String categoryName,
        int tyreCount,
        BigDecimal payloadTonsAtBooking,
        String bodyType,
        String pickup,
        String destination,
        BigDecimal distanceKm,
        BigDecimal loadTons,
        String goodsType,
        BigDecimal ratePerTonKm,
        BigDecimal chargeableTons,
        boolean minimumChargeApplied,
        BigDecimal baseFare,
        BigDecimal tollCharges,
        BigDecimal loadingCharges,
        BigDecimal platformFee,
        BigDecimal totalFare,
        String cancelReason,
        String ownerNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
```

### File 8 of 15 — `booking/dto/BookingStatusUpdateRequest.java`

```java
package com.truckconnect.booking.dto;

import com.truckconnect.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusUpdateRequest(
        @NotNull(message = "status is required") BookingStatus status,
        String note,        // optional note from the owner, e.g. driver name
        String reason       // optional reason, required by the service for REJECTED/CANCELLED
) { }
```

### Files 9 and 10 of 15 — the message DTOs

**`booking/dto/BookingMessageRequest.java`**

```java
package com.truckconnect.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingMessageRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 1000, message = "Message must be at most 1000 characters")
        String message
) { }
```

**`booking/dto/BookingMessageResponse.java`**

```java
package com.truckconnect.booking.dto;

import java.time.LocalDateTime;

public record BookingMessageResponse(
        Long id,
        Long bookingId,
        Long senderId,
        String senderName,
        String senderRole,
        String message,
        LocalDateTime createdAt
) { }
```

### File 11 of 15 — `booking/BookingMapper.java`

**Simple meaning:** One class, one job: turn a `Booking` entity into a `BookingResponse`. Controllers never build responses by hand, and the service never returns entities to the outside world — entities carry lazy-loaded links that would leak or crash in JSON. This is the mapping choice for this module: a small static mapper class, used by the service. (Module 3's `QuoteMapper` in File 15 does the same job for quotes.)

```java
package com.truckconnect.booking;

import com.truckconnect.booking.dto.BookingResponse;

public final class BookingMapper {

    private BookingMapper() { }

    public static BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getQuote().getId(),
                b.getStatus(),
                b.getCustomer().getId(),
                b.getCustomer().getFullName(),
                b.getCustomer().getPhone(),
                b.getOwner().getId(),
                b.getOwner().getFullName(),
                b.getOwner().getPhone(),
                b.getTruck().getId(),
                b.getTruckRegistrationNumber(),
                b.getCategoryName(),
                b.getTyreCount(),
                b.getPayloadTonsAtBooking(),
                b.getBodyType() == null ? null : b.getBodyType().name(),
                b.getPickup(),
                b.getDestination(),
                b.getDistanceKm(),
                b.getLoadTons(),
                b.getGoodsType(),
                b.getRatePerTonKm(),
                b.getChargeableTons(),
                b.isMinimumChargeApplied(),
                b.getBaseFare(),
                b.getTollCharges(),
                b.getLoadingCharges(),
                b.getPlatformFee(),
                b.getTotalFare(),
                b.getCancelReason(),
                b.getOwnerNote(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }
}
```

> **Lazy loading warning:** `toResponse` touches `b.getCustomer()`, `b.getOwner()`, `b.getTruck()` and `b.getQuote()`. Those are `LAZY` links, so this method must run **inside a transaction** (the service methods are `@Transactional`), or Hibernate will throw a `LazyInitializationException` when the controller tries to build the response after the transaction closed. If you see that error, the fix is to keep the mapping inside the service method — never to make the links `EAGER`.

---

## Part 8 — The service: where every rule lives

### File 12 of 15 — `booking/BookingService.java`

**Simple meaning:** This is the brain. Read it top to bottom: create (with the lock and the snapshot), accept, reject, cancel (with the Part 4 table), move to the next status (through the state machine), messages, and the two small ownership checks every other method relies on. Errors use `ResponseStatusException` with the right HTTP status — Spring returns it directly, so no change to Module 1's `GlobalExceptionHandler` is needed for these: `404` not found, `403` not yours, `409` not allowed in this state / already booked.

```java
package com.truckconnect.booking;

import com.truckconnect.booking.dto.*;
import com.truckconnect.quote.Quote;
import com.truckconnect.quote.QuoteRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMessageRepository messageRepository;
    private final QuoteRepository quoteRepository;
    private final TruckRepository truckRepository;

    public BookingService(BookingRepository bookingRepository,
                          BookingMessageRepository messageRepository,
                          QuoteRepository quoteRepository,
                          TruckRepository truckRepository) {
        this.bookingRepository = bookingRepository;
        this.messageRepository = messageRepository;
        this.quoteRepository = quoteRepository;
        this.truckRepository = truckRepository;
    }

    // ---------- Create: instant booking from a quote ----------

    /**
     * Creates a PENDING booking from a saved quote, at the quoted fixed price.
     * The whole method is ONE transaction, and the truck row is locked first:
     * a second request for the same truck waits, then sees the first booking
     * and fails with 409 instead of creating a double booking.
     */
    @Transactional
    public BookingResponse createBooking(User customer, CreateBookingRequest req) {
        Quote quote = quoteRepository.findById(req.quoteId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Quote not found: " + req.quoteId()));

        // The quote must belong to this customer — never book someone else's quote.
        if (!quote.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This quote does not belong to you");
        }
        // The quote must still be usable: ACTIVE, not expired, not already BOOKED.
        if (!"ACTIVE".equals(quote.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quote is no longer active (status: " + quote.getStatus() + ")");
        }
        if (quote.getValidUntil().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quote has expired. Please search again for a fresh quote.");
        }

        // Lock the truck row FIRST, then check — never check, then lock.
        Truck truck = truckRepository.findByIdForUpdate(quote.getTruck().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Truck not found"));

        // Guard 1: the truck itself must be free right now.
        if (truck.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Truck is not available right now");
        }
        // Guard 2: no other live booking may exist for this truck, even a
        // PENDING one (a PENDING booking reserves the truck — see Part 4).
        boolean alreadyBooked = bookingRepository.existsByTruckIdAndStatusIn(
                truck.getId(), BookingStatus.ACTIVE);
        if (alreadyBooked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Truck is already booked");
        }

        // Snapshot: copy EVERYTHING from the quote and the truck, right now.
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setOwner(truck.getOwner());
        booking.setTruck(truck);
        booking.setQuote(quote);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickup(quote.getPickup());
        booking.setDestination(quote.getDestination());
        booking.setDistanceKm(quote.getDistanceKm());
        booking.setLoadTons(quote.getLoadTons());
        booking.setGoodsType(quote.getGoodsType());
        booking.setTruckRegistrationNumber(truck.getRegistrationNumber());
        booking.setCategoryName(truck.getCategory().getName());
        booking.setTyreCount(truck.getCategory().getTyreCount());
        booking.setPayloadTonsAtBooking(truck.getPayloadTons());
        booking.setBodyType(truck.getBodyType());
        booking.setRatePerTonKm(quote.getRatePerTonKm());
        booking.setChargeableTons(quote.getChargeableTons());
        booking.setMinimumChargeApplied(quote.isMinimumChargeApplied());
        booking.setBaseFare(quote.getBaseFare());
        booking.setTollCharges(quote.getTollCharges());
        booking.setLoadingCharges(quote.getLoadingCharges());
        booking.setPlatformFee(quote.getPlatformFee());
        booking.setTotalFare(quote.getTotalFare());

        booking = bookingRepository.save(booking);

        // The quote is now used. It can never be booked a second time.
        quote.setStatus("BOOKED");
        quoteRepository.save(quote);

        return BookingMapper.toResponse(booking);
    }

    // ---------- Read: my bookings, one booking ----------

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(User customer) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(BookingMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> ownerBookings(User owner) {
        return bookingRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(BookingMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(User user, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireParty(user, booking);
        return BookingMapper.toResponse(booking);
    }

    // ---------- Owner: accept / reject ----------

    @Transactional
    public BookingResponse acceptBooking(User owner, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireOwner(owner, booking);
        // changeStatus checks the state machine: only PENDING -> CONFIRMED.
        changeStatus(booking, BookingStatus.CONFIRMED, null, null);
        // The truck is now committed: it leaves search until the trip ends.
        Truck truck = booking.getTruck();
        truck.setAvailabilityStatus(AvailabilityStatus.ON_TRIP);
        truckRepository.save(truck);
        return BookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse rejectBooking(User owner, Long bookingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required when rejecting a booking");
        }
        Booking booking = loadBooking(bookingId);
        requireOwner(owner, booking);
        changeStatus(booking, BookingStatus.REJECTED, reason, reason);
        // Truck was never taken, so it stays AVAILABLE. Nothing else to undo.
        return BookingMapper.toResponse(booking);
    }

    // ---------- Cancel: the Part 4 table, in code ----------

    @Transactional
    public BookingResponse cancelByCustomer(User customer, Long bookingId, String reason) {
        Booking booking = loadBooking(bookingId);
        requireCustomer(customer, booking);
        switch (booking.getStatus()) {
            case PENDING, CONFIRMED -> {
                // Free cancel. The truck is released below.
            }
            case DRIVER_ASSIGNED, PICKED_UP -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking cannot be cancelled by the customer after a driver "
                    + "is assigned. Please contact the truck owner.");
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking in status " + booking.getStatus()
                    + " cannot be cancelled by the customer");
        }
        return doCancel(booking, reason);
    }

    @Transactional
    public BookingResponse cancelByOwnerOrAdmin(User user, Long bookingId, String reason) {
        Booking booking = loadBooking(bookingId);
        if (user.getRole() == Role.ADMIN) {
            // Admin may cancel any non-final booking; ownership does not apply.
        } else {
            requireOwner(user, booking);
        }
        if (BookingStatus.isFinal(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking in status " + booking.getStatus() + " cannot be cancelled");
        }
        return doCancel(booking, reason);
    }

    private BookingResponse doCancel(Booking booking, String reason) {
        changeStatus(booking, BookingStatus.CANCELLED, reason, null);
        // Release the truck so it can be booked again.
        Truck truck = booking.getTruck();
        if (truck.getAvailabilityStatus() == AvailabilityStatus.ON_TRIP) {
            truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            truckRepository.save(truck);
        }
        return BookingMapper.toResponse(booking);
    }

    // ---------- Move to the next trip status ----------

    @Transactional
    public BookingResponse updateStatus(User user, Long bookingId,
                                        BookingStatusUpdateRequest req) {
        Booking booking = loadBooking(bookingId);
        // In Module 4 the owner (or admin) moves the trip forward.
        // Module 5 hands the trip statuses to the driver.
        if (user.getRole() == Role.ADMIN) {
            // Admin may move any booking, except out of DISPUTED (handled below).
        } else {
            requireOwner(user, booking);
        }
        if (booking.getStatus() == BookingStatus.DISPUTED
                && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an admin can resolve a disputed booking");
        }
        if (req.status() == BookingStatus.CANCELLED
                || req.status() == BookingStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use the cancel / reject endpoint for "
                    + req.status() + ", not the status endpoint");
        }
        changeStatus(booking, req.status(), null, req.note());

        // Completing the trip frees the truck for the next booking.
        if (req.status() == BookingStatus.COMPLETED) {
            Truck truck = booking.getTruck();
            truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            truckRepository.save(truck);
        }
        return BookingMapper.toResponse(booking);
    }

    // ---------- Messages: booking chat ----------

    @Transactional
    public BookingMessageResponse sendMessage(User user, Long bookingId,
                                              BookingMessageRequest req) {
        Booking booking = loadBooking(bookingId);
        requireParty(user, booking);
        if (BookingStatus.isFinal(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot message on a booking that is " + booking.getStatus());
        }
        BookingMessage msg = new BookingMessage();
        msg.setBooking(booking);
        msg.setSender(user);
        msg.setMessage(req.message().trim());
        msg = messageRepository.save(msg);
        return toMessageResponse(msg);
    }

    @Transactional(readOnly = true)
    public List<BookingMessageResponse> getMessages(User user, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireParty(user, booking);
        return messageRepository.findByBookingIdOrderByCreatedAtAsc(bookingId)
                .stream().map(this::toMessageResponse).toList();
    }

    private BookingMessageResponse toMessageResponse(BookingMessage m) {
        return new BookingMessageResponse(
                m.getId(), m.getBooking().getId(), m.getSender().getId(),
                m.getSender().getFullName(), m.getSender().getRole().name(),
                m.getMessage(), m.getCreatedAt());
    }

    // ---------- Helpers: the one status-change method, and ownership ----------

    /** The ONLY place a booking's status changes. Every move is checked here. */
    private void changeStatus(Booking booking, BookingStatus to,
                              String reason, String note) {
        if (!BookingStatus.canMoveTo(booking.getStatus(), to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid status change from " + booking.getStatus()
                    + " to " + to);
        }
        booking.setStatus(to);
        if (reason != null) booking.setCancelReason(reason);
        if (note != null) booking.setOwnerNote(note);
        bookingRepository.save(booking);
    }

    private Booking loadBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));
    }

    /** Customer, owner, or admin — anyone else gets 403. */
    private void requireParty(User user, Booking booking) {
        boolean isCustomer = booking.getCustomer().getId().equals(user.getId());
        boolean isOwner = booking.getOwner().getId().equals(user.getId());
        if (!isCustomer && !isOwner && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not a party to this booking");
        }
    }

    private void requireCustomer(User customer, Booking booking) {
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This is not your booking");
        }
    }

    private void requireOwner(User owner, Booking booking) {
        if (!booking.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This booking is not for one of your trucks");
        }
    }
}
```

Four things to notice in this code:

1. **`createBooking` never trusts the request for a price or a truck.** The request carries only `quoteId`. The truck, the owner, the price, and the customer all come from the database and the JWT. A forged request cannot change any of them.
2. **The quote status is a plain `String` in Module 3** (`"ACTIVE"`, `"BOOKED"`, `"EXPIRED"`), not an enum — so the service compares strings here (`"ACTIVE".equals(quote.getStatus())`) and writes `"BOOKED"`. If you later turn the quote status into an enum, change only those two lines.
3. **Rejecting does not free the quote.** The quote stays `BOOKED`. That is deliberate: a quote is a one-time offer, and a rejected booking means the customer must search again. If you instead want a rejected booking to return the quote to `ACTIVE`, change one line in `rejectBooking` — but then the same quote could be booked twice, so think twice before doing it.
4. **`updateStatus` refuses `CANCELLED` and `REJECTED`.** Those two moves carry a mandatory reason and their own side effects (releasing the truck), so they have their own endpoints. One generic endpoint that silently cancels would hide the reason.

### File 13 of 15 — `booking/BookingController.java` (customer + shared)

**Simple meaning:** The customer's doors, plus the two doors both parties share (booking details and messages). Role checks are `@PreAuthorize` on the method; ownership checks are in the service, because a role check can say "you are a customer" but only the service knows *whose* booking this is.

```java
package com.truckconnect.booking;

import com.truckconnect.booking.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(customer, request));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<BookingResponse>> myBookings(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(bookingService.myBookings(customer));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<BookingResponse> getOne(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBooking(user, id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BookingResponse> cancel(
            @AuthenticationPrincipal User customer,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BookingStatusUpdateRequest req) {
        String reason = req == null ? null : req.reason();
        return ResponseEntity.ok(
                bookingService.cancelByCustomer(customer, id, reason));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<List<BookingMessageResponse>> messages(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getMessages(user, id));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')")
    public ResponseEntity<BookingMessageResponse> sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody BookingMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.sendMessage(user, id, request));
    }
}
```

### File 14 of 15 — `booking/OwnerBookingController.java`

**Simple meaning:** The owner's doors. Note the URL: `/api/owner/bookings`, separate from the customer's `/api/bookings`, so the role rule is obvious at a glance and a customer can never stumble into an owner endpoint.

```java
package com.truckconnect.booking;

import com.truckconnect.booking.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/bookings")
@PreAuthorize("hasRole('OWNER')")
public class OwnerBookingController {

    private final BookingService bookingService;

    public OwnerBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Incoming bookings for this owner's trucks, newest first. */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> incoming(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(bookingService.ownerBookings(owner));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<BookingResponse> accept(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.acceptBooking(owner, id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<BookingResponse> reject(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest req) {
        return ResponseEntity.ok(
                bookingService.rejectBooking(owner, id, req.reason()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BookingStatusUpdateRequest req) {
        String reason = req == null ? null : req.reason();
        return ResponseEntity.ok(
                bookingService.cancelByOwnerOrAdmin(owner, id, reason));
    }

    /** Move the trip forward (CONFIRMED -> DRIVER_ASSIGNED -> ... -> COMPLETED). */
    @PostMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateStatus(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest req) {
        return ResponseEntity.ok(bookingService.updateStatus(owner, id, req));
    }
}
```

> **A note on the admin:** there is deliberately no `AdminBookingController` in this module. The admin can already read any booking (`GET /api/bookings/{id}`), read any thread, and cancel any booking — but cancelling as admin needs a door. Add one small method to `BookingController` if you need it now: `POST /api/bookings/{id}/cancel` with `@PreAuthorize("hasRole('ADMIN')")` calling `cancelByOwnerOrAdmin`. The service already supports it; only the door is missing. Do not duplicate the cancel logic anywhere else to add it.

### File 15 of 15 — `quote/QuoteMapper.java` (finishes Module 3)

**Simple meaning:** The same mapper idea as `BookingMapper`, for quotes. Module 3 left `GET /api/quotes/mine` returning an empty list because this mapping had no home yet — now it does.

```java
package com.truckconnect.quote;

import com.truckconnect.quote.dto.QuoteResponse;

public final class QuoteMapper {

    private QuoteMapper() { }

    public static QuoteResponse toResponse(Quote q) {
        return new QuoteResponse(
                q.getId(),
                q.getTruck().getId(),
                q.getTruck().getRegistrationNumber(),
                q.getTruck().getCategory().getName(),
                q.getTruck().getCategory().getTyreCount(),
                q.getTruck().getPayloadTons(),
                q.getTruck().getBodyType() == null
                        ? null : q.getTruck().getBodyType().name(),
                q.getRatePerTonKm(),
                q.getChargeableTons(),
                q.isMinimumChargeApplied(),
                q.getBaseFare(),
                q.getTollCharges(),
                q.getLoadingCharges(),
                q.getPlatformFee(),
                q.getTotalFare(),
                q.getValidUntil());
    }
}
```

> **A detail to check:** Module 3's `QuoteResponse` record has no `pickup`, `destination`, `distanceKm`, `loadTons`, or `goodsType` fields — it only carries the truck, the rate, and the fare breakdown. That is enough for the "my quotes" list, and changing the record now would break Module 3's search response. If you want the route shown in "my quotes", add those five fields to `QuoteResponse` and to both places that build it (here and in `SearchService.buildQuote`) — a small, safe change, but do it in both places or the code will not compile.

---

## Part 9 — The two small edits (do these first)

### Edit A — `truck/TruckRepository.java`: the lock method

Add this method to the Module 2 repository. `@Lock(PESSIMISTIC_WRITE)` makes the database lock the truck row until the transaction ends; the `@Query` is needed because Spring Data cannot express a lock on a plain `findById` method name.

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

// ... inside the TruckRepository interface, alongside the other methods:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select t from Truck t where t.id = :id")
Optional<Truck> findByIdForUpdate(@Param("id") Long id);
```

> **Flag — adjust this line if your Module 2 differs:** this query assumes the entity class is named `Truck` in package `com.truckconnect.truck`. If your Module 2 entity is named differently, change only `Truck` in the `select` above to match. Nothing else in this module depends on the query text.

### Edit B — finish `GET /api/quotes/mine` (Module 3)

In `quote/QuoteController.java`, replace the empty-list body:

```java
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<QuoteResponse>> myQuotes(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(searchService.myQuotes(customer));
    }
```

And add this method to `quote/SearchService.java` (it needs only the `QuoteRepository`, which the service already has):

```java
    @Transactional(readOnly = true)
    public List<QuoteResponse> myQuotes(User customer) {
        return quoteRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(QuoteMapper::toResponse).toList();
    }
```

That is the whole finish: no new endpoint, no new table, just the mapping Module 3 deferred. A quote whose `validUntil` has passed still appears in this list — it is history, not a live offer — but `POST /api/bookings` will refuse it (Part 8), so an expired quote can never be booked by mistake.

---

## Part 10 — Test checklist (do these in order)

You need: the app running, one CUSTOMER token, one OWNER token (the owner of the test truck), a second OWNER token (who owns a different truck), one ADMIN token, and one DRIVER token. Create a quote first with Module 3 search (Bengaluru → Hyderabad, 570 km, 20 tons, 12-tyre truck) — note its id and its total (₹33,280 with toll ₹1,500, loading ₹1,000, fee ₹0).

| # | Test | Expected result |
| --- | --- | --- |
| 1 | `POST /api/bookings { "quoteId": <id> }` as CUSTOMER. | `201 Created`. Status `PENDING`. `totalFare` ₹33,280, `truckRegistrationNumber`, `categoryName`, `tyreCount` 12, `payloadTonsAtBooking` 20 all present. The `quotes` row now has status `BOOKED`. |
| 2 | Snapshot: as ADMIN, change the 12-tyre rate to ₹2.80 (`PUT /api/admin/rates/{id}`), then `GET /api/bookings/{id}` again. | The booking still says **₹33,280** and rate ₹2.70. A new search quotes the higher price. Change the rate back to ₹2.70 afterwards. |
| 3 | Expired quote: in MySQL set an unused quote's `valid_until` to yesterday, then book it. | `409 Conflict`, "Quote has expired". |
| 4 | Already-BOOKED quote: book the same quote id from test 1 a second time. | `409 Conflict` — the quote status is `BOOKED`, not `ACTIVE`. |
| 5 | Double booking: book quote A for truck X (PENDING). Search again for the same truck, get quote B, and book it too. | Second booking fails with `409` "Truck is already booked", even though the truck row still says `AVAILABLE`. (Repeat the two requests at the same moment if you can — the lock must still let only one through.) |
| 6 | Owner accept: `POST /api/owner/bookings/{id}/accept` as the truck's owner. | Status `CONFIRMED`. The truck row is now `ON_TRIP`, and Module 3 search no longer returns it. |
| 7 | Owner reject: create a second booking on another truck, then reject it with `{ "reason": "Driver on leave" }`. | Status `REJECTED`, `cancelReason` holds the message. The truck stays `AVAILABLE`. Rejecting with no reason returns `400`. |
| 8 | Wrong owner cannot accept: the second OWNER tries to accept the first owner's booking. | `403 Forbidden`. |
| 9 | Customer cancel in PENDING: create a booking, cancel it as the customer. | `CANCELLED`, truck back to `AVAILABLE`. |
| 10 | Customer cancel in CONFIRMED: accept a booking, then cancel it as the customer. | `CANCELLED`, truck back to `AVAILABLE`. |
| 11 | Cancel in-transit blocked: move a booking to `DRIVER_ASSIGNED`, then `PICKED_UP`, then `IN_TRANSIT` (owner status endpoint), then cancel as the customer. | `409` "cannot be cancelled by the customer after a driver is assigned" (or the in-transit message). The same cancel as the owner succeeds. |
| 12 | Illegal jump: try `POST /api/owner/bookings/{id}/status { "status": "DELIVERED" }` on a `PENDING` booking. | `409` "Invalid status change from PENDING to DELIVERED". |
| 13 | Chat only for booking parties: customer and owner exchange messages on their booking; a different customer tries `GET /api/bookings/{id}/messages`. | The parties see the thread, oldest first. The stranger gets `403`. |
| 14 | Call link only for booking parties: `GET /api/bookings/{id}` as the booking's customer shows `ownerPhone`; the same call as a different customer. | The party sees the phone number (use it as `tel:+91...` in the frontend). The stranger gets `403` and never sees the number. |
| 15 | Unauthenticated: any booking endpoint with no token. | `401 Unauthorized`. |
| 16 | Wrong role: `POST /api/bookings` as OWNER; `POST /api/owner/bookings/{id}/accept` as CUSTOMER; any booking endpoint as DRIVER. | `403 Forbidden` in all three cases — the driver cannot touch bookings until Module 5. |
| 17 | Full happy path: book → accept → `DRIVER_ASSIGNED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED` → `COMPLETED`. | Each step returns `200`, the status moves one step at a time, and after `COMPLETED` the truck is `AVAILABLE` again and a new booking for it succeeds. |
| 18 | `GET /api/quotes/mine` as CUSTOMER. | Returns the customer's saved quotes (including the `BOOKED` one from test 1), newest first — no longer an empty list. |

### Common mistakes

- **Checking availability before locking the truck.** If the check and the lock are in different steps, two requests can both pass the check. The lock must come first, inside the same transaction — that ordering is the whole fix.
- **Reading the price from the quote at display time.** The booking response must come from the booking's own snapshot columns. If the frontend (or an endpoint) re-reads the quote to show the price, a rate change will silently rewrite history.
- **Letting the client send the status directly.** A `PUT /api/bookings/{id}` that copies `request.status` onto the entity bypasses the state machine entirely. Status changes go only through `changeStatus(...)` in the service.
- **Forgetting the truck release on cancel/complete.** A truck left `ON_TRIP` after its booking ended disappears from search forever. Every path out of an active booking — cancel, complete — must set the truck back to `AVAILABLE`.
- **Showing the owner's phone in search results.** The phone lives only in `BookingResponse`, behind the party check. If a search or truck-list response ever includes it, any stranger can harvest owner numbers.
- **Returning the `Booking` entity from a controller.** Lazy links (`customer`, `truck`, `quote`) will throw `LazyInitializationException` outside a transaction, or leak fields you never meant to expose. Always return the response DTO, built inside the service.
- **Treating `PENDING` as "not booked yet".** A `PENDING` booking already reserves the truck — the active-booking check blocks a second booking. If you only block on `CONFIRMED`, two `PENDING` bookings will both be accepted and you will have the double-booking you built all of this to prevent.
- **Using `double` for money.** Same rule as Module 3: `BigDecimal`, never `double`. A fare of ₹33,280.00 must stay exactly that.

---

## What is next: Module 5 — Driver & Trip Management

Module 4 ends with a booking that an owner moves through its trip statuses by hand. Module 5 gives that job to the right person and adds the missing pieces of the trip:

- **Driver assignment** — the owner (or transporter) assigns a real driver to a `CONFIRMED` booking, moving it to `DRIVER_ASSIGNED`; the driver sees only their own trips
- **Driver trip updates** — the driver (not the owner) moves the booking `PICKED_UP → IN_TRANSIT → DELIVERED`, with the role checks to match
- **Trip dates and location** — pickup date/time, and where the truck is, so the overlapping-date check Module 4 deliberately left out can finally be built properly
- **Proof of delivery** — what the driver records at `DELIVERED` before the booking can become `COMPLETED`
- **Dispute handling** — who raises a dispute, what the admin sees, and how a `DISPUTED` booking is resolved back to `CONFIRMED` or `CANCELLED`

After Module 5: customer/owner/driver/admin React screens, then payments (Module 6, which will finally use `PAYMENT_PENDING` and `PAYMENT_FAILED`), then deployment — Vercel + Render + TiDB Cloud free MySQL, the same free stack as ShopKart.
