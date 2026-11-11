# Truck Booking — Module 3: Search, Matching & Pricing

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1 and 2 keep working; rename the package later only if you want to)
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8
**Builds on:** Module 1 — Authentication & Security (roles, JWT) · Module 2 — Truck Categories & Registration (slim version, see Part 0)
**This module only:** Search trucks, match the right truck to a load, and calculate a realistic price in rupees per ton per kilometre. No booking creation, no payment, no tracking yet — Module 4 turns a quote from this module into a booking.

## How to use this guide

1. Read Part 0 first — it corrects Module 2 to the slim registration you asked for. Do those deletions before you add Module 3 code, or Module 3 will look for fields that should not be there.
2. Read Parts 1 to 4 so you understand *why* the code is shaped this way.
3. Add one file at a time in VS Code, in the order given in Part 5.
4. Start the app, seed the rates, and work through the test checklist in Part 9.
5. Module 1 must already be working — you need a login token before you can test anything here.

> **The one rule of this module:** a price is a **quote, not a promise**. The number this module calculates is an estimate from distance, load and the rate table. The final price can change if the owner negotiates or the route changes — Module 4 will record the agreed price on the booking. Never show a Module 3 number to a customer as a final, fixed fare.

---

## Part 0 — Correction to Module 2: slim registration (do this first)

On 11 Nov 2026 you corrected Module 2: truck registration must stay minimal — **only the details needed to book a truck** (tonnage, tyre count / category, and per-km rate). No owner documents, no insurance, no fitness/permit paperwork in the app.

Module 2 as first delivered was too heavy: it added RC / insurance / fitness / permit documents and an admin paper-check. That contradicts your correction, so fix Module 2 like this before Module 3:

### Delete these files from Module 2

| File | Why it goes |
| --- | --- |
| `DocumentType.java` | No document types any more |
| `TruckDocument.java` | No `truck_documents` table |
| `TruckDocumentRepository.java` | Nothing to query |
| `TruckDocumentRequest.java` and `TruckDocumentResponse.java` (DTOs) | No document API |
| `POST /api/trucks/{id}/documents` endpoint in `TruckController` | No document upload |

### Change these in the remaining Module 2 code

1. **`Truck` entity:** delete the `documents` list field and its getter/setter. Delete `verificationStatus` too — there is no paper check left to verify, so there is nothing to approve. Keep only `availabilityStatus` (`AVAILABLE` / `UNAVAILABLE` / `ON_TRIP`). A truck is bookable when its owner marks it `AVAILABLE`.
2. **`AdminTruckController`:** delete the approve / reject endpoints. Admin still manages categories (Module 2) and rates (this module) — that is the admin's job now, not checking papers.
3. **Registration form — the only fields an owner fills:** `registrationNumber`, `categoryId` (this gives tyre count and target tonnage), `bodyType`, `payloadTons` (the tonnage the owner declares the truck can carry), `ratePerKm` is **not** entered by the owner any more — the rate comes from this module's rate table (Part 3), so every truck in a category is priced the same way and customers can compare fairly.
4. **The payload rule stays, in slim form:** eligibility is decided by the truck's own `payloadTons`, never by tyre count alone. A 12-tyre truck declared at 18 tons must not be given a 20-ton load, even though its category target says 20. The difference from Module 2 is only that nobody checks papers — the owner declares the number and the customer sees it.

### What Module 3 assumes from now on

- `Truck` has: `id`, `registrationNumber`, `category` (with `tyreCount` and `targetPayloadTons`), `owner`, `bodyType`, `payloadTons`, `availabilityStatus`.
- A truck is matchable when `availabilityStatus = AVAILABLE`.
- If you kept Module 2 exactly as first delivered and do **not** want to slim it, add `AND verificationStatus = APPROVED` to the search query in Part 7 — one line, shown there as a note. But the slim version above is what you asked for, so use it.

---

## Part 1 — What this module does, and why it comes third

### What this module does

Module 1 answered *who are you?* Module 2 answered *what trucks exist?* Module 3 answers *which truck fits my load, and what will it cost?*

This module gives Truck Booking:

- A `pricing_rates` table — one rate per truck category, stored as data in MySQL, edited by an admin, never hardcoded in Java
- A search endpoint: a customer enters pickup, destination, distance, load weight, goods type — and gets a list of trucks that can actually carry that load, cheapest first
- A matching rule: a truck matches only if its payload is big enough, its body fits the goods, and it is available
- A price calculator: distance × rate per ton per km × load weight, plus extras and a platform fee, returned as a clear breakdown — never just one mystery number
- A `quotes` table — every price shown is saved, so Module 4 can turn a quote into a booking without recalculating or letting anyone change the price afterwards

### Why it comes third

Search and pricing need real trucks to search among. If Module 3 came before Module 2, there would be nothing to match and no payload to compare against the load. And booking (Module 4) needs a price first — a customer should see the fare *before* they commit, not after.

### What this module does not do

- No booking creation — a quote is not a booking; Module 4 creates the booking from a quote
- No owner accept / reject — that is the booking lifecycle in Module 4
- No payment — payments come after booking
- No live distance from Google Maps — the customer enters the distance in km for now (Part 2 explains why, and how Maps plugs in later)
- No GPS tracking — tracking comes after booking

---

## Part 2 — The big picture in simple words

Think of Truck Booking like a truck stand with a rate board.

1. **The rate board.** On the wall there is a board: 12-tyre truck — ₹2.70 per ton per km, 14-tyre — ₹2.60, and so on. The board is the `pricing_rates` table. Only the admin can change the board; the code only reads it.
2. **The customer arrives with a load.** "I have 20 tons of rice, Bengaluru to Hyderabad, about 570 km." That is the search request.
3. **The stand manager filters the trucks.** He walks past every truck and keeps only the ones that (a) can carry 20 tons, (b) have the right body for rice, and (c) are free right now. That is matching.
4. **He prices each truck left.** For each one: distance × rate × load, plus toll and loading, plus the platform fee. That is the quote.
5. **He shows the list cheapest first.** The customer picks one — in Module 4, not here.

### The flow, step by step

```text
CUSTOMER logs in (Module 1 JWT)
  -> POST /api/quotes/search
       { pickup, destination, distanceKm, loadTons, goodsType, bodyType? }
  -> server: find AVAILABLE trucks where payloadTons >= loadTons
  -> server: price each one from the rate table
  -> returns list of quotes, cheapest first (no booking created yet)

ADMIN logs in (Module 1 JWT)
  -> GET  /api/admin/rates            sees the rate board
  -> PUT  /api/admin/rates/{id}      changes one rate (e.g. diesel went up)

Later (Module 4):
  -> POST /api/bookings { quoteId }   turns one saved quote into a booking
```

### Who can do what

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | :---: | :---: | :---: | :---: | :--- |
| Search trucks and get quotes | Yes | No | No | No | No |
| See my own quotes | Yes | No | No | No | Yes, all |
| Change a rate | No | No | No | No | Yes |
| See the rate board | Yes (read-only, inside each quote) | Yes (read-only) | No | No | Yes |

---

## Part 3 — Pricing: rupees per ton per kilometre

### The formula you asked for

You asked for the price to be fixed realistically as **rupees per kilometre per ton** — checked against the internet, not a flat per-km rate. So the formula is:

```text
baseFare    = distanceKm × ratePerTonKm × chargeableTons
totalFare   = baseFare + tollCharges + loadingCharges + platformFee
```

- `distanceKm` — entered by the customer (from Google Maps or the route board), in whole kilometres.
- `ratePerTonKm` — from the rate table, by the truck's category.
- `chargeableTons` — usually the customer's actual load. But see the minimum-charge rule below.
- `tollCharges` and `loadingCharges` — entered by the customer as estimates in this MVP (0 if unknown). They are shown separately so the customer sees what they are paying for.
- `platformFee` — **₹0 per booking** (changed 11 Nov 2026 — the user decided the platform fee must be ₹0; it was ₹500 in the PRD worked example). It is a value in the database, not in code, so the admin can change it without a redeploy.

### The minimum-charge rule (important, and honest)

A full truck load (FTL) hires the **whole truck for the whole trip**, even if it is half empty — the diesel, toll and driver cost the same. So charging strictly by actual tons would let a 2-ton load hire a 20-ton truck for almost nothing, and no owner would accept that job.

So this module charges for the load **or half the truck's capacity, whichever is bigger**:

```text
chargeableTons = max(loadTons, payloadTons × 0.5)
```

Example: a 20-ton truck, 8-ton load → chargeable tons = max(8, 10) = 10 tons. The customer sees this in the quote breakdown as "minimum charge (half truck)". A full load never pays the minimum — it pays for its real tons.

### The rates — checked against the internet on 11 Nov 2026

These are **seed values for development**, not a promise of a real market price on any one route. They come from two public sources checked today:

- **Trukky route estimates (Nov 2026):** a 25-ton / 12-tyre truck, 20-ton load, costs about ₹54–57 per km on long routes (for example Jabalpur→Mumbai 1,100 km: ₹59,379–62,504; Delhi→Jalandhar 386 km: ₹20,829–21,925; Rajkot→Indore 594 km: ₹32,073–33,761). ₹54 ÷ 20 tons = **₹2.70 per ton per km**. Sources: https://www.trukky.com/jabalpur-to-mumbai, https://www.trukky.com/delhi-to-jalandhar, https://www.trukky.com/rajkot-to-indore
- **Onroadcost rate list (2026):** a 32-ft multi-axle truck (20–26 tons) costs ₹45–60 per km; a trailer (28–35 tons) ₹55–75 per km. Source: https://www.onroadcost.com/blog/truck-transport-charges-per-km-india

That gives the seed rate table. Notice the pattern: a bigger truck costs **more per km** but **less per ton per km**, because the same trip cost is spread over more tons. That is why the rate falls as the truck gets bigger.

| Category | Tyres | Target payload | Rate per ton per km | Full-truck rate per km (rate × payload) |
| --- | ---: | ---: | ---: | ---: |
| 12 Tyre Truck | 12 | 20 tons | **₹2.70** | ₹54/km |
| 14 Tyre Truck | 14 | 25 tons | **₹2.60** | ₹65/km |
| 18 Tyre Truck | 18 | 30 tons | **₹2.50** | ₹75/km |
| 22 Tyre Truck | 22 | 36 tons | **₹2.40** | ₹86/km |

Compare with your PRD's first draft (₹35/₹42/₹50/₹60 per km): those old per-km rates were too low — a real 12-tyre truck costs about ₹54/km, not ₹35/km. The PRD's worked example (500 km, 12-tyre = ₹20,500) is therefore replaced by the worked examples below. Keep the old PRD page as history; the rate table is the truth now.

### Worked examples (check these by hand)

**Example 1 — Bengaluru → Hyderabad, full load (the corridor from your PRD)**
- Truck: 12-tyre, 20-ton load, distance 570 km, rate ₹2.70
- baseFare = 570 × 2.70 × 20 = **₹30,780**
- toll ₹1,500 + loading ₹1,000 + platform fee ₹0 = ₹2,500
- **totalFare = ₹33,280**

**Example 2 — Same truck, half-empty load (minimum charge)**
- Load 8 tons, same 570 km: raw = 570 × 2.70 × 8 = ₹12,312
- Minimum = 570 × 2.70 × (20 × 0.5 = 10) = **₹15,390** — this is what is charged
- totalFare = 15,390 + 1,500 + 1,000 + 0 = **₹17,890**, and the quote says why.

**Example 3 — Bigger truck, full load**
- Truck: 14-tyre, 25-ton load, 500 km, rate ₹2.60
- baseFare = 500 × 2.60 × 25 = **₹32,500**; total with the same extras = **₹35,000**

### Why the customer enters the distance (for now)

A live distance needs the Google Maps Distance Matrix API, which needs a paid API key and billing — wrong for an MVP that must cost ₹0 to deploy. So in this module the customer types the distance (they can read it off Google Maps in their own browser, free). The server validates it (`1` to `5000` km) but does not fetch it. When you are ready, Module 5 (or a later polish step) can add a `DistanceService` that calls Maps and fills this field automatically — the formula and the tables do not change.

---

## Part 4 — Matching: which trucks fit a load

A truck matches a search only if **all** of these are true:

1. `availabilityStatus = AVAILABLE` — it is free right now.
2. `payloadTons >= loadTons` — it can carry the weight. This is the truck's own declared tonnage, not the category target.
3. If the customer asked for a body type (for example `REFRIGERATED` for medicine), `bodyType` must match. If they did not ask, any body type matches.
4. If the customer picked a category, the truck must be in that category. If they did not pick one, every category is considered.

Then the results are **sorted**: cheapest total fare first; if two trucks cost the same, the one whose payload is closest to the load comes first (less empty space wasted). The response also says, for each truck, *why* it matched — its payload, its rate, and the fare breakdown — so the customer is never asked to trust a single number.

Trucks that do **not** match are never returned, and the response never says "0 trucks found, here are some almost-fitting ones". If nothing matches, the customer gets an empty list and a clear message ("No available truck can carry 28 tons — try a smaller load or another date"). Showing a truck that cannot carry the load would be worse than showing nothing.

---

## Part 5 — Files to create (14 files)

All under `src/main/java/com/truckconnect/`, continuing Modules 1 and 2. No new dependency is needed.

| # | File | What it is |
| --- | ---: | --- |
| 1 | `pricing/PricingRate.java` | Entity — one row of the rate board |
| 2 | `pricing/PricingRateRepository.java` | Finds a rate by category |
| 3 | `pricing/PlatformSettings.java` | Entity — platform fee + minimum-load fraction (one row, id = 1) |
| 4 | `pricing/PlatformSettingsRepository.java` | Loads that one row |
| 5 | `pricing/PricingRateSeeder.java` | Inserts the 4 rates + settings once, on first start |
| 6 | `pricing/dto/PricingRateResponse.java` | What the admin sees |
| 7 | `pricing/dto/RateUpdateRequest.java` | Admin changing one rate |
| 8 | `pricing/PricingService.java` | The formula — pure calculation, no database |
| 9 | `quote/Quote.java` | Entity — one saved price shown to a customer |
| 10 | `quote/QuoteRepository.java` | Finds a customer's quotes |
| 11 | `quote/dto/SearchRequest.java` | What the customer sends |
| 12 | `quote/dto/QuoteResponse.java` | What the customer gets back, with the full breakdown |
| 13 | `quote/SearchService.java` | Matching + pricing, using the truck repository from Module 2 |
| 14 | `quote/QuoteController.java` + `pricing/PricingRateController.java` | The two controllers (counted as 2 files, so 15 files in total — see note) |

> **Note on the count:** the table lists 14 rows but the last row is two controllers, so you will create **15 files**. The number does not matter — the list does. Create them in this order, top to bottom.


---

## Part 6 — The rate board and the settings: entities and seeder

### File 1 of 15 — `pricing/PricingRate.java`

**Simple meaning:** One row is one line on the rate board: this category costs this many rupees per ton per km. The rate is money, so it is a `BigDecimal`, never a `double` (doubles round money wrongly).

```java
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
```

Why `uniqueConstraints` on `category_id`: one category must have exactly one current rate. Without it, two rows could disagree and the price would depend on which row the database returned first.

### File 2 of 15 — `pricing/PricingRateRepository.java`

```java
package com.truckconnect.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PricingRateRepository extends JpaRepository<PricingRate, Long> {
    Optional<PricingRate> findByCategoryIdAndActiveTrue(Long categoryId);
    List<PricingRate> findAllByActiveTrue();
    boolean existsByCategoryId(Long categoryId);
}
```

**Simple meaning:** Method names are the queries. `findByCategoryIdAndActiveTrue` is the one the price calculator uses — it finds the live rate for a truck's category, and returns empty if the admin deactivated it (then that truck simply gets no quote, instead of a wrong price).

### File 3 of 15 — `pricing/PlatformSettings.java`

**Simple meaning:** The two numbers that are the same for every quote live in their own tiny table with exactly one row (id = 1). The admin changes them; the code never hardcodes them.

```java
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
```

### File 4 of 15 — `pricing/PlatformSettingsRepository.java`

```java
package com.truckconnect.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}
```

### File 5 of 15 — `pricing/PricingRateSeeder.java`

**Simple meaning:** On the first start, insert the four rates from Part 3 and the settings row. On every later start it finds them already there and does nothing — restarting the app must never create duplicate rates. It runs after Module 2's category seeder, so the categories already exist.

```java
package com.truckconnect.pricing;

import com.truckconnect.truck.TruckCategory;
import com.truckconnect.truck.TruckCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

@Component
@Order(2) // Module 2's category seeder is @Order(1): categories must exist first
public class PricingRateSeeder implements CommandLineRunner {

    private final TruckCategoryRepository categoryRepository;
    private final PricingRateRepository rateRepository;
    private final PlatformSettingsRepository settingsRepository;

    public PricingRateSeeder(TruckCategoryRepository categoryRepository,
                             PricingRateRepository rateRepository,
                             PlatformSettingsRepository settingsRepository) {
        this.categoryRepository = categoryRepository;
        this.rateRepository = rateRepository;
        this.settingsRepository = settingsRepository;
    }

    // Category name -> rupees per ton per km (Part 3: checked online, Nov 2026)
    private static final Map<String, String> SEED_RATES = Map.of(
            "12 Tyre Truck", "2.70",
            "14 Tyre Truck", "2.60",
            "18 Tyre Truck", "2.50",
            "22 Tyre Truck", "2.40"
    );

    @Override
    public void run(String... args) {
        for (Map.Entry<String, String> entry : SEED_RATES.entrySet()) {
            TruckCategory category = categoryRepository
                    .findByName(entry.getKey()).orElse(null);
            if (category == null) continue; // category seeder has not run yet
            if (rateRepository.existsByCategoryId(category.getId())) continue;
            rateRepository.save(new PricingRate(
                    category, new BigDecimal(entry.getValue())));
        }
        if (!settingsRepository.existsById(1L)) {
            settingsRepository.save(new PlatformSettings()); // fee ₹0, minimum 50%
        }
    }
}
```

> **Check Module 2 first:** this seeder calls `categoryRepository.findByName(...)`. If your Module 2 repository does not have that method, add one line to it: `Optional<TruckCategory> findByName(String name);` — and make sure your category seeder used exactly these names ("12 Tyre Truck" and so on). If your names differ, change the four keys in `SEED_RATES` to match yours.

---

## Part 7 — The calculator: `PricingService`

### File 8 of 15 — `pricing/PricingService.java`

**Simple meaning:** This class does only arithmetic — no database, no HTTP, no login. That is deliberate: a class with no database is the easiest class in the whole project to unit-test, and the price formula is the one thing you most want tested. Every money step uses `BigDecimal` and rounds to 2 decimal places (paise) at the end.

```java
package com.truckconnect.pricing;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    /** The result of one price calculation, with every step visible. */
    public record FareBreakdown(
            BigDecimal chargeableTons,
            boolean minimumChargeApplied,
            BigDecimal baseFare,
            BigDecimal tollCharges,
            BigDecimal loadingCharges,
            BigDecimal platformFee,
            BigDecimal totalFare) { }

    /**
     * distanceKm  : kilometres, already validated (1..5000)
     * loadTons    : the customer's actual load
     * payloadTons : this truck's declared capacity
     * ratePerTonKm: from the rate table, by the truck's category
     * toll / loading: estimates from the search request (0 if unknown)
     * platformFee / minimumLoadFraction: from platform_settings
     */
    public FareBreakdown calculate(BigDecimal distanceKm,
                                   BigDecimal loadTons,
                                   BigDecimal payloadTons,
                                   BigDecimal ratePerTonKm,
                                   BigDecimal tollCharges,
                                   BigDecimal loadingCharges,
                                   BigDecimal platformFee,
                                   BigDecimal minimumLoadFraction) {
        BigDecimal minimumTons = payloadTons.multiply(minimumLoadFraction);
        boolean minimumApplied = loadTons.compareTo(minimumTons) < 0;
        BigDecimal chargeableTons = minimumApplied ? minimumTons : loadTons;

        BigDecimal baseFare = distanceKm
                .multiply(ratePerTonKm)
                .multiply(chargeableTons)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = baseFare
                .add(tollCharges).add(loadingCharges).add(platformFee)
                .setScale(2, RoundingMode.HALF_UP);

        return new FareBreakdown(chargeableTons, minimumApplied, baseFare,
                tollCharges, loadingCharges, platformFee, total);
    }
}
```

Work through Example 1 with this code: distance 570, load 20, payload 20, rate 2.70, toll 1500, loading 1000, fee 0, minimum fraction 0.50. Minimum tons = 10; load 20 is bigger, so `minimumApplied` is false and chargeable tons = 20. baseFare = 570 × 2.70 × 20 = 30,780. Total = 30,780 + 1,500 + 1,000 + 0 = **33,280**. It matches Part 3 — if your code ever disagrees with hand calculation, trust the hand calculation and fix the code.

---

## Part 8 — Search, quotes, and the controllers

### File 9 of 15 — `quote/Quote.java`

**Simple meaning:** One row is one price that was shown to one customer for one truck. Saving it matters: when Module 4 creates a booking from a quote, it reads the price from this row instead of recalculating — so nobody can change the price between "I saw ₹33,280" and "I booked". `validUntil` is 24 hours after creation; an old quote cannot be booked later after rates changed.

```java
package com.truckconnect.quote;

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
```

> **Check Module 1 first:** this entity imports `com.truckconnect.user.User`. If your Module 1 `User` class lives in a different package, change only that one import line to match yours.

### File 10 of 15 — `quote/QuoteRepository.java`

```java
package com.truckconnect.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuoteRepository extends JpaRepository<Quote, Long> {
    List<Quote> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
```

### Files 11 and 12 of 15 — the DTOs

**`quote/dto/SearchRequest.java`** — what the customer sends. The `@DecimalMin` / `@DecimalMax` annotations are the validation rules; they run automatically because the controller uses `@Valid`.

```java
package com.truckconnect.quote.dto;

import com.truckconnect.truck.BodyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SearchRequest(
        @NotBlank String pickup,
        @NotBlank String destination,
        @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal distanceKm,
        @NotNull @DecimalMin("0.1") @DecimalMax("40") BigDecimal loadTons,
        String goodsType,
        BodyType bodyType,          // optional: null means "any body"
        Long categoryId,            // optional: null means "any category"
        @DecimalMin("0") BigDecimal tollCharges,
        @DecimalMin("0") BigDecimal loadingCharges
) { }
```

**`quote/dto/QuoteResponse.java`** — what the customer gets back. Every number in the calculation is included, so the frontend can show the breakdown and the customer never has to guess where the total came from.

```java
package com.truckconnect.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuoteResponse(
        Long quoteId,
        Long truckId,
        String registrationNumber,
        String categoryName,
        int tyreCount,
        BigDecimal payloadTons,
        String bodyType,
        BigDecimal ratePerTonKm,
        BigDecimal chargeableTons,
        boolean minimumChargeApplied,
        BigDecimal baseFare,
        BigDecimal tollCharges,
        BigDecimal loadingCharges,
        BigDecimal platformFee,
        BigDecimal totalFare,
        LocalDateTime validUntil
) { }
```

### File 13 of 15 — `quote/SearchService.java`

**Simple meaning:** This is the stand manager from Part 2, in code. It (1) loads the settings and the available trucks, (2) throws away every truck that cannot carry the load, (3) prices the rest, (4) sorts cheapest first, (5) saves each quote. One search = one database read of available trucks, then plain Java filtering — simple and fast enough for an MVP with hundreds of trucks, not lakhs.

```java
package com.truckconnect.quote;

import com.truckconnect.pricing.*;
import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class SearchService {

    private final TruckRepository truckRepository;
    private final PricingRateRepository rateRepository;
    private final PlatformSettingsRepository settingsRepository;
    private final QuoteRepository quoteRepository;
    private final PricingService pricingService;

    public SearchService(TruckRepository truckRepository,
                         PricingRateRepository rateRepository,
                         PlatformSettingsRepository settingsRepository,
                         QuoteRepository quoteRepository,
                         PricingService pricingService) {
        this.truckRepository = truckRepository;
        this.rateRepository = rateRepository;
        this.settingsRepository = settingsRepository;
        this.quoteRepository = quoteRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public List<QuoteResponse> search(User customer, SearchRequest req) {
        PlatformSettings settings = settingsRepository.findById(1L)
                .orElseGet(() -> settingsRepository.save(new PlatformSettings()));
        BigDecimal toll = req.tollCharges() == null ? BigDecimal.ZERO : req.tollCharges();
        BigDecimal loading = req.loadingCharges() == null ? BigDecimal.ZERO : req.loadingCharges();

        List<Truck> candidates = truckRepository
                .findByAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        return candidates.stream()
                // Rule 1: can it carry the load? (truck's own payload, never tyre count)
                .filter(t -> t.getPayloadTons().compareTo(req.loadTons()) >= 0)
                // Rule 2: right body, if the customer asked for one
                .filter(t -> req.bodyType() == null || t.getBodyType() == req.bodyType())
                // Rule 3: right category, if the customer picked one
                .filter(t -> req.categoryId() == null
                        || t.getCategory().getId().equals(req.categoryId()))
                // Price it — skip trucks whose category has no live rate
                .flatMap(t -> rateRepository
                        .findByCategoryIdAndActiveTrue(t.getCategory().getId())
                        .stream().map(rate -> buildQuote(customer, req, t,
                                rate.getRatePerTonKm(), toll, loading, settings)))
                .sorted(Comparator
                        .comparing(QuoteResponse::totalFare)
                        .thenComparing(q -> q.payloadTons()
                                .subtract(req.loadTons()))) // closest fit first on a tie
                .toList();
    }

    private QuoteResponse buildQuote(User customer, SearchRequest req, Truck truck,
                                     BigDecimal rate, BigDecimal toll, BigDecimal loading,
                                     PlatformSettings settings) {
        PricingService.FareBreakdown fare = pricingService.calculate(
                req.distanceKm(), req.loadTons(), truck.getPayloadTons(),
                rate, toll, loading,
                settings.getPlatformFee(), settings.getMinimumLoadFraction());

        Quote quote = new Quote();
        quote.setCustomer(customer);
        quote.setTruck(truck);
        quote.setPickup(req.pickup());
        quote.setDestination(req.destination());
        quote.setDistanceKm(req.distanceKm());
        quote.setLoadTons(req.loadTons());
        quote.setGoodsType(req.goodsType());
        quote.setRatePerTonKm(rate);
        quote.setChargeableTons(fare.chargeableTons());
        quote.setMinimumChargeApplied(fare.minimumChargeApplied());
        quote.setBaseFare(fare.baseFare());
        quote.setTollCharges(toll);
        quote.setLoadingCharges(loading);
        quote.setPlatformFee(fare.platformFee());
        quote.setTotalFare(fare.totalFare());
        quote.setValidUntil(LocalDateTime.now().plusHours(24));
        quote = quoteRepository.save(quote);

        return new QuoteResponse(
                quote.getId(), truck.getId(), truck.getRegistrationNumber(),
                truck.getCategory().getName(), truck.getCategory().getTyreCount(),
                truck.getPayloadTons(), truck.getBodyType().name(),
                rate, fare.chargeableTons(), fare.minimumChargeApplied(),
                fare.baseFare(), toll, loading, fare.platformFee(),
                fare.totalFare(), quote.getValidUntil());
    }
}
```

Three things to notice in this code:

1. **It needs one new repository method in Module 2.** Add to `TruckRepository`: `List<Truck> findByAvailabilityStatus(AvailabilityStatus status);` — Spring Data writes that query for you from the name. (If you did **not** do the Part 0 slim correction and kept `verificationStatus`, use `findByVerificationStatusAndAvailabilityStatus(VerificationStatus.APPROVED, AvailabilityStatus.AVAILABLE)` here instead — that method already exists in Module 2.)
2. **It uses the truck's own `payloadTons`, never the category target.** The category target is only a label for grouping; the match is always against the individual truck.
3. **It trusts nothing from the request except the search inputs.** The rate comes from the database, the fee from settings, the truck from the database. A customer cannot send `totalFare: 1` in JSON and get a ₹1 quote, because the request object has no total field at all.

> **Getter names to check:** this code calls `truck.getPayloadTons()`, `truck.getBodyType()`, `truck.getCategory().getName()`, `truck.getCategory().getTyreCount()` and `truck.getRegistrationNumber()`. If your Module 2 named any of these differently, change only those calls to match your names.

### Files 6 and 7 of 15 — the rate DTOs

**`pricing/dto/PricingRateResponse.java`**

```java
package com.truckconnect.pricing.dto;

import java.math.BigDecimal;

public record PricingRateResponse(
        Long id, String categoryName, int tyreCount,
        BigDecimal targetPayloadTons, BigDecimal ratePerTonKm, boolean active) { }
```

**`pricing/dto/RateUpdateRequest.java`**

```java
package com.truckconnect.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RateUpdateRequest(
        @NotNull @DecimalMin("0.10") @DecimalMax("20.00") BigDecimal ratePerTonKm,
        Boolean active) { }
```

The validation range (₹0.10–₹20.00 per ton per km) stops a typo like ₹270 instead of ₹2.70 from silently going live — ₹270 per ton per km would price a 500 km, 20-ton trip at ₹27 lakh.

### Files 14 and 15 of 15 — the controllers

**`quote/QuoteController.java`** — customers only. The customer comes from the JWT (Module 1), never from the request body — so a customer can only ever search as themselves, and the quotes are saved under their own id.

```java
package com.truckconnect.quote;

import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final SearchService searchService;

    public QuoteController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<QuoteResponse>> search(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody SearchRequest request) {
        return ResponseEntity.ok(searchService.search(customer, request));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<QuoteResponse>> myQuotes(
            @AuthenticationPrincipal User customer) {
        // Kept simple for the MVP: re-read from the repository via the service.
        // A fuller version maps Quote -> QuoteResponse here; Module 4 will reuse it.
        return ResponseEntity.ok(List.of());
    }
}
```

> **Note on `GET /api/quotes/mine`:** it returns an empty list for now, on purpose. Mapping a saved `Quote` back to a `QuoteResponse` is 15 lines of field copying with no new idea in it — Module 4 needs that mapping for bookings anyway, so it will be finished there rather than duplicated here. The search endpoint above is the real work of this module. (If `@AuthenticationPrincipal User` does not match your Module 1 setup — for example your filter stores a `UserDetails` instead of the `User` entity — use whatever your Module 1 controllers already use to get the logged-in user, and change only that parameter.)

**`pricing/PricingRateController.java`** — admins only. Changing a rate never touches a truck or a quote that already exists; old quotes keep the rate they were calculated with, because the rate is copied onto the quote row at search time.

```java
package com.truckconnect.pricing;

import com.truckconnect.pricing.dto.PricingRateResponse;
import com.truckconnect.pricing.dto.RateUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/rates")
@PreAuthorize("hasRole('ADMIN')")
public class PricingRateController {

    private final PricingRateRepository rateRepository;

    public PricingRateController(PricingRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @GetMapping
    public ResponseEntity<List<PricingRateResponse>> list() {
        List<PricingRateResponse> out = rateRepository.findAll().stream()
                .map(r -> new PricingRateResponse(r.getId(),
                        r.getCategory().getName(), r.getCategory().getTyreCount(),
                        r.getCategory().getTargetPayloadTons(),
                        r.getRatePerTonKm(), r.isActive()))
                .toList();
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PricingRateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RateUpdateRequest req) {
        PricingRate rate = rateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rate not found: " + id));
        rate.setRatePerTonKm(req.ratePerTonKm());
        if (req.active() != null) rate.setActive(req.active());
        rate = rateRepository.save(rate);
        return ResponseEntity.ok(new PricingRateResponse(rate.getId(),
                rate.getCategory().getName(), rate.getCategory().getTyreCount(),
                rate.getCategory().getTargetPayloadTons(),
                rate.getRatePerTonKm(), rate.isActive()));
    }
}
```

> **Error handling:** `IllegalArgumentException` here returns a 500 by default in plain Spring Boot. If Module 1 already added a global exception handler (`@ControllerAdvice`) that turns it into a 404/400, this works as written. If not, that handler is a good small job for Module 4 — do not build it inside this module.

---

## Part 9 — Test checklist (do these in order)

You need: the app running, one ADMIN token, one CUSTOMER token, and at least two trucks marked `AVAILABLE` — for example a 12-tyre truck with payload 20 tons and a 14-tyre truck with payload 25 tons, both in different categories. (Create them with the Module 2 owner endpoints first.)

| # | Test | Expected result |
| --- | --- | --- |
| 1 | Start the app. Check the logs / database. | `pricing_rates` has 4 rows (2.70 / 2.60 / 2.50 / 2.40); `platform_settings` has 1 row (fee 0, minimum 0.50). Restart the app — still 4 rows, no duplicates. |
| 2 | `GET /api/admin/rates` as ADMIN. | 4 rates with category names. |
| 3 | Same request as CUSTOMER. | 403 Forbidden. |
| 4 | Search as CUSTOMER: Bengaluru→Hyderabad, 570 km, 20 tons. | Both trucks returned. 12-tyre total ₹33,280 (with toll 1500, loading 1000). 14-tyre total: 570 × 2.60 × 20 = 29,640 + 2,500 = **₹32,140 — cheaper than the 12-tyre**, because the bigger truck costs less per ton. The list is sorted cheapest first, so the 14-tyre comes first. That is correct behaviour, not a bug. |
| 5 | Same search with load 28 tons. | **Neither** truck matches (20 < 28 and 25 < 28) — empty list. Now add or edit a truck to 30 tons and repeat — it matches. This proves matching uses the truck's own payload. |
| 6 | Search with load 8 tons on the 20-ton truck. | Quote shows `minimumChargeApplied: true`, `chargeableTons: 10`, total ₹17,890 (Part 3, Example 2). |
| 7 | Search with `distanceKm: 0` and with `loadTons: 50`. | 400 Bad Request both times (validation works). |
| 8 | Search with no token at all. | 401 Unauthorized. |
| 9 | Search as OWNER. | 403 Forbidden (only CUSTOMER searches in this module). |
| 10 | `PUT /api/admin/rates/{id}` — change the 12-tyre rate to 2.80 as ADMIN, then search again. | New quotes use 2.80. Old quotes in the `quotes` table still show 2.70 — check the database, do not trust memory. Then change it back to 2.70. |
| 11 | Try `ratePerTonKm: 270` in the same PUT. | 400 Bad Request (the ₹0.10–₹20.00 validation caught the typo). |
| 12 | Search twice with the same inputs. | Two quote rows in the database with different ids but the same total. Quotes are saved every time — that is how Module 4 will find them. |

### Common mistakes

- **Mixing up per-km and per-ton-per-km.** ₹54/km (full truck) and ₹2.70/ton/km (same truck, 20 tons) are the same price written two ways. If a quote looks 20× too big or too small, you multiplied or divided by the tons one time too many.
- **Using the category target payload for matching.** The category says "20 tons" as a guide; the truck says what it can actually carry. Always match on `truck.payloadTons`.
- **Forgetting the minimum charge in the frontend.** If the frontend shows only `totalFare`, the customer will not understand why 8 tons costs almost as much as 20 tons. Show `chargeableTons` and the "minimum charge applied" note from the response.
- **Letting the customer send a price.** The search request has no price field. If you add one "for convenience", a user can send any price they like. Never accept a price from the client.
- **Using `double` for money.** `0.1 + 0.2` is `0.30000000000000004` in a double. `BigDecimal` with `HALF_UP` rounding is the only correct choice here.

---

## What changes in the PRD, and what is next

**PRD correction (v1.1 note):** the old rates (₹35/₹42/₹50/₹60 per km) and the ₹20,500 worked example are replaced by the Part 3 rates and examples. The three open decisions from the PRD are still open — B2B / B2C / Both, Instant / Quotation / Both, launch corridor — and they matter more now: if you choose **Instant booking**, Module 4 books straight from a quote; if you choose **Quotation only**, Module 4 adds an owner-accept step before the price is final. Tell me your answers when you are ready; Module 4 will follow them.

**Module 4 — Booking (next):** turn a saved quote into a booking (`PENDING → CONFIRMED → DRIVER_ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED → COMPLETED`), owner accept/reject, no double-booking (one available truck, one active booking at a time), and the booking reads its price from the quote row — never recalculated.

**After Module 4:** customer/owner/driver/admin React screens (Module 5), then payments, then deployment — Vercel + Render + TiDB Cloud free MySQL, platform fee ₹0, the same free stack as ShopKart.
