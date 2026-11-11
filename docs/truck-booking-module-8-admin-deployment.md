# Truck Booking — Module 8: Admin Dashboard & Analytics + Final Integration, Testing & Free Deployment

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1–7 keep working; rename the package later only if you want to)
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8, Spring Security + JWT, React + Tailwind frontend, modular monolith
**Builds on:** Module 1 — Authentication & Security · Module 2 — Truck Categories & Registration (slim) · Module 3 — Search, Matching & Pricing · Module 4 — Booking lifecycle · Module 5 — Drivers, Trips & Delivery Proof · Module 6 — Payments & Owner Settlements · Module 7 — React pages for customer / owner / driver / admin
**This module only:** The admin dashboard and analytics, the admin management endpoints, the final end-to-end integration check, the full test suite, and the free deployment guide. No new business feature, no new payment gateway, no live distance — this module ties everything together and takes it live for ₹0.

> **Deployment is a GUIDE only — read this first:** nothing in this module deploys anything live, creates any account, or signs you in anywhere. You do the GitHub / TiDB Cloud / Render / Vercel steps yourself in Part 9, with your own sign-ins. This guide never asks for a password or a secret in chat, and no real secret appears anywhere in this file.

## How to use this guide

1. Read Parts 1 and 2 first so you understand *why* the dashboard counts what it counts — especially the one rule in Part 1: **revenue is the platform fee only, never the total fare**.
2. Modules 4–7 were built in parallel with this module. This guide does **not** wait for them and does **not** redo them. It assumes the contracts in Part 0. If a name in your Modules 4–7 differs from the assumed name, change only the one flagged line — do not rewrite the whole file.
3. Add one file at a time in VS Code, in the order given in Part 4.
4. Run the unit tests in Part 8, then the integration and security tests, then the manual smoke test in Part 8.5.
5. Only when every local test passes, follow Part 9 to deploy — in this order: GitHub → TiDB Cloud → Render backend → Vercel frontend → verify live.
6. Finish with Part 11: run it locally first (MySQL, backend on 8080, frontend on 5173), every time, before you trust the live site.

> **The one rule of this module:** the admin sees **everything about the business, and changes almost nothing by hand**. The dashboard is read-only numbers. Admins deactivate a bad user, resolve a dispute, and change a rate — they never delete a user, never edit a booking price, and never touch money directly. If a number on the dashboard looks wrong, the bug is in Modules 3–6, not in the dashboard.

---

## Part 0 — What this module assumes from Modules 1–7 (read this first)

Modules 4–7 are assumed, not re-checked here. If your code already uses these names, everything below works as written. If a name differs, change only the flagged line.

| Assumed name | Where it comes from | What to check / flagged line |
| --- | --- | --- |
| `com.truckconnect.user.User`, `Role` enum: `CUSTOMER, OWNER, DRIVER, TRANSPORTER, ADMIN` | Module 1 | If your `User` is in another package, change only the import lines. If your role field is called `role`, the dashboard queries work; if it is called something else, change `countByRole` in Part 3, File 2. |
| `com.truckconnect.truck.Truck`, `AvailabilityStatus`: `AVAILABLE, UNAVAILABLE, ON_TRIP` | Module 2 (slim) | Truck fields: `registrationNumber, category, bodyType, payloadTons, availabilityStatus`. No documents, no paperwork. |
| `com.truckconnect.truck.TruckCategory`, `PricingRate`, `PlatformSettings` (platform fee ₹0) | Modules 2–3 | Rates: 12-tyre ₹2.70, 14 ₹2.60, 18 ₹2.50, 22 ₹2.40 per ton per km. Fare = distance × rate × chargeable tons + toll + loading + ₹0. Chargeable tons = max(load, 50% of capacity). |
| `com.truckconnect.booking.Booking`, `BookingStatus`: `PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, COMPLETED, REJECTED, CANCELLED, PAYMENT_PENDING, PAYMENT_FAILED, DISPUTED` | Module 4 (assumed) | **Flag — check this field first:** this guide assumes `Booking` has `platformFee` (BigDecimal, copied from the quote at booking time), `totalFare`, and `completedAt` (LocalDateTime, set when the status becomes COMPLETED). If your Module 4 has no `completedAt`, use `updatedAt` in the revenue query in Part 3, File 2 — the flagged line says exactly where. Snapshot price: the booking never recalculates the fare. |
| `com.truckconnect.booking.BookingRepository` | Module 4 (assumed) | Assumed methods are listed in Part 3, File 2. Add any that are missing — they are one-line Spring Data method names. |
| `com.truckconnect.driver.Driver`, `Trip`, `TripEvent` | Module 5 (assumed) | Only used in the integration flow (Part 7), not in dashboard code. |
| `com.truckconnect.payment.OwnerSettlement`, settlement status `PENDING, PAID` | Module 6 (assumed) | Assumed fields: `ownerAmount` (BigDecimal), `status` (String or enum). **Flag:** if your settlement status enum is named differently, change only the `sumPendingSettlements` query in Part 3, File 2. COD is the default payment; Razorpay is later (Part 10). |
| React pages for customer / owner / driver / admin | Module 7 (assumed) | The admin dashboard page in Module 7 calls `GET /api/admin/dashboard` from Part 3. If Module 7 built its dashboard page against a different response shape, change the frontend field names to match the DTO in Part 3, File 1 — do not change the backend DTO. |
| Decisions (final) | PRD | **Both** B2B + B2C, **Instant booking + owner call / chat**, **All India** launch. |

---

## Part 1 — What this module does, and why it comes last

### What this module does

Modules 1–7 answered *who are you, what trucks exist, what does it cost, how do I book, who drives it, how is it paid for, and what does the screen look like?* Module 8 answers *is the business healthy, is anything stuck, and can a stranger on the internet use it?*

This module gives Truck Booking:

- An admin dashboard endpoint: `GET /api/admin/dashboard` — counts of users by role, trucks by status, bookings by status, revenue this month, and pending settlements, all from simple repository count/sum queries. No new dependency, no analytics library.
- Admin management endpoints, consolidated in one place: list and deactivate users, resolve disputes, view the audit log, and a recap of category + rate management from Modules 2–3.
- An audit log: a small `AuditLog` table that records who did what, to which booking / payment / settlement, and when — so a dispute can be answered from facts, not memory.
- A final integration checklist: the whole journey from registration to settlement, step by step, with the exact endpoint and expected status for each step.
- A full test suite: unit tests, integration tests, security tests, and a manual smoke test you run with your own hands.
- A free deployment guide (₹0 total): GitHub → TiDB Cloud free MySQL → Render free backend → Vercel free frontend, with the exact environment variables and the exact order.

### The revenue rule (the most important paragraph in this module)

**Revenue is the platform fee only — ₹0 per COMPLETED booking — never the total fare.**

The total fare belongs to the truck owner. If a booking costs ₹33,280 (Module 3, Example 1), the platform earned ₹0 and the owner earned ₹33,280. A dashboard that adds up total fares would claim the business made ₹33,280 on that trip, when the platform actually earned nothing from it — and every decision made from that number would be wrong.

> **Honest note:** with the platform fee at ₹0, `revenueThisMonth` is ₹0 in this MVP. That is correct, not a bug — the platform takes no cut from bookings yet. If the business later wants revenue, it must come from something new (for example a commission or a subscription), decided and built as a separate step. Do not invent a fee here to make the dashboard look busy.

So:

```text
revenueThisMonth = SUM(platformFee) over bookings
                   where status = COMPLETED
                   and completedAt is in this calendar month
```

Owner earnings are **separate**: they live in Module 6 `OwnerSettlement` rows (gross fare − ₹0 platform fee = owner amount). The dashboard shows `pendingSettlementsTotal` as a separate number so the admin can see what is still owed to owners. Never add revenue and owner earnings together, and never subtract one from the other on this screen — they are two different piles of money.

### What this module does not do

- No Razorpay gateway — COD stays the default; Razorpay is a "not done yet" item in Part 10.
- No live Google Maps distance — the customer still enters distance in km (Module 3).
- No real-time chat — owner call / chat is polling only in the MVP (Part 10).
- No push notifications, no mobile app, no driver self-service login beyond Module 5.
- No deleting users, no editing booking prices, no changing another admin's role — see Part 5 for why each of these is forbidden.

---

## Part 2 — The big picture in simple words

Think of Truck Booking like a transport office with a whiteboard.

1. **The whiteboard.** Every morning the manager writes on it: how many customers, owners, drivers; how many trucks are free, busy, off; how many bookings are waiting, moving, delivered, stuck; how much the office earned this month (₹0 platform fee per finished trip — see the revenue rule above); how much is still owed to owners. That whiteboard is `GET /api/admin/dashboard`. The manager does not count by walking around the yard — the numbers come from the database, in one request.
2. **The complaint register.** When a customer says "my goods arrived damaged", the manager opens the register, writes down what happened, and marks the booking resolved — the goods are either accepted as delivered, or the booking is cancelled and the money handled manually. That is dispute handling in Part 5. The register never rewrites history: the original booking stays, and the resolution is added on top.
3. **The logbook.** Every time a booking changes status, a payment is confirmed, or a settlement is paid, someone writes one line in the logbook: who, what, which booking, when. That is the audit log in Part 6. If two people disagree later, the logbook settles it.
4. **The final walk-through.** Before the office opens to the public, the manager walks the whole journey once, as a customer, then as an owner, then as a driver, then as an admin — register, add a truck, search, quote, book, assign a driver, pick up, deliver, pay COD, settle, check the whiteboard moved. That is Part 7. If any step fails, the office does not open.
5. **Opening the office to the public.** The code goes on GitHub, the database lives on TiDB Cloud (free), the backend lives on Render (free), the website lives on Vercel (free). That is Part 9. The office is the same code you ran on your laptop — only the address changes.

### The flow, step by step

```text
ADMIN logs in (Module 1 JWT, role ADMIN)
  -> GET /api/admin/dashboard
       { usersByRole, trucksByStatus, bookingsByStatus,
         revenueThisMonth, pendingSettlementsTotal }
  -> GET /api/admin/users            list users, deactivate a bad one
  -> GET /api/admin/disputes         bookings with status DISPUTED
  -> POST /api/admin/disputes/{id}/resolve   note + terminal status
  -> GET /api/admin/audit-logs       who changed what, when

EVERYONE ELSE (customer / owner / driver):
  -> GET /api/admin/dashboard        403 Forbidden — admin only, always
```

### Who can do what

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| View dashboard | No | No | No | No | Yes |
| List / deactivate users | No | No | No | No | Yes (never delete, never change another admin's role) |
| Resolve a dispute | No | No | No | No | Yes |
| View audit log | No | No | No | No | Yes |
| Change a category or a rate | No | No | No | No | Yes (Modules 2–3 endpoints, recapped in Part 5) |
| See revenue | No | No | No | No | Yes — platform fee only |

---

## Part 3 — Admin Dashboard backend: the counts, the money, and nothing else

**Simple meaning:** the dashboard is a read-only summary. It does not create, change, or delete anything. It asks the database five simple questions — how many, how many, how many, how much fee, how much owed — and returns the answers in one JSON object. No new dependency, no analytics library, no scheduled job.

### File 1 of 12 — `admin/dto/AdminDashboardResponse.java`

**Simple meaning:** exactly what the frontend (Module 7 admin page) receives. Maps are used for the counts so a new status or role appears automatically without changing this file.

```java
package com.truckconnect.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminDashboardResponse(
        Map<String, Long> usersByRole,
        Map<String, Long> trucksByStatus,
        Map<String, Long> bookingsByStatus,
        BigDecimal revenueThisMonth,
        BigDecimal pendingSettlementsTotal,
        long totalUsers,
        long totalTrucks,
        long totalBookings
) { }
```

Why `Map<String, Long>` and not one field per role: if Module 1 ever adds a role, the dashboard still works. The frontend reads the map keys (`CUSTOMER`, `OWNER`, `DRIVER`, `TRANSPORTER`, `ADMIN`) and shows whatever is there.

### File 2 of 12 — the repository queries (add these to existing repositories)

**Simple meaning:** Spring Data writes these queries from the method names. No JPQL is needed except for the two money sums, and those are two short `@Query` lines. Add each method to the repository named — do not create new repositories for these.

Add to `user/UserRepository.java` (Module 1):

```java
long countByRole(Role role);
```

Add to `truck/TruckRepository.java` (Module 2):

```java
long countByAvailabilityStatus(AvailabilityStatus status);
```

Add to `booking/BookingRepository.java` (Module 4) — this is the file with the most additions:

```java
package com.truckconnect.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countByStatus(BookingStatus status);

    List<Booking> findByStatus(BookingStatus status);

    // Revenue this month = SUM of the platform fee only, on COMPLETED bookings
    // whose completion date is on or after the first day of this month.
    // NEVER sum totalFare here — that money belongs to the owner, not the platform.
    @Query("SELECT COALESCE(SUM(b.platformFee), 0) FROM Booking b " +
           "WHERE b.status = 'COMPLETED' AND b.completedAt >= :from")
    BigDecimal sumPlatformFeesSince(@Param("from") LocalDateTime from);
}
```

> **Flag — check this line before you run it:** `b.completedAt` assumes your Module 4 `Booking` has a `completedAt` field that is set when the status becomes COMPLETED. If your Module 4 has no `completedAt`, change only `b.completedAt` in the query above to `b.updatedAt` (if your entity has an `updatedAt` field) — and set `from` to the first day of the month as usual. Do not change anything else in this file.

Add to `payment/OwnerSettlementRepository.java` (Module 6):

```java
// Total still owed to owners: SUM of ownerAmount where the settlement is PENDING.
@Query("SELECT COALESCE(SUM(s.ownerAmount), 0) FROM OwnerSettlement s " +
       "WHERE s.status = 'PENDING'")
BigDecimal sumPendingSettlements();
```

> **Flag — check this line:** this assumes the settlement status value is the string `'PENDING'`. If your Module 6 settlement status is an enum (for example `SettlementStatus.PENDING`), change the query to `WHERE s.status = com.truckconnect.payment.SettlementStatus.PENDING` — one line, in this file only. If your field is called `ownerPayable` instead of `ownerAmount`, change only that field name here and in File 3.

Why `COALESCE(..., 0)`: when there are no completed bookings yet, `SUM` returns `null`, and the dashboard would crash or show a blank. `COALESCE` turns that `null` into `0` — a new business correctly shows ₹0 revenue.

### File 3 of 12 — `admin/AdminDashboardService.java`

**Simple meaning:** the service asks each repository one question, puts the answers in the DTO, and returns it. It contains no business rule except the date rule: "this month" means from the 1st of this month at 00:00 until now.

```java
package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminDashboardResponse;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.payment.OwnerSettlementRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final TruckRepository truckRepository;
    private final BookingRepository bookingRepository;
    private final OwnerSettlementRepository settlementRepository;

    public AdminDashboardService(UserRepository userRepository,
                                 TruckRepository truckRepository,
                                 BookingRepository bookingRepository,
                                 OwnerSettlementRepository settlementRepository) {
        this.userRepository = userRepository;
        this.truckRepository = truckRepository;
        this.bookingRepository = bookingRepository;
        this.settlementRepository = settlementRepository;
    }

    public AdminDashboardResponse getDashboard() {
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        long totalUsers = 0;
        for (Role role : Role.values()) {
            long count = userRepository.countByRole(role);
            usersByRole.put(role.name(), count);
            totalUsers += count;
        }

        Map<String, Long> trucksByStatus = new LinkedHashMap<>();
        long totalTrucks = 0;
        for (AvailabilityStatus status : AvailabilityStatus.values()) {
            long count = truckRepository.countByAvailabilityStatus(status);
            trucksByStatus.put(status.name(), count);
            totalTrucks += count;
        }

        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        long totalBookings = 0;
        for (BookingStatus status : BookingStatus.values()) {
            long count = bookingRepository.countByStatus(status);
            bookingsByStatus.put(status.name(), count);
            totalBookings += count;
        }

        // Revenue this month: platform fee only, COMPLETED bookings only.
        LocalDateTime startOfMonth = LocalDate.now()
                .withDayOfMonth(1).atStartOfDay();
        BigDecimal revenueThisMonth =
                bookingRepository.sumPlatformFeesSince(startOfMonth);

        BigDecimal pendingSettlementsTotal =
                settlementRepository.sumPendingSettlements();

        return new AdminDashboardResponse(
                usersByRole, trucksByStatus, bookingsByStatus,
                revenueThisMonth, pendingSettlementsTotal,
                totalUsers, totalTrucks, totalBookings);
    }
}
```

Work through it by hand: 2 completed bookings this month, each with platform fee ₹0 → `sumPlatformFeesSince` returns ₹0. That is the revenue. If those two bookings had total fares of ₹33,280 and ₹17,890, the revenue is still ₹0 — the ₹51,170 of fares is the owners' money, and it appears only in the settlements total, never here.

### File 4 of 12 — `admin/AdminDashboardController.java`

```java
package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminDashboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> dashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
}
```

**Simple meaning:** one endpoint, admin only, read-only. The `@PreAuthorize` on the class covers every method in this controller — a customer, owner, or driver gets 403 before any code runs. No request body, no parameters: the dashboard is always "right now".

### Example response (what the Module 7 admin page receives)

```json
{
  "usersByRole": { "CUSTOMER": 12, "OWNER": 5, "DRIVER": 8, "TRANSPORTER": 1, "ADMIN": 1 },
  "trucksByStatus": { "AVAILABLE": 4, "UNAVAILABLE": 1, "ON_TRIP": 2 },
  "bookingsByStatus": { "PENDING": 1, "CONFIRMED": 1, "IN_TRANSIT": 1, "COMPLETED": 2, "DISPUTED": 0, "CANCELLED": 0 },
  "revenueThisMonth": 0.00,
  "pendingSettlementsTotal": 51170.00,
  "totalUsers": 27,
  "totalTrucks": 7,
  "totalBookings": 5
}
```

Read it as a sentence: 27 users, 7 trucks, 5 bookings, ₹0 earned this month (2 × ₹0 platform fee), ₹51,170 still owed to owners. If `revenueThisMonth` ever looks like a fare total (tens of thousands after one booking), the revenue query is summing the wrong field — go back to File 2 and check it sums `platformFee`, not `totalFare`.

---

## Part 4 — Files to create (12 files)

All under `src/main/java/com/truckconnect/`, continuing Modules 1–7. No new dependency is needed. No new table except `audit_logs` (Part 6).

| # | File | What it is |
| --- | --- | --- |
| 1 | `admin/dto/AdminDashboardResponse.java` | Dashboard response (Part 3) |
| 2 | Repository additions | One-line methods added to existing `UserRepository`, `TruckRepository`, `BookingRepository`, `OwnerSettlementRepository` (Part 3) |
| 3 | `admin/AdminDashboardService.java` | Counts + revenue + pending settlements (Part 3) |
| 4 | `admin/AdminDashboardController.java` | `GET /api/admin/dashboard` (Part 3) |
| 5 | `admin/dto/AdminUserResponse.java` + `admin/AdminUserController.java` | List / deactivate users (Part 5) |
| 6 | `admin/dto/DisputeResolveRequest.java` + `admin/DisputeController.java` | Disputed bookings + resolve (Part 5) |
| 7 | `admin/AuditLog.java` + `admin/AuditLogRepository.java` + `admin/AuditService.java` | Audit log entity, repository, writer (Part 6) |
| 8 | `admin/AuditLogController.java` | `GET /api/admin/audit-logs` (Part 6) |
| 9 | `src/main/resources/application.properties` + `application-prod.properties` | Local vs production profiles (Part 9) |
| 10 | `render.yaml` (optional) + `.gitignore` additions | Render as code, and never commit secrets (Part 9) |
| 11 | `src/test/java/com/truckconnect/pricing/PricingServiceTest.java` | Unit test — fare examples (Part 8) |
| 12 | `src/test/java/com/truckconnect/booking/BookingServiceTest.java`, `quote/SearchServiceTest.java`, `admin/AdminDashboardServiceTest.java` | Unit tests — transitions, matching, dashboard (Part 8) |

Create them in this order, top to bottom. Files 9–10 are deployment files, not Java — do them after the code works locally, not before.

---

## Part 5 — Admin management endpoints: users, disputes, categories & rates

**Simple meaning:** these are the only three things an admin is allowed to change by hand — a user's active status, a dispute's outcome, and a category / rate. Everything else (booking prices, payments, settlements) is changed only by the normal flow in Modules 4–6, never by an admin typing a new value.

### 5.1 Users — list and deactivate. Never delete, never change another admin's role.

Why never delete a user: a deleted customer would orphan their bookings, quotes, and settlements — the database would hold bookings that point to nobody. Deactivation keeps the history and blocks the login. If Module 1 already has an `active` / `enabled` field on `User`, use it; if it does not, add one boolean field `active` (default `true`) to `User` and make your Module 1 login check reject inactive users with 403. **Flag:** if your Module 1 `UserDetailsService` does not check `active`, add that one check there — deactivation does nothing without it.

Why never change another admin's role: if one admin could demote another admin, a compromised admin account could lock every other admin out. Role changes for admins are a manual database job for the project owner, outside the app, and they do not happen through an endpoint at all.

**File 5 of 12 — `admin/dto/AdminUserResponse.java`**

```java
package com.truckconnect.admin.dto;

public record AdminUserResponse(
        Long id, String name, String email, String role, boolean active) { }
```

> **Flag:** this assumes `User` has `getName()`, `getEmail()`, `getRole()`, and `isActive()`. If your Module 1 named the name field `fullName` or `username`, change only those getter calls in the controller below.

**File 5 (continued) — `admin/AdminUserController.java`**

```java
package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminUserResponse;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminUserController(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> list() {
        List<AdminUserResponse> out = userRepository.findAll().stream()
                .map(u -> new AdminUserResponse(u.getId(), u.getName(),
                        u.getEmail(), u.getRole().name(), u.isActive()))
                .toList();
        return ResponseEntity.ok(out);
    }

    // Deactivate a user. Never delete. Never deactivate yourself.
    // Never deactivate another ADMIN — that is the rule in Part 5 above.
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<AdminUserResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (user.getId().equals(admin.getId())) {
            throw new IllegalStateException("You cannot deactivate your own admin account.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalStateException("You cannot deactivate another admin.");
        }
        user.setActive(false);
        userRepository.save(user);
        auditService.log(admin, "USER_DEACTIVATED", "USER", user.getId());
        return ResponseEntity.ok(new AdminUserResponse(user.getId(), user.getName(),
                user.getEmail(), user.getRole().name(), user.isActive()));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<AdminUserResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(true);
        userRepository.save(user);
        auditService.log(admin, "USER_ACTIVATED", "USER", user.getId());
        return ResponseEntity.ok(new AdminUserResponse(user.getId(), user.getName(),
                user.getEmail(), user.getRole().name(), user.isActive()));
    }
}
```

> **Note on `@AuthenticationPrincipal User`:** same note as Module 3 — use whatever your Module 1 controllers already use to get the logged-in user, and change only that parameter. **Note on errors:** `IllegalArgumentException` / `IllegalStateException` here return 500 by default in plain Spring Boot. If Module 1 or 4 already added a global `@ControllerAdvice` handler that turns them into 404 / 400, this works as written. If not, add that handler now — it is one small file, and Part 8 tests expect 404 for a missing user, not 500.

### 5.2 Disputes — list DISPUTED bookings, resolve with a note, back to a terminal state

**Simple meaning:** a dispute is a booking with status `DISPUTED` (set in Module 4 or 5 when a customer reports a problem). The admin does not fix the truck or re-drive the trip — the admin decides the outcome, writes down why, and moves the booking to a terminal state: `COMPLETED` (the delivery is accepted after all, COD / settlement stands) or `CANCELLED` (the booking is voided; any refund is handled manually in Module 6, never automatically here). The booking never goes back to `IN_TRANSIT` or `PENDING` from a dispute — a disputed trip is over, one way or the other.

**File 6 of 12 — `admin/dto/DisputeResolveRequest.java`**

```java
package com.truckconnect.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DisputeResolveRequest(
        @NotNull String resolution,   // COMPLETED or CANCELLED — the terminal state
        @NotBlank String adminNote    // required: why this decision was made
) { }
```

**File 6 (continued) — `admin/DisputeController.java`**

```java
package com.truckconnect.admin;

import com.truckconnect.admin.dto.DisputeResolveRequest;
import com.truckconnect.booking.Booking;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class DisputeController {

    private final BookingRepository bookingRepository;
    private final AuditService auditService;

    public DisputeController(BookingRepository bookingRepository, AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<Booking>> listDisputed() {
        return ResponseEntity.ok(bookingRepository.findByStatus(BookingStatus.DISPUTED));
    }

    @PostMapping("/{bookingId}/resolve")
    public ResponseEntity<Booking> resolve(
            @PathVariable Long bookingId,
            @Valid @RequestBody DisputeResolveRequest req,
            @AuthenticationPrincipal User admin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.DISPUTED) {
            throw new IllegalStateException(
                    "Only a DISPUTED booking can be resolved. Current status: "
                    + booking.getStatus());
        }
        BookingStatus resolution;
        try {
            resolution = BookingStatus.valueOf(req.resolution());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "resolution must be COMPLETED or CANCELLED, not: " + req.resolution());
        }
        if (resolution != BookingStatus.COMPLETED
                && resolution != BookingStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "resolution must be COMPLETED or CANCELLED, not: " + req.resolution());
        }
        booking.setStatus(resolution);
        // Store the admin's reason on the booking if Module 4 gave it a field
        // for it (for example adminNote). If your Booking has no such field,
        // the note still survives in the audit log line below — do not add a
        // column just for this; the audit log is the record.
        bookingRepository.save(booking);
        auditService.log(admin, "DISPUTE_RESOLVED_" + resolution.name(),
                "BOOKING", booking.getId());
        return ResponseEntity.ok(booking);
    }
}
```

> **Flag:** `booking.setStatus(...)` assumes a `setStatus(BookingStatus)` setter on Module 4's `Booking`. If your Module 4 stores status as a `String`, change only that call to `booking.setStatus(resolution.name())`. If your Module 4 `Booking` does have an `adminNote` field, add one line before `save`: `booking.setAdminNote(req.adminNote());` — otherwise skip it, as the comment says.

Worked example: booking #42 is DISPUTED because the customer says 2 tons arrived short. The admin checks the delivery proof (Module 5), finds the proof photo shows the full load, and resolves it: `{"resolution": "COMPLETED", "adminNote": "Delivery proof photo shows full load; customer accepted on call."}` → status becomes COMPLETED, revenue counts its ₹0 fee, and the settlement stands. If instead the proof is missing, the admin resolves it as CANCELLED with a note, and the refund (if any COD was paid) is handled manually in Module 6.

### 5.3 Categories + rates — recap, no new code

Admin category and rate management already exists. Do not rebuild it here — use it, and link it from the admin page.

| What the admin does | Endpoint (already built) | Module |
| --- | --- | --- |
| List categories (12 / 14 / 18 / 22 tyre) | `GET /api/categories` (or your Module 2 list endpoint) | Module 2 |
| Create / update a category | Module 2 admin category endpoints | Module 2 |
| See the rate board | `GET /api/admin/rates` | Module 3 |
| Change one rate (₹0.10–₹20.00 per ton per km) | `PUT /api/admin/rates/{id}` | Module 3 |
| Change the platform fee (default ₹0) | `platform_settings` row, id = 1 (Module 3 seeder) — change it in the database or via a Module 3 admin settings endpoint if you built one; there is no new endpoint in this module | Module 3 |

Changing a rate never changes an old quote or an old booking — the rate is copied onto the quote at search time (Module 3) and onto the booking at booking time (Module 4). That is why the dashboard revenue never changes when a rate changes: revenue is counted from bookings that already happened, at the fee they already carried.

---

## Part 6 — Audit log: who did what, to what, when

**Simple meaning:** a one-line diary entry for every important change. The audit log is never edited and never deleted through the app. It is written by the code, not by a person typing — so nobody can write a flattering version of events afterwards.

### When a line is written

| Event | Action string | Written by |
| --- | --- | --- |
| Booking status changes (accept, assign driver, pick up, deliver, complete, cancel, reject, dispute) | `BOOKING_<NEW_STATUS>` — e.g. `BOOKING_CONFIRMED`, `BOOKING_DELIVERED`, `BOOKING_DISPUTED` | Module 4 booking service — add one `auditService.log(...)` call next to each `setStatus` (see "How to wire it" below) |
| Payment confirmed (COD confirmed) | `PAYMENT_CONFIRMED` | Module 6 payment service |
| Settlement paid to owner | `SETTLEMENT_PAID` | Module 6 settlement service |
| User deactivated / activated | `USER_DEACTIVATED` / `USER_ACTIVATED` | Part 5 (already in the code above) |
| Dispute resolved | `DISPUTE_RESOLVED_COMPLETED` / `DISPUTE_RESOLVED_CANCELLED` | Part 5 (already in the code above) |

### File 7 of 12 — `admin/AuditLog.java`, `admin/AuditLogRepository.java`, `admin/AuditService.java`

**`admin/AuditLog.java`** — the entity. `targetType` is a short word like `BOOKING`, `USER`, `PAYMENT`, `SETTLEMENT`; `targetId` is that row's id. No foreign key is used on purpose: if a booking were ever deleted, the log line must survive.

```java
package com.truckconnect.admin;

import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;               // who did it (the logged-in admin / owner / system user)

    @Column(nullable = false, length = 60)
    private String action;            // e.g. BOOKING_CONFIRMED, PAYMENT_CONFIRMED

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;        // e.g. BOOKING, USER, PAYMENT, SETTLEMENT

    @Column(name = "target_id", nullable = false)
    private Long targetId;            // id of the booking / user / payment / settlement

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditLog() { }

    public AuditLog(User actor, String action, String targetType, Long targetId) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public Long getId() { return id; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**`admin/AuditLogRepository.java`**

```java
package com.truckconnect.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByCreatedAtDesc();
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId);
}
```

**`admin/AuditService.java`** — the writer. Every service that changes something important calls this one method. It is deliberately boring: build the row, save it, done.

```java
package com.truckconnect.admin;

import com.truckconnect.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User actor, String action, String targetType, Long targetId) {
        auditLogRepository.save(new AuditLog(actor, action, targetType, targetId));
    }
}
```

### How to wire it into Modules 4 and 6 (do this, it is small)

In your Module 4 `BookingService`, wherever you change a booking's status, add one line directly after the `save` (or before it — either works, as long as it is in the same method):

```java
// Example — inside the owner-accept method, after bookingRepository.save(booking):
auditService.log(owner, "BOOKING_CONFIRMED", "BOOKING", booking.getId());
```

Do the same for every status change (`BOOKING_DRIVER_ASSIGNED`, `BOOKING_PICKED_UP`, `BOOKING_IN_TRANSIT`, `BOOKING_DELIVERED`, `BOOKING_COMPLETED`, `BOOKING_CANCELLED`, `BOOKING_REJECTED`, `BOOKING_DISPUTED`), and in Module 6 after a COD payment is confirmed (`PAYMENT_CONFIRMED`, target type `PAYMENT`, target id = the payment id) and after a settlement is paid (`SETTLEMENT_PAID`, target type `SETTLEMENT`, target id = the settlement id). Inject `AuditService` into those services with a constructor parameter, exactly as the controllers above do. If a Module 4/6 method has no logged-in user to pass as `actor` (for example a system job), pass the booking's owner or customer — the important part is that the line exists, not whose name is on it.

### File 8 of 12 — `admin/AuditLogController.java`

```java
package com.truckconnect.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> list() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<AuditLog>> forBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("BOOKING", bookingId));
    }
}
```

**Simple meaning:** admin only, read-only, newest first. The second endpoint answers the dispute question "what happened to booking #42, in order?" in one call.

---

## Part 7 — Final integration checklist: the whole journey, step by step

**Simple meaning:** do these steps in order, with the exact endpoint and expected result for each one. Do not skip a step — a later step depends on the earlier one (you cannot assign a driver to a booking that does not exist). Use the demo data below, then repeat with your own values.

Demo data for this walk-through: customer `customer@demo.in`, owner `owner@demo.in`, driver created by the owner, truck 12-tyre, payload 20 tons, Bengaluru → Hyderabad, 570 km, 20 tons of rice, toll ₹1,500, loading ₹1,000. Expected fare: ₹33,280 (Module 3, Example 1). If your fare differs, stop — the bug is in Module 3, not here.

| # | Step | Who | Endpoint | Expected status | What must change after this step |
| --- | --- | --- | --- | --- | --- |
| 1 | Register customer | — | `POST /api/auth/register` `{"name","email","password","role":"CUSTOMER"}` | 201 (or 200 — use whatever Module 1 returns) | Customer row exists; dashboard `usersByRole.CUSTOMER` goes up by 1 |
| 2 | Register owner | — | `POST /api/auth/register` `{"role":"OWNER"}` | 201 / 200 | Owner row exists |
| 3 | Log in as owner, get JWT | Owner | `POST /api/auth/login` | 200 + token | Token works on the next call |
| 4 | Owner adds a truck | Owner | `POST /api/trucks` `{"registrationNumber":"KA01AB1234","categoryId":1,"bodyType":"OPEN","payloadTons":20}` | 201 / 200 | Truck exists, `availabilityStatus = AVAILABLE`; dashboard `trucksByStatus.AVAILABLE` goes up by 1 |
| 5 | Admin sees the rates | Admin | `GET /api/admin/rates` | 200 | 4 rates; 12-tyre = ₹2.70 |
| 6 | Customer searches | Customer | `POST /api/quotes/search` `{"pickup":"Bengaluru","destination":"Hyderabad","distanceKm":570,"loadTons":20,"goodsType":"rice","tollCharges":1500,"loadingCharges":1000}` | 200 | At least 1 quote; the 12-tyre quote total = **₹33,280**; `minimumChargeApplied: false`. Save the `quoteId`. |
| 7 | Customer books instantly | Customer | `POST /api/bookings` `{"quoteId": <id from step 6>}` | 201 / 200 | Booking exists, status `PENDING`, `totalFare = 33280`, `platformFee = 0`; quote status becomes `BOOKED` |
| 8 | Owner accepts | Owner | `POST /api/bookings/{id}/accept` (or your Module 4 accept endpoint) | 200 | Status `CONFIRMED`; audit log gains `BOOKING_CONFIRMED` |
| 9 | Owner assigns a driver | Owner | `POST /api/bookings/{id}/assign-driver` `{"driverId": <id>}` | 200 | Status `DRIVER_ASSIGNED`; driver sees the trip (Module 5) |
| 10 | Driver / owner marks picked up | Driver or Owner | `POST /api/bookings/{id}/pickup` (or `POST /api/trips/{id}/pickup`) | 200 | Status `PICKED_UP`; truck becomes `ON_TRIP`; dashboard `trucksByStatus.ON_TRIP` goes up by 1, `AVAILABLE` down by 1 |
| 11 | In transit | Driver | `POST /api/bookings/{id}/in-transit` (or trip event) | 200 | Status `IN_TRANSIT`; `TripEvent` timeline gains a row (Module 5) |
| 12 | Delivery proof uploaded | Driver | `POST /api/bookings/{id}/delivery-proof` (Module 5 endpoint) | 200 | Proof stored; status still `IN_TRANSIT` until the next step |
| 13 | Delivered | Driver / Owner | `POST /api/bookings/{id}/deliver` | 200 | Status `DELIVERED` |
| 14 | COD confirmed | Owner or Admin | `POST /api/payments/{bookingId}/confirm-cod` (Module 6 endpoint) | 200 | Payment row `PAID` / confirmed; audit log gains `PAYMENT_CONFIRMED`; settlement row created (`gross 33280, platform fee 0, owner amount 33280`) |
| 15 | Settlement paid to owner | Admin | `POST /api/admin/settlements/{id}/pay` (Module 6 endpoint) | 200 | Settlement status `PAID`; audit log gains `SETTLEMENT_PAID`; dashboard `pendingSettlementsTotal` falls by ₹33,280 |
| 16 | Booking completed | Admin or Owner | `POST /api/bookings/{id}/complete` | 200 | Status `COMPLETED`, `completedAt` set; truck back to `AVAILABLE` |
| 17 | Admin dashboard moves | Admin | `GET /api/admin/dashboard` | 200 | `bookingsByStatus.COMPLETED` up by 1, `revenueThisMonth` up by **₹0 only** (not ₹33,280), `pendingSettlementsTotal` down as in step 15 |
| 18 | Audit log tells the story | Admin | `GET /api/admin/audit-logs/booking/{id}` | 200 | Lines in order: `BOOKING_CONFIRMED`, `BOOKING_DRIVER_ASSIGNED`, `BOOKING_PICKED_UP`, `BOOKING_IN_TRANSIT`, `BOOKING_DELIVERED`, `PAYMENT_CONFIRMED`, `SETTLEMENT_PAID`, `BOOKING_COMPLETED` |

> **Flag — endpoint names in steps 8–16:** Modules 4–6 may name these endpoints differently (`/confirm`, `/assign`, `/start-trip`, and so on). The status transitions and the order are the contract — if your Module 4 calls step 8 `POST /api/bookings/{id}/confirm`, use that name and change only the endpoint column in your own copy of this table. Do not change the order, and do not skip the audit-log line for any step.

### What to do if a step fails

| Symptom | Most likely cause | Fix |
| --- | --- | --- |
| 401 on every step after login | Token missing, expired, or not sent as `Authorization: Bearer <token>` | Log in again; copy the token exactly; check the header name and the word `Bearer` |
| 403 on a step | Wrong role for that endpoint (e.g. customer trying to accept a booking) | Log in as the role in the table; check the `@PreAuthorize` on that controller |
| 400 on search | `distanceKm` or `loadTons` outside the validated range (Module 3: 1–5000 km, 0.1–40 tons) | Fix the request values, not the validation |
| Empty quote list at step 6 | No `AVAILABLE` truck with `payloadTons >= 20`, or the rate for that category is inactive | Check step 4 — is the truck `AVAILABLE`? Check `GET /api/admin/rates` — is the 12-tyre rate active? |
| Fare is not ₹33,280 at step 6 | Rate, minimum-charge, or fee wrong | Re-check Module 3 Part 3 by hand: 570 × 2.70 × 20 = 30,780 + 1,500 + 1,000 + 0 = 33,280. If the code disagrees, trust the hand calculation. |
| 409 / error on step 7 (second booking) | No-double-booking rule: the truck is already on an active booking | That is correct behaviour — use a different truck, or complete / cancel the first booking. See Part 8, BookingServiceTest. |
| Dashboard revenue jumps by the full fare | Revenue query sums `totalFare` instead of `platformFee` | Go back to Part 3, File 2, and fix the one query line. |
| Dashboard numbers do not move after step 17 | `completedAt` is never set, so the revenue query's date filter excludes the booking | Set `completedAt = LocalDateTime.now()` in the Module 4 complete method, or switch the query to `updatedAt` (Part 3 flag). |
| Audit log is empty | `auditService.log(...)` was never wired into Modules 4/6 | Add the one-line calls in Part 6, "How to wire it". |
| Frontend shows the dashboard but the numbers are wrong | Frontend is reading a different field name than the DTO | Match the frontend to `AdminDashboardResponse` in Part 3, File 1 — do not change the backend DTO. |

---

## Part 8 — Full testing: unit, integration, security

### 8.1 What to test, and in what order

1. **Unit tests first** — fast, no database, no server. They prove the formula and the rules.
2. **Integration tests second** — with a real database, proving the flow works end to end.
3. **Security tests third** — proving the wrong role cannot do the wrong thing.
4. **Manual smoke test last** (Part 8.5) — with your own hands, in the browser and in Postman / curl, because an automated test cannot see a broken button.

### 8.2 Unit tests

**Choice for integration tests (pick one, stated plainly): H2 in-memory database.** The integration test below uses H2, not Testcontainers, because H2 needs no Docker and works on a beginner's laptop with zero setup. Testcontainers (real MySQL in Docker) is the more faithful choice for a production team — but it needs Docker installed and running, and it is slower. If you later install Docker, you can switch the integration test to Testcontainers by changing only the test configuration (see the note after the integration test); the test methods themselves do not change.

#### File 11 of 12 — `src/test/java/com/truckconnect/pricing/PricingServiceTest.java`

**Simple meaning:** the two worked examples from Module 3, as tests. If either number changes, the test fails and tells you the formula broke. These are the most valuable tests in the whole project.

```java
package com.truckconnect.pricing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    // Module 3, Example 1: 570 km, 20 tons, 12-tyre @ 2.70, toll 1500, loading 1000, fee 0.
    @Test
    void fullLoad_BengaluruToHyderabad_is33280() {
        PricingService.FareBreakdown fare = pricingService.calculate(
                new BigDecimal("570"), new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("2.70"), new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0.50"));

        assertEquals(new BigDecimal("20.00"), fare.chargeableTons());
        assertFalse(fare.minimumChargeApplied());
        assertEquals(new BigDecimal("30780.00"), fare.baseFare());
        assertEquals(new BigDecimal("33280.00"), fare.totalFare());
    }

    // Module 3, Example 2: same trip, 8-ton load -> minimum charge (half truck = 10 tons).
    @Test
    void halfEmptyLoad_appliesMinimumCharge_is17890() {
        PricingService.FareBreakdown fare = pricingService.calculate(
                new BigDecimal("570"), new BigDecimal("8"), new BigDecimal("20"),
                new BigDecimal("2.70"), new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0.50"));

        assertTrue(fare.minimumChargeApplied());
        assertEquals(new BigDecimal("10.00"), fare.chargeableTons());
        assertEquals(new BigDecimal("15390.00"), fare.baseFare());
        assertEquals(new BigDecimal("17890.00"), fare.totalFare());
    }

    // Module 3, Example 3: 14-tyre, 25 tons, 500 km @ 2.60 -> base 32500, total 35000.
    @Test
    void biggerTruck_fullLoad_is35000() {
        PricingService.FareBreakdown fare = pricingService.calculate(
                new BigDecimal("500"), new BigDecimal("25"), new BigDecimal("25"),
                new BigDecimal("2.60"), new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0.50"));

        assertEquals(new BigDecimal("32500.00"), fare.baseFare());
        assertEquals(new BigDecimal("35000.00"), fare.totalFare());
    }
}
```

Run it: in VS Code, right-click the file → **Run Java**, or in the terminal: `./mvnw test -Dtest=PricingServiceTest`. All 3 tests must pass. If one fails, the formula is wrong — fix `PricingService`, not the test.

#### File 12a of 12 — `src/test/java/com/truckconnect/booking/BookingServiceTest.java`

**Simple meaning:** the booking lifecycle is a set of allowed moves, like a board game. This test checks the two most important rules: (1) you can only move forward along the allowed path, and (2) one truck cannot be double-booked. It uses Mockito, so it needs no database.

```java
package com.truckconnect.booking;

import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock TruckRepository truckRepository;
    @InjectMocks BookingService bookingService;

    // Transition table: each row is (from, to, allowed?).
    // The full allowed path is:
    // PENDING -> CONFIRMED / REJECTED / CANCELLED
    // CONFIRMED -> DRIVER_ASSIGNED / CANCELLED
    // DRIVER_ASSIGNED -> PICKED_UP / CANCELLED
    // PICKED_UP -> IN_TRANSIT
    // IN_TRANSIT -> DELIVERED / DISPUTED
    // DELIVERED -> COMPLETED / DISPUTED
    // DISPUTED -> COMPLETED / CANCELLED   (admin only, Part 5)
    // COMPLETED, REJECTED, CANCELLED are terminal: nothing leaves them.
    @Test
    void transitionTable_completedBookingCannotMove() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> bookingService.changeStatus(1L, BookingStatus.CANCELLED, new User()));
    }

    @Test
    void transitionTable_pendingCanBeConfirmed() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Booking result = bookingService.changeStatus(1L, BookingStatus.CONFIRMED, new User());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void transitionTable_pendingCannotJumpToDelivered() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> bookingService.changeStatus(1L, BookingStatus.DELIVERED, new User()));
    }

    @Test
    void noDoubleBooking_truckWithActiveBookingCannotBeBookedAgain() {
        Truck truck = new Truck();
        truck.setId(7L);
        Booking active = new Booking();
        active.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findActiveByTruckId(7L)).thenReturn(List.of(active));

        assertThrows(IllegalStateException.class,
                () -> bookingService.createBooking(new User(), truck, null));
    }
}
```

> **Flag — adjust these two lines to your Module 4:** this test assumes your `BookingService` has methods `changeStatus(Long, BookingStatus, User)` and `createBooking(User, Truck, Quote)` (or similar), and that `BookingRepository` has a method `findActiveByTruckId(Long)` returning active bookings for a truck. If your Module 4 named them differently, change only the method names in this test to match yours — the *rules* being tested (terminal states are terminal, no skipping steps, no double-booking) do not change. If your Module 4 has no `findActiveByTruckId`, add it to `BookingRepository` as a `@Query` that selects bookings for a truck whose status is in (`PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, PAYMENT_PENDING`) — that list is the definition of "active" for this rule.

#### File 12b of 12 — `src/test/java/com/truckconnect/quote/SearchServiceTest.java`

```java
package com.truckconnect.quote;

import com.truckconnect.pricing.*;
import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.truck.*;
import com.truckconnect.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock TruckRepository truckRepository;
    @Mock PricingRateRepository rateRepository;
    @Mock PlatformSettingsRepository settingsRepository;
    @Mock QuoteRepository quoteRepository;
    @Mock PricingService pricingService;
    @InjectMocks SearchService searchService;

    private Truck truck(long id, String reg, int payload) {
        Truck t = new Truck();
        t.setId(id);
        t.setRegistrationNumber(reg);
        t.setPayloadTons(new BigDecimal(payload));
        t.setBodyType(BodyType.OPEN);
        TruckCategory category = new TruckCategory();
        category.setId(1L);
        category.setName("12 Tyre Truck");
        category.setTyreCount(12);
        category.setTargetPayloadTons(new BigDecimal("20"));
        t.setCategory(category);
        return t;
    }

    @Test
    void search_truckTooSmall_isNotReturned() {
        Truck small = truck(1L, "KA01AA0001", 10); // 10 tons cannot carry 20
        when(truckRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(small));
        when(settingsRepository.findById(1L))
                .thenReturn(Optional.of(new PlatformSettings()));

        SearchRequest req = new SearchRequest("Bengaluru", "Hyderabad",
                new BigDecimal("570"), new BigDecimal("20"), "rice",
                null, null, BigDecimal.ZERO, BigDecimal.ZERO);

        List<QuoteResponse> result = searchService.search(new User(), req);
        assertTrue(result.isEmpty(), "A 10-ton truck must never be offered a 20-ton load");
    }

    @Test
    void search_sortedCheapestFirst() {
        // Two matching trucks, different rates: the cheaper rate must come first.
        // (Pricing is mocked here; the real formula is already tested in PricingServiceTest.)
        Truck cheap = truck(1L, "KA01AA0001", 20);
        Truck dear = truck(2L, "KA01AA0002", 20);
        when(truckRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(dear, cheap)); // deliberately in the wrong order
        when(settingsRepository.findById(1L))
                .thenReturn(Optional.of(new PlatformSettings()));
        when(rateRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(Optional.of(new PricingRate(cheap.getCategory(),
                        new BigDecimal("2.70"))));
        when(pricingService.calculate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> new PricingService.FareBreakdown(
                        new BigDecimal("20"), false, new BigDecimal("30780.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0"),
                        new BigDecimal("30780.00")));
        when(quoteRepository.save(any())).thenAnswer(i -> {
            Quote q = i.getArgument(0);
            // Give each saved quote a fake id so the response can be built.
            // (If your Quote has setId, use it; otherwise this line is harmless.)
            return q;
        });

        SearchRequest req = new SearchRequest("Bengaluru", "Hyderabad",
                new BigDecimal("570"), new BigDecimal("20"), "rice",
                null, null, BigDecimal.ZERO, BigDecimal.ZERO);

        List<QuoteResponse> result = searchService.search(new User(), req);
        assertEquals(2, result.size());
        assertTrue(result.get(0).totalFare()
                .compareTo(result.get(1).totalFare()) <= 0,
                "Results must be sorted cheapest first");
    }
}
```

> **Flag:** this test assumes `TruckCategory` has `setName / setTyreCount / setTargetPayloadTons` and `Truck` has `setBodyType / setCategory / setPayloadTons / setRegistrationNumber`. If your Module 2 named any of these differently, change only those setter calls. If your `SearchService.search` returns quotes in a different order when totals tie, the second test still passes — it only checks cheapest first, not the tie-break.

#### File 12c of 12 — `src/test/java/com/truckconnect/admin/AdminDashboardServiceTest.java`

```java
package com.truckconnect.admin;

import com.truckconnect.admin.dto.AdminDashboardResponse;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.payment.OwnerSettlementRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock TruckRepository truckRepository;
    @Mock BookingRepository bookingRepository;
    @Mock OwnerSettlementRepository settlementRepository;
    @InjectMocks AdminDashboardService dashboardService;

    @Test
    void revenue_isPlatformFeeOnly_notTotalFare() {
        // 2 completed bookings this month, fee 0 each -> revenue 0.
        // Their total fares (33280 + 17890 = 51170) must NOT appear as revenue.
        when(userRepository.countByRole(any(Role.class))).thenReturn(0L);
        when(truckRepository.countByAvailabilityStatus(any(AvailabilityStatus.class)))
                .thenReturn(0L);
        when(bookingRepository.countByStatus(any(BookingStatus.class))).thenReturn(0L);
        when(bookingRepository.sumPlatformFeesSince(any()))
                .thenReturn(new BigDecimal("0.00"));
        when(settlementRepository.sumPendingSettlements())
                .thenReturn(new BigDecimal("51170.00"));

        AdminDashboardResponse dto = dashboardService.getDashboard();

        assertEquals(new BigDecimal("0.00"), dto.revenueThisMonth());
        assertEquals(new BigDecimal("51170.00"), dto.pendingSettlementsTotal());
        assertNotEquals(dto.revenueThisMonth(), dto.pendingSettlementsTotal(),
                "Revenue and owner money must never be the same number");
    }
}
```

> **Note:** this test mocks the repositories, so it proves the service passes the repository's answer through unchanged. The *query itself* (`SUM(platformFee)` vs `SUM(totalFare)`) is proven by the integration test below, against a real database.

### 8.3 Integration test — one flow, real database (H2)

Add H2 as a **test-only** dependency in `pom.xml` (inside `<dependencies>`, next to your other test dependencies). It is never used in production — the live database is still MySQL / TiDB Cloud.

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

Then create `src/test/resources/application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:truckbooking_test;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
```

And the test itself, `src/test/java/com/truckconnect/booking/BookingFlowIntegrationTest.java`:

```java
package com.truckconnect.booking;

import com.truckconnect.admin.AdminDashboardService;
import com.truckconnect.admin.dto.AdminDashboardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One booking, end to end, against a real (in-memory H2) database.
 * This test proves the dashboard revenue query sums platformFee, not totalFare,
 * and that a COMPLETED booking moves the dashboard counts.
 *
 * NOTE: H2 is used here because it needs no Docker. If you later install Docker
 * and want the test to run against real MySQL, switch to Testcontainers:
 * replace the H2 dependency with testcontainers + mysql, annotate the test
 * with @Testcontainers / @Container MySQLContainer, and set the datasource
 * URL from the container. The test methods below do not change.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired AdminDashboardService dashboardService;

    @Test
    void completedBooking_countsZeroRevenue_notFullFare() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPlatformFee(new BigDecimal("0"));
        booking.setTotalFare(new BigDecimal("33280"));
        booking.setCompletedAt(java.time.LocalDateTime.now());
        bookingRepository.save(booking);

        AdminDashboardResponse dto = dashboardService.getDashboard();

        assertEquals(new BigDecimal("0.00"), dto.revenueThisMonth(),
                "One completed booking must add exactly 0 to revenue, not 33280");
        assertEquals(1L, dto.bookingsByStatus().get("COMPLETED"));
        assertEquals(1L, dto.totalBookings());
    }
}
```

> **Flag:** this test sets fields directly on `Booking` (`setPlatformFee`, `setTotalFare`, `setCompletedAt`, `setStatus`). If your Module 4 `Booking` requires a customer / truck / quote to save (non-null foreign keys), create those rows first in the test with the repositories, or set the fields your entity requires — the assertions at the end do not change.

Run all tests: `./mvnw test`. Every test must pass before you deploy. If a test fails, fix the code, not the test — unless the flagged line in the test tells you the name differs, in which case fix the name.

### 8.4 Security tests — the 401 / 403 matrix

**Simple meaning:** for every endpoint, check what happens with (a) no token, (b) the wrong role's token, and (c) the right role's token. The expected answers are always the same: no token → 401, wrong role → 403, right role → 200 / 201. Test these with MockMvc or with Postman — the table is the same either way.

| Endpoint | No token | CUSTOMER | OWNER | DRIVER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| `GET /api/admin/dashboard` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/admin/users` | 401 | 403 | 403 | 403 | 200 |
| `PATCH /api/admin/users/{id}/deactivate` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/admin/disputes` | 401 | 403 | 403 | 403 | 200 |
| `POST /api/admin/disputes/{id}/resolve` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/admin/audit-logs` | 401 | 403 | 403 | 403 | 200 |
| `GET /api/admin/rates` | 401 | 403 | 403 | 403 | 200 |
| `POST /api/quotes/search` | 401 | 200 | 403 | 403 | 403 |
| `POST /api/bookings` | 401 | 201 / 200 | 403 | 403 | 403 |
| `POST /api/bookings/{id}/accept` | 401 | 403 | 200 (only the truck's owner) | 403 | 403 |

**Ownership isolation — test these by hand, they matter more than the matrix:**

1. Owner A tries to accept Owner B's booking → 403 (or 404 — either is safe, as long as it is not 200). The check is: the booking's truck belongs to the logged-in owner.
2. Customer A tries to open Customer B's booking (`GET /api/bookings/{id}`) → 403 / 404, never 200.
3. Customer A tries to book with Customer B's `quoteId` → 403 / 400, never 200. The quote belongs to the customer who searched.
4. Driver A tries to see Driver B's trip → 403 / 404, never 200.
5. An admin deactivating another admin → 400 (the `IllegalStateException` in Part 5), never 200. An admin deactivating themselves → 400, never 200.

If any of these returns 200, stop — that is a security bug, and it must be fixed before deployment, however small it looks.

---

### 8.5 Manual smoke-test checklist (at least 15 items — do these with your own hands)

You need: the app running locally, one ADMIN token, one CUSTOMER token, one OWNER token, and the demo data from Part 7. Tick each box only when you have seen the result yourself.

| # | Test | Expected result |
| --- | --- | --- |
| 1 | Start the app locally (Part 11 order). Check the logs. | No error on startup; `audit_logs`, `pricing_rates` (4 rows), `platform_settings` (1 row) exist. Restart — still 4 rates, no duplicates. |
| 2 | `GET /api/admin/dashboard` as ADMIN. | 200, with all 8 fields from Part 3. `revenueThisMonth` is ₹0.00 (the platform fee is ₹0), never a fare total. |
| 3 | Same request as CUSTOMER, then as OWNER, then with no token. | 403, 403, 401 — in that order. |
| 4 | Search as CUSTOMER: Bengaluru→Hyderabad, 570 km, 20 tons. | Quote total ₹33,280 for the 12-tyre truck; cheapest first. |
| 5 | Search with load 8 tons on the same truck. | `minimumChargeApplied: true`, total ₹17,890. |
| 6 | Book the ₹33,280 quote as CUSTOMER. | Booking created, status `PENDING`, `platformFee = 0`, `totalFare = 33280`. |
| 7 | Try to book the same quote again. | Error (quote already `BOOKED`) — a quote can be booked only once. |
| 8 | Accept the booking as the truck's OWNER. | Status `CONFIRMED`. Try as a different owner — 403 / 404, never 200. |
| 9 | Assign a driver, mark picked up, in transit, deliver with proof. | Each step moves the status forward; the truck shows `ON_TRIP` while moving and `AVAILABLE` again after completion. |
| 10 | Confirm COD, pay the settlement, complete the booking. | Settlement `PAID`; booking `COMPLETED`; `completedAt` is set. |
| 11 | `GET /api/admin/dashboard` again as ADMIN. | `COMPLETED` up by 1, `revenueThisMonth` up by exactly ₹0, `pendingSettlementsTotal` down by the owner amount. |
| 12 | `GET /api/admin/audit-logs/booking/{id}` as ADMIN. | The full story in order, newest first — at least the 8 lines from Part 7, step 18. |
| 13 | Create a dispute: set a booking to `DISPUTED`, then resolve it as ADMIN with a note. | `GET /api/admin/disputes` lists it before, and not after; status becomes `COMPLETED` or `CANCELLED` only. Try resolving it a second time — error, never 200. |
| 14 | Deactivate a test CUSTOMER as ADMIN, then try to log in as them. | Login fails (403 / 401). Reactivate them — login works again. Try deactivating another ADMIN — error, never 200. |
| 15 | Change the 12-tyre rate to ₹2.80 as ADMIN, search again, change it back to ₹2.70. | New quotes use ₹2.80; old quotes and old bookings still show ₹2.70 / ₹33,280. |
| 16 | Open the frontend (Module 7) as CUSTOMER, OWNER, and ADMIN in turn. | Customer sees search + quote + book; owner sees trucks + bookings + call/chat; admin sees the dashboard numbers from step 11. No role sees another role's page. |
| 17 | In the frontend, complete one full booking without touching Postman. | Every step works from the screen, including the owner call/chat button. If a button does nothing, the frontend is calling the wrong endpoint — check Part 7. |
| 18 | Stop the backend, restart it, log in again. | All data is still there (MySQL, not memory); the dashboard numbers are unchanged. |

### Common mistakes

- **Summing `totalFare` as revenue.** The single most expensive mistake in this module. Revenue is `SUM(platformFee)` on `COMPLETED` bookings. If revenue looks like a fare, re-read Part 3, File 2.
- **Forgetting `completedAt`.** Without it, the "this month" filter has nothing to filter on, and revenue stays ₹0 forever — or counts bookings from last year. Set it in the Module 4 complete method.
- **Deleting a user instead of deactivating.** Deletion orphans bookings and breaks the dashboard counts. Deactivate, keep the history.
- **Letting a dispute go back to `IN_TRANSIT`.** A disputed trip is over. The only exits are `COMPLETED` or `CANCELLED`.
- **Writing audit lines by hand in the controller instead of in the service.** If the status changes in the service but the log line is in the controller, a second caller (another endpoint, a job) will change the status without logging it. Log next to the `setStatus`, in the service.
- **Testing only as ADMIN.** The security bugs hide in the other roles. Run the Part 8.4 matrix for every new endpoint, every time.
- **Changing the test to match broken code.** If `PricingServiceTest` says ₹33,280 and the code says something else, the code is wrong. The hand calculation in Module 3 is the truth.

---

## Part 9 — Free deployment guide, step by step (₹0 total)

**Read this before you start:** this is a guide only. Nothing is deployed by this module, no account is created by this guide, and no sign-in is done for you. You will sign in to GitHub, TiDB Cloud, Render, and Vercel yourself, in your own browser, and you will type your own passwords and copy your own database password into Render yourself — never in chat, never in a file that goes to GitHub. Total cost: **₹0** on every free tier below. The only cost is time, and the free tiers have limits (below) that are fine for a demo and a first launch, but not for heavy traffic.

**Order matters — do not skip ahead:** GitHub first (the code has to live somewhere), then TiDB Cloud (the database has to exist before the backend can connect), then Render (the backend), then Vercel (the frontend needs the backend's URL), then verify.

### 9.0 What you need before you start

- A GitHub account (you already have one: `Chandan9153` — use it).
- The finished project in VS Code, with all tests passing (`./mvnw test` green).
- A TiDB Cloud account — free Starter tier, no card required for the free tier (same approach as ShopKart).
- A Render account and a Vercel account — both free, both can sign in with GitHub.

### 9.1 Step A — Push the code to GitHub (repo name: `truck-booking`)

**File 10 of 12 — `.gitignore` additions (do this before your first push).** If your project already has a `.gitignore`, add these lines to it. If it does not, create one in the project root.

```gitignore
# Secrets — never commit these. Ever.
.env
application-prod.properties
*.pem
*.key

# Build output and IDE files
target/
node_modules/
dist/
.idea/
*.iml
.DS_Store
```

Why this matters: `application-prod.properties` will hold your real database password on Render via environment variables, not in a file — but if you ever put a password in a properties file by mistake, this `.gitignore` is the last line of defence. Check before every push with `git status`: if you see `.env` or a properties file with a password in it, do not push. Remove it, commit the `.gitignore` fix, and push again.

Then, in VS Code's terminal, in the project root:

```bash
git init
git add .
git status        # READ this list. No .env, no password file, no target/ folder.
git commit -m "Truck Booking - Modules 1-8 complete"
git branch -M main
git remote add origin https://github.com/Chandan9153/truck-booking.git
git push -u origin main
```

> **Flag:** if the repo `Chandan9153/truck-booking` does not exist yet, create it first on github.com (New repository → name `truck-booking` → Public → **do not** tick "Add a README" if you already have code locally, or the push will conflict). If GitHub asks you to sign in, sign in yourself in the browser — do not paste a password or a token in chat.

### 9.2 Step B — TiDB Cloud free MySQL (create database `truck_booking`)

1. Sign in to TiDB Cloud yourself (tidbcloud.com). Create a free Starter cluster — ₹0/month, the same free tier used for ShopKart. Pick the region closest to India (Singapore, `ap-southeast-1`, is the usual choice).
2. Wait until the cluster status is **Active**.
3. In the cluster, create a database named exactly `truck_booking`.
4. Create a database user (or use the default) and **copy the host, port (4000), username, and password into a safe place only you can see** — for example a password manager, or a piece of paper. You will paste them into Render in Step C. **Never paste them in chat, never put them in a file that goes to GitHub, never put them in this guide.**
5. Note the connection details: host looks like `gateway01.ap-southeast-1.prod.aws.tidbcloud.com`, port `4000`, database `truck_booking`. Your host will be different — use the one TiDB Cloud shows you.

Free-tier limits to know: the Starter tier is free but small (limited storage and connections). It is enough for a demo and early users. If you outgrow it, TiDB will tell you — do not upgrade on a whim; the app works the same on the free tier.

### 9.3 Step C — Render backend (free web service)

**Build and start commands — this guide uses Render's build/start commands, not a Dockerfile.** Be consistent: do not add a Dockerfile as well, or Render will get confused about which one to use. If you already have a `Dockerfile` in the project, delete it or leave it out of the repo for this deployment.

1. Sign in to Render yourself (render.com, "Sign in with GitHub" is easiest).
2. **New → Web Service → Build and deploy from a Git repository** → pick `Chandan9153/truck-booking`.
3. Fill in exactly this:

| Field | Value |
| --- | --- |
| Name | `truck-booking-backend` |
| Region | Singapore (closest to TiDB) — or Oregon if Singapore is not offered on free |
| Branch | `main` |
| Root Directory | leave blank (backend is at the repo root; the frontend lives in `frontend/`) |
| Runtime | Java |
| Build Command | `./mvnw clean package -DskipTests` |
| Start Command | `java -jar target/truck-booking-0.0.1-SNAPSHOT.jar` |

> **Flag — check the jar name:** the start command assumes your `pom.xml` has `<artifactId>truck-booking</artifactId>` and `<version>0.0.1-SNAPSHOT</version>`, so Maven builds `target/truck-booking-0.0.1-SNAPSHOT.jar`. If your `pom.xml` says something else, change only the jar name in the start command to match what is actually in your `target/` folder after a local `./mvnw clean package -DskipTests`.

4. **Environment variables** — click **Environment** and add these, one by one. Type the values yourself. The database values come from Step B; the JWT secret is a long random string you invent (at least 32 characters — for example, generate one in your password manager; never reuse a password from another site).

| Key | Value (what to type) |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:mysql://<your-tidb-host>:4000/truck_booking?sslMode=VERIFY_IDENTITY` — replace `<your-tidb-host>` with the host TiDB Cloud showed you |
| `DATABASE_USERNAME` | your TiDB database username |
| `DATABASE_PASSWORD` | your TiDB database password (paste it here, in Render, never in chat) |
| `JWT_SECRET` | your long random secret (at least 32 characters) |
| `PORT` | Render sets this itself — do **not** set it by hand unless Render asks you to |

5. Click **Create Web Service**. The first build takes a few minutes. Watch the logs: you want to see the app start and the seeders run (4 pricing rates, 1 settings row) with no database-connection error.
6. Your backend URL will look like `https://truck-booking-backend.onrender.com` — your exact name will differ. **Do not guess it and do not copy a URL from this guide: there is no live URL in this file.** Use the URL Render shows you.

**Free-tier warning (important, and normal):** the Render free instance **sleeps when idle**. The first request after a sleep can take **~50 seconds**. That is not a bug and not a failure — wait, and the request will answer. If you test the live site and the first page takes a minute, that is the free tier waking up. Paid Render does not sleep; do not pay for it until real users need it.

### 9.4 The profiles: `application.properties` and `application-prod.properties`

**File 9 of 12 — `src/main/resources/application.properties`** (local development — this file **is** committed to GitHub; it holds no secret):

```properties
spring.application.name=truck-booking
server.port=8080

# Local MySQL (your laptop). Change the password only on your own laptop.
spring.datasource.url=jdbc:mysql://localhost:3306/truck_booking?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=YOUR_LOCAL_MYSQL_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# JWT (local only — the real secret lives in Render, never here)
app.jwt.secret=CHANGE_ME_LOCAL_ONLY_AT_LEAST_32_CHARACTERS_LONG
app.jwt.expiration-ms=900000
```

> **Note:** replace `YOUR_LOCAL_MYSQL_PASSWORD` with your own laptop's MySQL password. That password lives only on your laptop and never goes to GitHub — if you are nervous, move it to an `.env` file instead and keep the `.gitignore` line from Step A. The value `CHANGE_ME_LOCAL_ONLY...` is a placeholder for local testing, not a real secret.

**File 9 (continued) — `src/main/resources/application-prod.properties`** (production — this file is **not** committed to GitHub; it is listed in `.gitignore` in Step A, and Render supplies the values as environment variables instead):

```properties
server.port=${PORT:8080}

spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=900000
```

**Simple meaning:** `application.properties` is "how the app runs on my laptop". `application-prod.properties` is "how the app runs on Render" — and every real value in it comes from Render's Environment tab, not from the file. That is why the file can be in `.gitignore` and the app still works live: Render injects the secrets at runtime. If you ever see a real password inside a `.properties` file, that file must not be pushed — stop and fix the `.gitignore` first.

**File 10 (continued) — `render.yaml` (optional).** If you want Render to be set up from code instead of clicking, add this file to the repo root. It does the same thing as Step C's form — same build command, same start command, same environment variable names (with values still typed in Render, never in this file). If you use it, do not also fill in the form differently; one source of truth.

```yaml
services:
  - type: web
    name: truck-booking-backend
    runtime: java
    buildCommand: ./mvnw clean package -DskipTests
    startCommand: java -jar target/truck-booking-0.0.1-SNAPSHOT.jar
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        sync: false
      - key: DATABASE_USERNAME
        sync: false
      - key: DATABASE_PASSWORD
        sync: false
      - key: JWT_SECRET
        generateValue: true
```

`sync: false` means "ask me for the value in Render, do not read it from this file" — that is the point. `generateValue: true` on `JWT_SECRET` lets Render invent a random secret for you, which is safer than inventing one yourself.

### 9.5 Step D — Vercel frontend (import the repo, Root Directory `frontend`)

1. Sign in to Vercel yourself (vercel.com, "Continue with GitHub" is easiest). If Vercel asks for a 2FA code, enter it yourself in the browser — do not share it in chat.
2. **Add New → Project → Import** `Chandan9153/truck-booking`.
3. Fill in exactly this:

| Field | Value |
| --- | --- |
| Framework Preset | Vite (or React — Vercel usually detects it) |
| Root Directory | `frontend` |
| Build Command | `npm run build` (Vercel's default for Vite) |
| Output Directory | `dist` (Vercel's default for Vite) |
| Environment Variable `VITE_API_BASE_URL` | Your Render URL + `/api` — for example `https://truck-booking-backend.onrender.com/api` (use **your** Render URL from Step C, not this example) |

4. Click **Deploy**. The first deploy takes a few minutes.
5. Your frontend URL will look like `https://truck-booking.vercel.app` — again, use the URL Vercel shows you, not a guessed one.

> **Flag — check the variable name:** this assumes your Module 7 frontend reads the backend address from `import.meta.env.VITE_API_BASE_URL`. If your Module 7 used a different name (for example `VITE_API_URL`), use that name in Vercel instead — the value is the same (Render URL + `/api`). Do not change the frontend code to match this guide; change the variable name to match the frontend.

### 9.6 Step E — Verify live (do this, do not assume it works)

| # | Check | Expected result |
| --- | --- | --- |
| 1 | Open your Render URL + `/actuator/health` in the browser (if Module 1 added Spring Actuator; otherwise open the backend root URL). | `{"status":"UP"}` (or the backend's welcome / 404 page — either proves the server is awake). Remember the first request can take ~50s on the free tier. |
| 2 | Open your Vercel URL. | The Truck Booking home page loads — search box, categories, no blank page. |
| 3 | Register a new customer on the live site, log in. | 200, token issued, dashboard / search page opens. |
| 4 | Search live: Bengaluru→Hyderabad, 570 km, 20 tons. | A quote appears. If the database is empty, there will be no trucks — that is correct, not a bug: add a truck first (Step 5). |
| 5 | Log in as an owner on the live site, add a truck (12-tyre, 20 tons), search again as the customer. | Quote total ₹33,280. Book it. |
| 6 | Log in as ADMIN on the live site, open the admin dashboard page. | The counts move: 1 booking; `revenueThisMonth` stays ₹0 (the platform fee is ₹0) even after the booking is COMPLETED. |
| 7 | Leave the site alone for 15 minutes, open it again. | First load is slow (~50s) — the free tier woke up. That is normal. |

If any step fails live but passes locally, the difference is almost always one of three things: (a) the environment variable is wrong or missing in Render / Vercel, (b) the TiDB password was pasted wrong, or (c) the frontend is pointing at `localhost:8080` instead of the Render URL. Check those three first, in that order.

---

## Part 10 — Go-live checklist + what is NOT done yet

### Go-live checklist (tick each box before you tell anyone the link)

- [ ] All tests green locally: `./mvnw test` passes (Part 8).
- [ ] Manual smoke test done locally: all 18 items in Part 8.5 ticked.
- [ ] `.gitignore` checked: `git status` shows no `.env`, no password file, no `target/`.
- [ ] Code pushed to GitHub repo `truck-booking`, branch `main`.
- [ ] TiDB Cloud cluster Active, database `truck_booking` exists.
- [ ] Render backend deployed, logs show a clean start, health endpoint answers.
- [ ] Vercel frontend deployed, `VITE_API_BASE_URL` points at the Render URL + `/api`.
- [ ] Live verification done: register, search, quote ₹33,280, book, admin dashboard moves (Part 9.6).
- [ ] One full live booking completed end to end (Part 7, steps 1–18, on the live URLs).
- [ ] Admin login works live, and a customer login cannot open `/api/admin/dashboard` (403).

### What is NOT done yet — and what each one would take next

| Not done yet | What it is now | What it would take next |
| --- | --- | --- |
| Real Razorpay gateway | COD is the default; payments are confirmed by hand (Module 6) | Create a Razorpay account, add its key/secret as Render env vars (never in code), add a `POST /api/payments/razorpay/order` endpoint and a webhook to confirm payment, keep COD as a fallback. About one module of work. |
| Google Maps live distance | Customer types the distance in km (Module 3) | A Google Cloud account with billing + Maps Distance Matrix API key, a `DistanceService` that fills `distanceKm` automatically, and the key as an env var. The pricing formula does not change. |
| Driver self-service login | Drivers are created / assigned by the owner (Module 5) | A driver registration + login flow in Module 1 (role `DRIVER`), and a driver app screen in Module 7. The booking and trip code does not change. |
| Real-time chat (owner call / chat) | Polling only in the MVP — the frontend re-asks the server every few seconds | WebSockets (Spring + STOMP) or a hosted chat service, plus a `messages` table. Polling works for a demo; real-time is a polish step, not a launch blocker. |
| Push notifications | None — the user must open the app to see a status change | Firebase Cloud Messaging (free tier) + a `device_token` on the user, and a send on each booking status change. |
| Mobile app (Android / iOS) | React web app only, usable in a phone browser | React Native (reusing the same API) or a PWA wrapper around the existing frontend. The backend does not change. |

None of these blocks the launch. Launch with COD, typed distance, and polling — then add them one at a time, in the order your users ask for them.

---

## Part 11 — Project complete: what to do next in VS Code

**Run locally first — in this order, every time:**

1. **MySQL first.** Start MySQL on your laptop. Make sure the database `truck_booking` exists (it is created automatically if `createDatabaseIfNotExist=true` is in your local properties, Part 9.4). If MySQL is not running, the backend will fail to start — that is the most common "it worked yesterday" bug, and the fix is always "start MySQL".
2. **Backend second, on 8080.** In VS Code, open the backend folder, then in the terminal:
   ```bash
   ./mvnw spring-boot:run
   ```
   Wait for "Started TruckBookingApplication" in the logs. Check the seeders ran: 4 pricing rates, 1 settings row. Open `http://localhost:8080/api/admin/rates` with an ADMIN token — you should see the 4 rates.
3. **Frontend third, on 5173.** In VS Code, open a second terminal, go into the `frontend/` folder:
   ```bash
   npm install
   npm run dev
   ```
   Open `http://localhost:5173` in your browser. The home page loads, search works, and the admin page calls the local backend on 8080.
4. **Run the tests.** In a third terminal (or in VS Code's Testing panel):
   ```bash
   ./mvnw test
   ```
   All tests green before you change anything else.
5. **Do one full booking locally** (Part 7, steps 1–18) before you touch deployment. If it works locally and fails live, the difference is in Part 9 (environment variables), not in your code.

**After it works locally:** follow Part 9 in order — GitHub, TiDB Cloud, Render, Vercel, verify. Then tell one real user to try it, watch what confuses them, and fix that one thing before adding any feature from Part 10.

**What you have built, in one paragraph:** a full truck-booking marketplace — registration and login with roles and JWT, slim truck registration, search and honest per-ton-per-km pricing, instant booking with owner accept and driver assignment, trips with delivery proof, COD payments with owner settlements, React screens for every role, an admin dashboard that tells the truth about revenue (₹0 in this MVP, because the platform fee is ₹0 — never the fare), an audit log that remembers everything, a test suite that proves the price and the rules, and a free deployment that costs ₹0. That is a complete project. The next step is not more code — it is one real booking, by one real customer, on the live site.

---

## File list — everything in Module 8 (for your VS Code checklist)

- `admin/dto/AdminDashboardResponse.java`
- `admin/AdminDashboardService.java`
- `admin/AdminDashboardController.java`
- `admin/dto/AdminUserResponse.java`
- `admin/AdminUserController.java`
- `admin/dto/DisputeResolveRequest.java`
- `admin/DisputeController.java`
- `admin/AuditLog.java`
- `admin/AuditLogRepository.java`
- `admin/AuditService.java`
- `admin/AuditLogController.java`
- `src/main/resources/application.properties` (local — committed, no secret)
- `src/main/resources/application-prod.properties` (production — in `.gitignore`, values from Render)
- `render.yaml` (optional)
- `.gitignore` (additions — Step A)
- `src/test/java/com/truckconnect/pricing/PricingServiceTest.java`
- `src/test/java/com/truckconnect/booking/BookingServiceTest.java`
- `src/test/java/com/truckconnect/quote/SearchServiceTest.java`
- `src/test/java/com/truckconnect/admin/AdminDashboardServiceTest.java`
- `src/test/java/com/truckconnect/booking/BookingFlowIntegrationTest.java`
- `src/test/resources/application-test.properties`

Plus one-line additions to existing repositories (`UserRepository`, `TruckRepository`, `BookingRepository`, `OwnerSettlementRepository`) and one-line audit calls in Modules 4 and 6. No new dependency except H2 for tests.

*End of Module 8 — and end of the Truck Booking build. Next: run it locally (Part 11), deploy it free (Part 9), and get one real booking.*
