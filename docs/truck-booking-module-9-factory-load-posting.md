# Truck Booking — Module 9: Factory Load Posting

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1–8 keep working; rename the package later only if you want to)
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8, Spring Security + JWT
**Builds on:** Module 1 — Authentication & Security (roles, JWT) · Module 2 — Truck Categories & Registration (slim version) · Module 3 — Search, Matching & Pricing (quotes, rates, platform fee ₹0) · Module 4 — Booking (instant booking, snapshot, no double-booking, owner call/chat)
**This module only:** Let a factory / business / any customer post one big load — "I have 60 tons, I need 3 trucks of 20 tons each" — see the matching available trucks with an instant price per truck and a total for all of them, book one or more of those trucks instantly, and then call/chat with each truck owner. No payment (Module 6), no driver assignment changes (Module 5), no live distance from Google Maps.

## How to use this guide

1. Read Parts 1 to 4 so you understand *why* the code is shaped this way.
2. Add one file at a time in VS Code, in the order given in Part 5. Modules 1–4 must already be working — you need a login token, at least three `AVAILABLE` trucks, and a working Module 3 search before you can test anything here.
3. Make the two small edits in Part 9 first (one new link on `Quote`, one new link on `Booking`) — Module 9 will not compile without them.
4. Start the app and work through the test checklist in Part 10.
5. Do not skip the over-booking rule in Part 4. It is the whole point of this module: a load post that needs 3 trucks must never end up with 4 bookings, even if two people click "Book" at the same second.

> **The one rule of this module:** a load post is a **shopping list, not a booking**. Posting "I need 3 trucks" books nothing and charges nothing. Only the "Book" step — which runs Module 4's booking code, one booking per truck — creates bookings, locks trucks, and fixes prices. If a post is cancelled or expires, nothing is owed, because nothing was ever booked.

---

## Part 1 — What this module does, and why it comes ninth

### What this module does

Modules 1–4 answered, in order: *who are you?* *what trucks exist?* *which truck fits my load, and what will it cost?* *lock it in.* But every one of those steps assumed **one customer, one load, one truck**. A factory does not think like that. A factory thinks: "60 tons of steel coils are ready in Bengaluru, they must reach Hyderabad, I need 3 trucks of 20 tons each, pickup is tomorrow."

This module gives Truck Booking:

- A `load_posts` table — one row per posted load: what the goods are, how many tons in total, how many trucks are needed, how many tons each truck must carry, where from, where to, how far, on which date, which body type
- A load-post lifecycle — `OPEN → PARTIALLY_BOOKED → FULLY_BOOKED`, plus `CANCELLED` and `EXPIRED`, enforced in the service, never by the frontend
- Matching for a whole load — for one post, the same Module 3 rules run once **per truck needed**: available trucks whose payload is big enough for one truck's share, cheapest first, each with its own instant price, plus the total for all trucks needed
- Booking from a load post — the poster books one, two, or all of the matching trucks instantly. Each truck gets its **own Module 4 booking** (own status, own owner, own chat), and every one of those bookings is linked back to the same load post, so the factory can see "2 of 3 trucks booked" in one place
- Partial booking — booking only some trucks leaves the post `PARTIALLY_BOOKED` with the remaining count visible; booking the last truck flips it to `FULLY_BOOKED` automatically
- Over-booking protection — the same pessimistic lock + active-booking check from Module 4, plus a count check on the post itself, all inside one transaction, so two people can never book more trucks than the post needs
- Owner call / chat after booking — exactly as in Module 4: the phone number and the chat thread live on each booking, never on the public load post

### Why it comes ninth

A load post is useless without everything before it. It needs logged-in users (Module 1), real trucks with payloads (Module 2), a price formula and a quote to book from (Module 3), and a booking that locks a truck and snapshots its price (Module 4). This module adds **no new pricing and no new booking logic** — it reuses Module 3 to price each truck and Module 4 to book each truck. That is deliberate: one price formula and one booking path in the whole project means a bug fixed in one place is fixed everywhere.

### What this module does not do

- No payment — each booking created from a post is a normal Module 4 booking, so Module 6 (COD) applies to it exactly as it applies to a single-truck booking
- No driver assignment changes — Module 5 owns drivers; each booking from a post gets its driver the same way any other booking does
- No bidding or quotation round-trip — booking from a post is **instant booking at the fixed quoted price**, the decision you already made (Instant + owner call/chat). An owner can still reject a booking (Module 4), and then that truck's slot opens up again on the post
- No live distance from Google Maps — the poster enters the distance in km, exactly as in Module 3
- No real-time chat — messages are stored rows read by `GET`, exactly as in Module 4
- No splitting one truck across two posts — one truck carries one truck's share of one load; a 20-ton truck is never half-booked to two different factories

---

## Part 2 — The big picture in simple words

Think of Truck Booking like a notice board at a truck stand, plus a receptionist.

1. **The factory pins a notice on the board.** "60 tons steel coils, Bengaluru → Hyderabad, 570 km, pickup tomorrow, need 3 trucks of 20 tons each, open body." That notice is the load post. Anyone logged in can read the board; only the factory that pinned it (and the admin) can take it down or change it.
2. **Truck owners walk past the board and see only the notices that fit their truck.** An owner with a 20-ton truck sees this notice; an owner with a 12-ton truck does not — their truck cannot carry one 20-ton share. That is the owner view, `GET /api/owner/load-posts/matching`.
3. **The factory asks the receptionist: "who can do this job, and what will it cost?"** The receptionist (the matching endpoint) runs the Module 3 search once, with one truck's share — 20 tons — and prices every available truck that can carry 20 tons. The answer is a list, cheapest first, each with its own fare, plus the total if the factory books all 3 trucks at that truck's price.
4. **The factory picks trucks and presses "Book".** For each truck picked, the receptionist does exactly what Module 4 does for a single booking: check the quote, lock the truck row, check no other live booking exists for it, copy the price onto a new booking, mark the quote used. One booking per truck. If the factory picks 2 trucks now, the notice stays up saying "1 truck still needed" — `PARTIALLY_BOOKED`.
5. **The last truck flips the notice to "done".** When the third truck is booked, the post becomes `FULLY_BOOKED` and disappears from the open board. The factory now has 3 bookings, 3 owners, 3 phone numbers, 3 chats — one per truck — and can coordinate pickup times with each owner separately.
6. **If an owner rejects, the slot opens again.** A rejected booking does not eat the slot: the post goes back from `FULLY_BOOKED` to `PARTIALLY_BOOKED` (or stays `PARTIALLY_BOOKED`), the remaining count goes back up by one, and the factory can book a different truck for that slot.

### The flow, step by step

```text
FACTORY (CUSTOMER or TRANSPORTER) logs in (Module 1 JWT)
  -> POST /api/load-posts
       { goodsDescription: "Steel coils", totalTons: 60, trucksNeeded: 3,
         categoryId: 12-tyre, bodyType: OPEN,
         pickup: "Bengaluru", destination: "Hyderabad", distanceKm: 570,
         pickupDate: "2026-11-12", tollCharges: 1500, loadingCharges: 1000 }
       -> returns the post, status OPEN, trucksBooked 0, trucksRemaining 3

FACTORY (or any logged-in user)
  -> GET /api/load-posts/{id}/matches
       server: per-truck load = 60 / 3 = 20 tons
       server: Module 3 search with load 20 tons -> quotes, cheapest first
       -> returns matches (each: truck + quote + per-truck fare)
          + summary: cheapest per-truck fare, total if all 3 booked at that fare

FACTORY books 2 trucks now
  -> POST /api/load-posts/{id}/book { "quoteIds": [11, 12] }
       server (ONE transaction): lock the post row, count live bookings (0),
         need 3, booking 2 is allowed (0 + 2 <= 3)
       server: for each quote -> Module 4 createBooking logic
         (quote must belong to this post, ACTIVE, not expired;
          lock truck, no live booking, snapshot, quote -> BOOKED)
       -> returns 2 bookings (201 Created), post -> PARTIALLY_BOOKED,
          trucksBooked 2, trucksRemaining 1

OWNER of each booked truck
  -> POST /api/owner/bookings/{id}/accept   (Module 4, unchanged)
  -> factory calls / chats each owner from each booking (Module 4, unchanged)

FACTORY books the last truck later
  -> POST /api/load-posts/{id}/book { "quoteIds": [15] }
       -> 1 booking, post -> FULLY_BOOKED, trucksRemaining 0
```

### Who can do what

| Action | CUSTOMER | TRANSPORTER | OWNER | DRIVER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| Post a load | Yes | Yes | No | No | No |
| Edit / cancel my own post | Yes (own posts, OPEN or PARTIALLY_BOOKED only) | Yes (own posts) | No | No | Yes (cancel any post) |
| See open posts (the board) | Yes | Yes | Yes | No | Yes, all |
| See posts matching my trucks | — (see open posts) | — | Yes (`/api/owner/load-posts/matching`) | No | Yes |
| Get matches + prices for a post | Yes (own posts, or any open post) | Yes | Yes (for their own trucks, via owner view) | No | Yes |
| Book trucks from a post | Yes (own posts only) | Yes (own posts only) | No | No | No |
| See / call / chat on the bookings a post created | Yes (own posts' bookings — via each booking, Module 4) | Yes (own posts' bookings) | Yes (own trucks' bookings) | No | Yes (read) |
| Touch a post that is not theirs | No | No | No | No | Yes (cancel) |

**Why CUSTOMER and TRANSPORTER, not OWNER?** A factory posts a load because it *has goods to move* — that is a customer job, whether it is a person (B2C) or a company (B2B). An owner *has trucks to offer* — that is the other side of the board. One user account has one role in Module 1, so a factory owner who also owns trucks uses two accounts, or posts as a customer and lists trucks as an owner. Do not let an owner book their own trucks through a post — the service refuses it (Part 4, Rule 4).

---

## Part 3 — The load post: fields, statuses, and the per-truck maths

### The fields

| Field | What it means | Rule |
| --- | --- | --- |
| `goodsDescription` | What the goods are — "Steel coils", "Rice bags", "Furniture" | Required, 3–200 characters. This is what owners read on the board. |
| `totalTons` | All the goods together, in tons | Required, 0.5–500 tons. 500 is the ceiling because 500 ÷ 20 = 25 trucks — bigger than that is not one post, it is a contract. |
| `trucksNeeded` | How many trucks the poster wants | Required, 1–25. Must fit the maths below. |
| `tonsPerTruck` | How many tons **each** truck must carry | **Calculated, never typed:** `totalTons / trucksNeeded`. Stored on the post so matching and pricing always use the same number. |
| `category` | Which truck category (12 / 14 / 18 / 22 tyre) | Required. Matching only considers trucks in this category — the factory asked for this size, and a bigger truck at a different rate is a different job. |
| `bodyType` | Which body the goods need (OPEN, CLOSED_CONTAINER, FLATBED, TRAILER, REFRIGERATED) | Optional — null means "any body". Same values as Module 2 `BodyType`. |
| `pickup`, `destination` | Cities | Required, same as Module 3 quotes. |
| `distanceKm` | Kilometres, entered by the poster | Required, 1–5000, same as Module 3. |
| `pickupDate` | The date the goods must be picked up | Required, today or later. A post whose pickup date has passed is expired (see statuses). |
| `tollCharges`, `loadingCharges` | Estimates, per truck | Optional, 0 if unknown. **Per truck, not for the whole load** — each booking is priced per truck, so the extras must be per truck too, or the total will be wrong. |
| `contactPhone` | A phone number for owners to reach the poster | Optional — if empty, the poster's Module 1 phone number is used. Shown only to owners whose truck matches, never on the public board. (Same privacy rule as Module 4: a phone number is only ever shown to a party to a booking — here, to an owner viewing a matching post. A stranger browsing open posts sees no phone number.) |
| `status` | OPEN / PARTIALLY_BOOKED / FULLY_BOOKED / CANCELLED / EXPIRED | Set by the service, never by the request. |
| `postedBy` | The user who posted it | From the JWT, never from the request body. |

### The per-truck maths — the one calculation that matters

Everything in this module hangs on one division:

```text
tonsPerTruck = totalTons / trucksNeeded
```

And one matching rule, borrowed wholesale from Module 3:

```text
a truck matches  <=>  availabilityStatus = AVAILABLE
                     AND truck.category = post.category
                     AND truck.payloadTons >= tonsPerTruck
                     AND (post.bodyType is null OR truck.bodyType = post.bodyType)
```

And one pricing rule, borrowed wholesale from Module 3 — the fare for **one** truck carrying **one** share:

```text
chargeableTons = max(tonsPerTruck, payloadTons x 0.5)   (the 50% minimum-charge rule)
baseFare       = distanceKm x ratePerTonKm x chargeableTons
perTruckFare   = baseFare + tollCharges + loadingCharges + platformFee (₹0)
totalForAllTrucks = perTruckFare x trucksNeeded        (only meaningful
                     if every truck is booked at the same fare — see below)
```

**Two things to notice:**

1. **`totalTons` must divide evenly.** If a factory posts 50 tons and asks for 3 trucks, `tonsPerTruck` = 16.67 tons — a nonsense share that no owner can load exactly. So the service **refuses** a post unless `totalTons` divides evenly by `trucksNeeded` (to 2 decimal places — 50 tons / 2 trucks = 25.00 is fine; 50 / 3 = 16.67 is refused with `400` and the message "Total tons must divide evenly across the trucks needed — try 50 tons with 2 trucks, or 51 tons with 3"). The factory fixes the numbers before posting, instead of the system guessing. This is the single most common mistake in this module — see "Common mistakes".
2. **The "total for all trucks" is an estimate, not a promise.** Each truck is priced on its own: two 12-tyre trucks cost the same per truck (same category, same rate), but if the factory books a 12-tyre truck for one slot and the matches list also contains a different truck in the same category at a different payload, the minimum-charge rule can make their fares differ. So the summary shows **both**: the cheapest per-truck fare, and the total if all remaining trucks were booked at that cheapest fare, clearly labelled as an estimate. The real total is the sum of the bookings actually created — never the estimate.

### The statuses

| Status | Simple meaning | Is it final? |
| --- | --- | --- |
| `OPEN` | Posted, no truck booked yet | No |
| `PARTIALLY_BOOKED` | At least one truck booked, at least one still needed | No |
| `FULLY_BOOKED` | All trucks needed are booked (live bookings = `trucksNeeded`) | **Yes, for booking** — no more trucks can be booked from it, but it is not deleted: the factory still sees its bookings through it |
| `CANCELLED` | The poster (or admin) called it off | **Yes** |
| `EXPIRED` | The pickup date passed while trucks were still needed | **Yes** |

**The transition table** — the only moves allowed, enforced by `LoadPostStatus.canMoveTo(...)` exactly like Module 4's `BookingStatus`:

```text
OPEN             -> PARTIALLY_BOOKED, FULLY_BOOKED, CANCELLED, EXPIRED
PARTIALLY_BOOKED -> FULLY_BOOKED, CANCELLED, EXPIRED
FULLY_BOOKED     -> CANCELLED
CANCELLED        -> (nothing — final)
EXPIRED          -> (nothing — final)
```

Three things to notice:

1. **The service moves the status, never the client.** Booking the first truck moves `OPEN → PARTIALLY_BOOKED`; booking the last moves it to `FULLY_BOOKED`. The request never contains a status. A client that sends `{"status": "FULLY_BOOKED"}` gets it ignored — there is no status field on any request object in this module.
2. **A rejected booking re-opens the slot.** If a booking created from a post is rejected by its owner (Module 4), the live-booking count for the post drops by one, and the service moves the post back: `FULLY_BOOKED → PARTIALLY_BOOKED`, or it stays `PARTIALLY_BOOKED` with one more truck remaining. This is a small hook in the booking flow (Part 9, Edit C) — without it, a rejected truck would silently eat a slot and the factory could never fill its load.
3. **Expiry is checked on read, not by magic.** There is no background job in the MVP. Every time the service lists or loads a post, it first runs `expireOverduePosts()`: any post in `OPEN` or `PARTIALLY_BOOKED` whose `pickupDate` is before today becomes `EXPIRED`. If you later add a `@Scheduled` daily job, it calls the same one method — the rule lives in one place either way. A `FULLY_BOOKED` post never expires: its trucks are already booked, and the bookings have their own lifecycle.

### Worked examples (check these by hand — the test checklist uses them)

**Example 1 — The factory load from your voice call: 60 tons, 3 trucks, Bengaluru → Hyderabad (the main example)**
- Post: 60 tons total, 3 trucks needed, 12-tyre category, OPEN body, 570 km, toll ₹1,500, loading ₹1,000, platform fee ₹0
- `tonsPerTruck` = 60 ÷ 3 = **20 tons** — divides evenly, so the post is accepted
- Matching: 12-tyre trucks with `payloadTons >= 20` and `AVAILABLE`
- Per-truck fare: chargeable = max(20, 20 × 0.5 = 10) = 20 tons; base = 570 × 2.70 × 20 = **₹30,780**; total per truck = 30,780 + 1,500 + 1,000 + 0 = **₹33,280**
- Total if all 3 booked at this fare: 33,280 × 3 = **₹99,840**
- After booking 2 trucks: post is `PARTIALLY_BOOKED`, 2 booked, **1 remaining (20 tons still to move)**. After the third: `FULLY_BOOKED`, 0 remaining.

**Example 2 — Bigger trucks, fewer of them: 50 tons, 2 trucks, 500 km**
- Post: 50 tons, 2 trucks, 14-tyre category, 500 km, toll ₹1,500, loading ₹1,000
- `tonsPerTruck` = 50 ÷ 2 = **25 tons**
- Per-truck fare: base = 500 × 2.60 × 25 = **₹32,500**; total per truck = 32,500 + 1,500 + 1,000 + 0 = **₹35,000**
- Total for both trucks: 35,000 × 2 = **₹70,000**

**Example 3 — The minimum-charge rule, per truck: 45 tons, 3 trucks, 570 km**
- Post: 45 tons, 3 trucks, 12-tyre, 570 km, toll ₹1,500, loading ₹1,000
- `tonsPerTruck` = 45 ÷ 3 = **15 tons** — each truck carries only 15 of its 20 tons
- Per-truck fare: chargeable = max(15, 20 × 0.5 = 10) = 15 tons (the minimum does **not** bite here — 15 is already more than half); base = 570 × 2.70 × 15 = **₹23,085**; total per truck = 23,085 + 1,500 + 1,000 + 0 = **₹25,585**
- Total for 3 trucks: 25,585 × 3 = **₹76,755**
- If the same 45 tons had been split across **4** trucks instead (11.25 tons each — and 45 ÷ 4 = 11.25 divides evenly), the minimum charge *would* bite on a 20-ton truck: chargeable = max(11.25, 10) = 11.25, still above half — so no. But 40 tons across 4 trucks (10 tons each) is exactly at the minimum. The rule is per truck, per booking, exactly as in Module 3 — nothing new to learn, only a new place it shows up.

**Example 4 — Refused at posting time: 50 tons, 3 trucks**
- `tonsPerTruck` = 50 ÷ 3 = 16.666… — does not divide evenly
- The service returns `400 Bad Request`: "Total tons (50) must divide evenly across 3 trucks. Try 48 tons (16 each), 51 tons (17 each), or 2 trucks (25 each)."
- The factory fixes the numbers and posts again. No post is ever saved with a fractional share.

---

## Part 4 — The four hard rules, in plain words

### Rule 1 — Reuse Module 3 to price, reuse Module 4 to book. Never copy their code.

The matches endpoint does **not** contain a price formula. It builds a Module 3 `SearchRequest` (pickup, destination, distance, `loadTons = tonsPerTruck`, body type, category) and calls `SearchService.search(...)` — the exact method Module 3 already tested. The only new work is tagging each returned quote with this post (Part 9, Edit A) so the booking step can prove the quote came from this post.

The book endpoint does **not** contain booking logic. For each quote id, it calls the same steps Module 4's `createBooking` performs: quote belongs to this post, quote `ACTIVE` and not expired, lock the truck, no live booking for that truck, snapshot, quote → `BOOKED`. The code lives in `LoadPostService.bookFromPost(...)`, which calls `BookingService` — it does not re-implement it. If Module 4's double-booking guard ever improves, load-post bookings improve with it, for free.

**Why this matters for a beginner:** if you copy the pricing formula into a second place, the two copies will drift — one day the rate changes in one and not the other, and the factory sees ₹33,280 on the board and ₹34,420 on the booking. One formula, one booking path, zero drift.

### Rule 2 — No over-booking: count, lock, then book — all in one transaction

Three things work together inside one `@Transactional` method (`LoadPostService.bookFromPost`):

1. **Lock the post row first.** The service loads the post with `findByIdForUpdate(...)` (`SELECT … FOR UPDATE`, exactly as Module 4 locks the truck). A second booking request for the same post waits at that line, then sees the world *after* the first booking.
2. **Count live bookings for the post, inside the transaction, after the lock.** Live means a `Booking` linked to this post whose status is in `BookingStatus.ACTIVE` (`PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT` — Module 4's set). If `liveCount + quoteIds.size() > trucksNeeded`, the whole request fails with `409 Conflict` and the message "This load needs 3 trucks, 2 are already booked — you can book at most 1 more". Nothing is booked, nothing is half-done: the transaction rolls back.
3. **Each truck is still locked and checked individually** by the Module 4 booking steps — so even if two different posts somehow target the same truck, only one booking wins. The post-level count stops *too many* trucks; the truck-level lock stops *the same* truck twice.

**Truck status through a load-post booking:** identical to Module 4 — `PENDING` leaves the truck column `AVAILABLE` but the active-booking check blocks a second booking; `CONFIRMED` moves it to `ON_TRIP`; `COMPLETED` / `CANCELLED` returns it to `AVAILABLE`. Nothing new to learn.

### Rule 3 — One booking per truck, each with its own owner, price, and chat

A load post never creates a "group booking". It creates **N ordinary Module 4 bookings**, each linked to the post by `booking.loadPost`. That is what makes everything else work without new code:

- Each booking has its own owner (different trucks can belong to different owners — the whole point of a marketplace), its own snapshot price, its own lifecycle, its own call link and chat thread (Module 4, Part 4, Rule 5 — the phone number is only ever sent to a party to *that* booking).
- Cancelling one booking (customer free-cancel in `PENDING`/`CONFIRMED`, Module 4 rules) frees **one** slot on the post: the live count drops, the post moves back to `PARTIALLY_BOOKED` if it was `FULLY_BOOKED`, and the factory can book a replacement truck. Cancelling one booking never cancels the post or the other bookings.
- Cancelling the **post** does **not** cancel its bookings. A post in `PARTIALLY_BOOKED` can be cancelled (the factory no longer needs the remaining trucks), but the trucks already booked stay booked — their owners have already committed. The cancel-post endpoint refuses to cancel a `FULLY_BOOKED` post for exactly this reason: there is nothing left to cancel on the post, only bookings, and those are cancelled one by one through Module 4. If you want "cancel everything", cancel the post first, then cancel each booking — two deliberate steps, never one silent wipe.

### Rule 4 — Ownership is checked on the post, not just on the booking

- **Posting:** the poster comes from the JWT. A request body containing `postedBy` is ignored — there is no such field on the request object.
- **Editing / cancelling a post:** only the poster or an ADMIN. A different customer gets `403`, even if they guessed the post id.
- **Booking from a post:** only the poster (or an ADMIN acting for them — no, the admin does **not** book; the admin only cancels. Booking is the poster's job). A different customer booking from someone else's post gets `403` — otherwise anyone could spend a factory's load.
- **The poster cannot book their own trucks.** If the poster is also a truck owner (two accounts, or one account with both roles — Module 1 gives one role per account, so this needs two accounts), the service refuses any quote whose truck belongs to the poster with `409` "You cannot book your own truck". The marketplace rule is simple: you are either the factory or the fleet on any one booking, never both.
- **Viewing matches:** any logged-in user may view matches for an open post — that is the board. But the contact phone is only shown to an owner whose truck actually matches the post, and to the poster and admin. A stranger browsing the board sees the goods, the tons, the route, and the date — never a phone number.

---

## Part 5 — Files to create (11 files + 3 small edits)

All under `src/main/java/com/truckconnect/`, continuing Modules 1–4. **No new dependency is needed.**

| # | File | What it is |
| --- | --- | --- |
| 1 | `loadpost/LoadPostStatus.java` | Enum — the 5 statuses + the transition table |
| 2 | `loadpost/LoadPost.java` | Entity — one posted load |
| 3 | `loadpost/LoadPostRepository.java` | Finds open posts, the poster's posts, matching posts for an owner |
| 4 | `loadpost/dto/CreateLoadPostRequest.java` | What the factory sends to post a load |
| 5 | `loadpost/dto/LoadPostResponse.java` | What everyone gets back: the post + booked / remaining counts |
| 6 | `loadpost/dto/LoadPostMatchResponse.java` | One matching truck + its quote + per-truck fare + the totals summary |
| 7 | `loadpost/dto/BookFromLoadPostRequest.java` | What the factory sends to book: just the quote ids |
| 8 | `loadpost/LoadPostMapper.java` | One place that turns a `LoadPost` into a `LoadPostResponse` |
| 9 | `loadpost/LoadPostService.java` | The brain: post, match, book, cancel, expire, ownership checks |
| 10 | `loadpost/LoadPostController.java` | Customer/transporter/admin endpoints |
| 11 | `loadpost/OwnerLoadPostController.java` | Owner endpoints: posts matching my trucks |

**Edits (not new files):**

- **Edit A — `quote/Quote.java` (Module 3):** add a nullable `loadPost` link (Part 9). A normal Module 3 quote has `loadPost = null`; a quote created by the matches endpoint points at its post.
- **Edit B — `booking/Booking.java` (Module 4):** add a nullable `loadPost` link (Part 9). A normal Module 4 booking has `loadPost = null`; a booking created from a post points at its post. This is how the live-booking count is done.
- **Edit C — `booking/BookingService.java` (Module 4):** two small hooks — when a booking is rejected or cancelled, if it has a `loadPost`, refresh that post's status (Part 9). Without these, a rejected truck silently eats a slot forever.

> **Check Modules 2–4 first:** this module calls `truck.getPayloadTons()`, `truck.getBodyType()`, `truck.getCategory()`, `truck.getOwner()`, `user.getPhone()`, `user.getFullName()`, `user.getRole()`, and `BookingStatus.ACTIVE` — all exactly as Modules 2–4 use them. **Flag — adjust if your Module 2 differs:** Module 2's original `Truck` entity named the payload field `permittedPayloadTons` with getter `getPermittedPayloadTons()`, while Modules 3–4 use `getPayloadTons()`. If you kept the original name, change only those calls in this module to `getPermittedPayloadTons()` — the design does not change. Do not create a second payload field.

---

## Part 6 — The entities, repositories and the state machine

### File 1 of 11 — `loadpost/LoadPostStatus.java`

**Simple meaning:** The list of statuses, plus the table from Part 3 written as data. `canMoveTo(from, to)` is the one question the whole lifecycle asks — the same pattern as Module 4's `BookingStatus`.

```java
package com.truckconnect.loadpost;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum LoadPostStatus {
    OPEN,
    PARTIALLY_BOOKED,
    FULLY_BOOKED,
    CANCELLED,
    EXPIRED;

    /** The transition table from Part 3. A move not listed here is refused. */
    private static final Map<LoadPostStatus, Set<LoadPostStatus>> ALLOWED =
            new EnumMap<>(LoadPostStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(PARTIALLY_BOOKED, FULLY_BOOKED,
                CANCELLED, EXPIRED));
        ALLOWED.put(PARTIALLY_BOOKED, EnumSet.of(FULLY_BOOKED,
                CANCELLED, EXPIRED));
        ALLOWED.put(FULLY_BOOKED, EnumSet.of(CANCELLED));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(LoadPostStatus.class));
        ALLOWED.put(EXPIRED, EnumSet.noneOf(LoadPostStatus.class));
    }

    public static boolean canMoveTo(LoadPostStatus from, LoadPostStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Statuses in which trucks can still be booked from the post. */
    public static boolean isBookable(LoadPostStatus status) {
        return status == OPEN || status == PARTIALLY_BOOKED;
    }

    public static boolean isFinal(LoadPostStatus status) {
        return status == CANCELLED || status == EXPIRED;
    }
}
```

### File 2 of 11 — `loadpost/LoadPost.java`

**Simple meaning:** One row is one notice on the board. The top half is links (who posted it, which category). The middle is the load itself — and note `tonsPerTruck`: it is a stored column, calculated once at posting time by the service, so every later read (matching, pricing, the frontend) uses the same number and nobody re-divides with different rounding. The bottom is the lifecycle: status and dates. There is **no** stored `trucksBooked` column — the count is always computed from live bookings (Part 8), so it can never drift out of sync with reality.

```java
package com.truckconnect.loadpost;

import com.truckconnect.truck.BodyType;
import com.truckconnect.truck.TruckCategory;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "load_posts")
public class LoadPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posted_by_id")
    private User postedBy;

    @Column(name = "goods_description", nullable = false, length = 200)
    private String goodsDescription;

    @Column(name = "total_tons", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTons;

    @Column(name = "trucks_needed", nullable = false)
    private int trucksNeeded;

    // Calculated once at posting: totalTons / trucksNeeded. Never typed by the client.
    @Column(name = "tons_per_truck", nullable = false, precision = 10, scale = 2)
    private BigDecimal tonsPerTruck;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private TruckCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", length = 20)
    private BodyType bodyType; // null = any body

    @Column(nullable = false, length = 120)
    private String pickup;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "toll_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal tollCharges = BigDecimal.ZERO;

    @Column(name = "loading_charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal loadingCharges = BigDecimal.ZERO;

    @Column(name = "contact_phone", length = 15)
    private String contactPhone; // null = use postedBy's phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoadPostStatus status = LoadPostStatus.OPEN;

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

    public LoadPost() { }

    // Getters and setters for every field (same pattern as Modules 1-4).
    public Long getId() { return id; }
    public User getPostedBy() { return postedBy; }
    public void setPostedBy(User u) { this.postedBy = u; }
    public String getGoodsDescription() { return goodsDescription; }
    public void setGoodsDescription(String v) { this.goodsDescription = v; }
    public BigDecimal getTotalTons() { return totalTons; }
    public void setTotalTons(BigDecimal v) { this.totalTons = v; }
    public int getTrucksNeeded() { return trucksNeeded; }
    public void setTrucksNeeded(int v) { this.trucksNeeded = v; }
    public BigDecimal getTonsPerTruck() { return tonsPerTruck; }
    public void setTonsPerTruck(BigDecimal v) { this.tonsPerTruck = v; }
    public TruckCategory getCategory() { return category; }
    public void setCategory(TruckCategory c) { this.category = c; }
    public BodyType getBodyType() { return bodyType; }
    public void setBodyType(BodyType b) { this.bodyType = b; }
    public String getPickup() { return pickup; }
    public void setPickup(String p) { this.pickup = p; }
    public String getDestination() { return destination; }
    public void setDestination(String d) { this.destination = d; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal v) { this.distanceKm = v; }
    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate d) { this.pickupDate = d; }
    public BigDecimal getTollCharges() { return tollCharges; }
    public void setTollCharges(BigDecimal v) { this.tollCharges = v; }
    public BigDecimal getLoadingCharges() { return loadingCharges; }
    public void setLoadingCharges(BigDecimal v) { this.loadingCharges = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String p) { this.contactPhone = p; }
    public LoadPostStatus getStatus() { return status; }
    public void setStatus(LoadPostStatus s) { this.status = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

### File 3 of 11 — `loadpost/LoadPostRepository.java`

**Simple meaning:** Method names are the queries. The last one is the pessimistic lock for the post row — the same `@Lock` + `@Query` pattern as Module 4's `TruckRepository.findByIdForUpdate`.

```java
package com.truckconnect.loadpost;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadPostRepository extends JpaRepository<LoadPost, Long> {

    /** The board: open posts, newest first. */
    List<LoadPost> findByStatusInOrderByCreatedAtDesc(
            java.util.Collection<LoadPostStatus> statuses);

    List<LoadPost> findByPostedByIdOrderByCreatedAtDesc(Long postedById);

    /** Posts whose pickup date has passed while trucks were still needed. */
    List<LoadPost> findByStatusInAndPickupDateBefore(
            java.util.Collection<LoadPostStatus> statuses, LocalDate date);

    /** Lock the post row until the transaction ends — the over-booking guard. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LoadPost p where p.id = :id")
    Optional<LoadPost> findByIdForUpdate(@Param("id") Long id);
}
```

---

## Part 7 — The DTOs and the mapper

### File 4 of 11 — `loadpost/dto/CreateLoadPostRequest.java`

**Simple meaning:** What the factory sends. Notice what is **not** here: no `tonsPerTruck` (the service calculates it), no `status` (the service sets `OPEN`), no `postedBy` (the JWT supplies it), no price of any kind (Module 3 calculates it). A client cannot smuggle any of those in, because the object has no field for them.

```java
package com.truckconnect.loadpost.dto;

import com.truckconnect.truck.BodyType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLoadPostRequest(
        @NotBlank @Size(min = 3, max = 200) String goodsDescription,
        @NotNull @DecimalMin("0.5") @DecimalMax("500") BigDecimal totalTons,
        @NotNull @Min(1) @Max(25) int trucksNeeded,
        @NotNull Long categoryId,
        BodyType bodyType,               // optional: null means "any body"
        @NotBlank String pickup,
        @NotBlank String destination,
        @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal distanceKm,
        @NotNull @FutureOrPresent LocalDate pickupDate,
        @DecimalMin("0") BigDecimal tollCharges,     // per truck, optional
        @DecimalMin("0") BigDecimal loadingCharges,  // per truck, optional
        @Pattern(regexp = "^[6-9][0-9]{9}$",
                 message = "Phone must be a 10-digit Indian mobile number")
        String contactPhone              // optional: null = use account phone
) { }
```

> **Check Module 1 first:** the phone pattern above (`^[6-9][0-9]{9}$`) is the same rule Module 1 uses for `User.phone`. If your Module 1 used a different pattern, copy that one here instead of this one — one phone rule in the whole project.

### File 5 of 11 — `loadpost/dto/LoadPostResponse.java`

**Simple meaning:** Everything the board and the factory's "my posts" page need: the post itself, how many trucks are booked, how many are still needed, and the per-truck share. `trucksBooked` and `trucksRemaining` are computed by the service from live bookings — they are numbers in the response, not columns in the table.

```java
package com.truckconnect.loadpost.dto;

import com.truckconnect.loadpost.LoadPostStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LoadPostResponse(
        Long postId,
        Long postedById,
        String postedByName,
        String goodsDescription,
        BigDecimal totalTons,
        int trucksNeeded,
        BigDecimal tonsPerTruck,
        String categoryName,
        int tyreCount,
        String bodyType,
        String pickup,
        String destination,
        BigDecimal distanceKm,
        LocalDate pickupDate,
        BigDecimal tollCharges,
        BigDecimal loadingCharges,
        LoadPostStatus status,
        int trucksBooked,
        int trucksRemaining,
        LocalDateTime createdAt
) { }
```

### File 6 of 11 — `loadpost/dto/LoadPostMatchResponse.java`

**Simple meaning:** One matching truck, with everything the factory needs to choose it: the truck, the Module 3 quote that prices it, the fare breakdown, and — at the bottom — the summary for the whole post. The summary is computed from the **cheapest** match: "cheapest per-truck fare × trucks still needed". It is labelled an estimate in the field name itself (`estimatedTotalForRemainingTrucks`), so no frontend can mistake it for a final total.

```java
package com.truckconnect.loadpost.dto;

import com.truckconnect.quote.dto.QuoteResponse;
import java.math.BigDecimal;
import java.util.List;

public record LoadPostMatchResponse(
        Long postId,
        BigDecimal tonsPerTruck,
        int trucksNeeded,
        int trucksBooked,
        int trucksRemaining,
        List<Match> matches,
        BigDecimal cheapestPerTruckFare,          // null when no matches
        BigDecimal estimatedTotalForRemainingTrucks, // cheapest x remaining; estimate only
        String estimateNote
) {
    /** One matching truck: the truck, its quote, and its fare. */
    public record Match(
            Long truckId,
            String registrationNumber,
            String categoryName,
            int tyreCount,
            BigDecimal payloadTons,
            String bodyType,
            String ownerName,
            QuoteResponse quote
    ) { }
}
```

### File 7 of 11 — `loadpost/dto/BookFromLoadPostRequest.java`

**Simple meaning:** The factory sends exactly one thing — which quotes it is booking. No prices, no truck ids, no counts. The quotes already carry the truck, the price, and (after Edit A) the post they came from, so the service can verify all three from the database. A forged request cannot change any of them.

```java
package com.truckconnect.loadpost.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BookFromLoadPostRequest(
        @NotEmpty(message = "Choose at least one truck to book")
        @Size(max = 25, message = "Cannot book more than 25 trucks at once")
        List<Long> quoteIds
) { }
```

### File 8 of 11 — `loadpost/LoadPostMapper.java`

**Simple meaning:** One class, one job: turn a `LoadPost` entity into a `LoadPostResponse`. The counts come in as parameters because the mapper does not query the database — the service counts first, then maps. Controllers never build responses by hand, and the service never returns entities to the outside world (same rule as Module 4).

```java
package com.truckconnect.loadpost;

import com.truckconnect.loadpost.dto.LoadPostResponse;

public final class LoadPostMapper {

    private LoadPostMapper() { }

    public static LoadPostResponse toResponse(LoadPost p,
                                              int trucksBooked) {
        return new LoadPostResponse(
                p.getId(),
                p.getPostedBy().getId(),
                p.getPostedBy().getFullName(),
                p.getGoodsDescription(),
                p.getTotalTons(),
                p.getTrucksNeeded(),
                p.getTonsPerTruck(),
                p.getCategory().getName(),
                p.getCategory().getTyreCount(),
                p.getBodyType() == null ? null : p.getBodyType().name(),
                p.getPickup(),
                p.getDestination(),
                p.getDistanceKm(),
                p.getPickupDate(),
                p.getTollCharges(),
                p.getLoadingCharges(),
                p.getStatus(),
                trucksBooked,
                p.getTrucksNeeded() - trucksBooked,
                p.getCreatedAt());
    }
}
```

> **Lazy loading warning:** `toResponse` touches `p.getPostedBy()` and `p.getCategory()`. Those are `LAZY` links, so this method must run **inside a transaction** (the service methods are `@Transactional`), or Hibernate will throw a `LazyInitializationException`. Same rule as Module 4's `BookingMapper` — keep the mapping inside the service method, never make the links `EAGER`.

---

## Part 8 — The service: where every rule lives

### File 9 of 11 — `loadpost/LoadPostService.java`

**Simple meaning:** This is the brain. Read it top to bottom: post (with the even-division check), read the board, match (by calling Module 3), book (by calling Module 4's steps, inside one transaction with the post locked), cancel, expire, and the small helpers — counting live bookings, moving the status, and the ownership checks. Errors use `ResponseStatusException` with the right HTTP status, exactly as Module 4 does: `400` bad input, `403` not yours, `404` not found, `409` not allowed in this state / too many trucks.

```java
package com.truckconnect.loadpost;

import com.truckconnect.booking.*;
import com.truckconnect.booking.dto.BookingResponse;
import com.truckconnect.loadpost.dto.*;
import com.truckconnect.quote.Quote;
import com.truckconnect.quote.QuoteRepository;
import com.truckconnect.quote.SearchService;
import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.truck.TruckCategory;
import com.truckconnect.truck.TruckCategoryRepository;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class LoadPostService {

    private final LoadPostRepository postRepository;
    private final BookingRepository bookingRepository;
    private final QuoteRepository quoteRepository;
    private final TruckCategoryRepository categoryRepository;
    private final TruckRepository truckRepository;
    private final SearchService searchService;
    private final BookingService bookingService;

    public LoadPostService(LoadPostRepository postRepository,
                           BookingRepository bookingRepository,
                           QuoteRepository quoteRepository,
                           TruckCategoryRepository categoryRepository,
                           TruckRepository truckRepository,
                           SearchService searchService,
                           BookingService bookingService) {
        this.postRepository = postRepository;
        this.bookingRepository = bookingRepository;
        this.quoteRepository = quoteRepository;
        this.categoryRepository = categoryRepository;
        this.truckRepository = truckRepository;
        this.searchService = searchService;
        this.bookingService = bookingService;
    }

    // ---------- Post a load ----------

    @Transactional
    public LoadPostResponse createPost(User poster, CreateLoadPostRequest req) {
        TruckCategory category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found: " + req.categoryId()));

        // Rule: total tons must divide evenly across the trucks needed.
        // 60 / 3 = 20.00 is fine; 50 / 3 = 16.67 is refused, not rounded.
        BigDecimal tonsPerTruck = req.totalTons().divide(
                BigDecimal.valueOf(req.trucksNeeded()), 2, RoundingMode.HALF_UP);
        BigDecimal check = tonsPerTruck.multiply(
                BigDecimal.valueOf(req.trucksNeeded()));
        if (check.compareTo(req.totalTons()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total tons (" + req.totalTons() + ") must divide evenly across "
                    + req.trucksNeeded() + " trucks. Try "
                    + suggestEvenSplit(req.totalTons(), req.trucksNeeded()));
        }
        // Rule: one truck's share must fit inside the category it asked for.
        // A 12-tyre category targets 20 tons; a 25-ton share cannot go in it.
        if (tonsPerTruck.compareTo(category.getTargetPayloadTons()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each truck would carry " + tonsPerTruck + " tons, but the "
                    + category.getName() + " category targets "
                    + category.getTargetPayloadTons() + " tons. "
                    + "Pick a bigger category or more trucks.");
        }

        LoadPost post = new LoadPost();
        post.setPostedBy(poster);
        post.setGoodsDescription(req.goodsDescription().trim());
        post.setTotalTons(req.totalTons());
        post.setTrucksNeeded(req.trucksNeeded());
        post.setTonsPerTruck(tonsPerTruck);
        post.setCategory(category);
        post.setBodyType(req.bodyType());
        post.setPickup(req.pickup().trim());
        post.setDestination(req.destination().trim());
        post.setDistanceKm(req.distanceKm());
        post.setPickupDate(req.pickupDate());
        post.setTollCharges(req.tollCharges() == null
                ? BigDecimal.ZERO : req.tollCharges());
        post.setLoadingCharges(req.loadingCharges() == null
                ? BigDecimal.ZERO : req.loadingCharges());
        // No phone typed in the form? Use the poster's account phone.
        post.setContactPhone(req.contactPhone() == null
                ? poster.getPhone() : req.contactPhone());
        post.setStatus(LoadPostStatus.OPEN);
        post = postRepository.save(post);

        return LoadPostMapper.toResponse(post, 0);
    }

    /** Small helper: suggest the nearest even splits, e.g. "48 tons (16 each), 51 tons (17 each)". */
    private String suggestEvenSplit(BigDecimal total, int trucks) {
        BigDecimal perTruck = total.divide(
                BigDecimal.valueOf(trucks), 0, RoundingMode.DOWN);
        BigDecimal lower = perTruck.multiply(BigDecimal.valueOf(trucks));
        BigDecimal upper = perTruck.add(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(trucks));
        return lower + " tons (" + perTruck + " each) or "
                + upper + " tons (" + perTruck.add(BigDecimal.ONE) + " each)";
    }

    // ---------- Read: the board, my posts, one post ----------

    @Transactional
    public List<LoadPostResponse> openPosts() {
        expireOverduePosts();
        return postRepository.findByStatusInOrderByCreatedAtDesc(
                        List.of(LoadPostStatus.OPEN, LoadPostStatus.PARTIALLY_BOOKED))
                .stream().map(p -> LoadPostMapper.toResponse(p, countLiveBookings(p)))
                .toList();
    }

    @Transactional
    public List<LoadPostResponse> myPosts(User poster) {
        expireOverduePosts();
        return postRepository.findByPostedByIdOrderByCreatedAtDesc(poster.getId())
                .stream().map(p -> LoadPostMapper.toResponse(p, countLiveBookings(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadPostResponse getPost(User user, Long postId) {
        LoadPost post = loadPost(postId);
        return LoadPostMapper.toResponse(post, countLiveBookings(post));
    }

    // ---------- Match: Module 3 search, once, for one truck's share ----------

    /**
     * Runs the Module 3 search with load = tonsPerTruck, then tags each
     * returned quote with this post so bookFromPost can prove the quote
     * came from here. The price formula is Module 3's — nothing is
     * recalculated here.
     */
    @Transactional
    public LoadPostMatchResponse matches(User user, Long postId) {
        LoadPost post = loadPost(postId);
        if (!LoadPostStatus.isBookable(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is " + post.getStatus()
                    + " — no more trucks can be booked from it");
        }
        expireOverduePosts();
        post = loadPost(postId); // re-read: expiry may have changed the status
        if (!LoadPostStatus.isBookable(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is " + post.getStatus()
                    + " — the pickup date has passed");
        }

        SearchRequest search = new SearchRequest(
                post.getPickup(), post.getDestination(), post.getDistanceKm(),
                post.getTonsPerTruck(), post.getGoodsDescription(),
                post.getBodyType(), post.getCategory().getId(),
                post.getTollCharges(), post.getLoadingCharges());

        // The poster searches as themselves; an owner browsing matches
        // searches as themselves too — SearchService only needs a User
        // to stamp the quotes with. Any logged-in user may call this.
        List<QuoteResponse> quotes = searchService.search(user, search);

        // Tag the quotes with this post (Edit A) and build the match list.
        int remaining = post.getTrucksNeeded() - countLiveBookings(post);
        List<LoadPostMatchResponse.Match> matchList = quotes.stream().map(q -> {
            Quote quote = quoteRepository.findById(q.quoteId()).orElseThrow();
            quote.setLoadPost(post);
            quoteRepository.save(quote);
            return new LoadPostMatchResponse.Match(
                    q.truckId(), q.registrationNumber(), q.categoryName(),
                    q.tyreCount(), q.payloadTons(), q.bodyType(),
                    ownerNameOf(q.truckId()), q);
        }).toList();

        BigDecimal cheapest = matchList.stream()
                .map(m -> m.quote().totalFare())
                .min(BigDecimal::compareTo).orElse(null);
        BigDecimal estimatedTotal = cheapest == null ? null
                : cheapest.multiply(BigDecimal.valueOf(remaining));
        String note = cheapest == null
                ? "No available truck can carry " + post.getTonsPerTruck()
                        + " tons right now — try again later or change the category."
                : "Estimate only: " + remaining + " truck(s) still needed x cheapest "
                        + "per-truck fare " + cheapest
                        + ". The final total is the sum of the bookings actually created.";

        return new LoadPostMatchResponse(post.getId(), post.getTonsPerTruck(),
                post.getTrucksNeeded(), countLiveBookings(post), remaining,
                matchList, cheapest, estimatedTotal, note);
    }

    // ---------- Book: one Module 4 booking per truck, in one transaction ----------

    /**
     * Books 1..N trucks from a post. The whole method is ONE transaction:
     * the post row is locked first, the live count is checked, then each
     * quote is booked through BookingService.createBooking — the exact
     * Module 4 path, so the snapshot, the truck lock, and the
     * no-double-booking guard all apply unchanged. If any quote fails,
     * nothing is booked (the transaction rolls back).
     */
    @Transactional
    public List<BookingResponse> bookFromPost(User poster, Long postId,
                                              BookFromLoadPostRequest req) {
        LoadPost post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Load post not found: " + postId));
        requirePoster(poster, post);
        if (!LoadPostStatus.isBookable(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is " + post.getStatus()
                    + " — no more trucks can be booked from it");
        }
        if (post.getPickupDate().isBefore(LocalDate.now())) {
            moveStatus(post, LoadPostStatus.EXPIRED);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post expired — the pickup date has passed");
        }

        // Over-booking guard: live bookings + this request must not exceed need.
        int live = countLiveBookings(post);
        int remaining = post.getTrucksNeeded() - live;
        if (req.quoteIds().size() > remaining) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This load needs " + post.getTrucksNeeded() + " trucks, "
                    + live + " are already booked — you can book at most "
                    + remaining + " more");
        }
        // No quote twice in one request — booking the same truck twice
        // would fail in Module 4 anyway, but fail fast with a clear message.
        if (req.quoteIds().stream().distinct().count() != req.quoteIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The same quote appears twice — each truck can be booked once");
        }

        List<BookingResponse> created = new java.util.ArrayList<>();
        for (Long quoteId : req.quoteIds()) {
            Quote quote = quoteRepository.findById(quoteId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Quote not found: " + quoteId));
            // The quote must come from THIS post's matches, belong to the
            // poster, and still be usable — Module 4 checks the last two,
            // this module adds the first.
            if (quote.getLoadPost() == null
                    || !quote.getLoadPost().getId().equals(post.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Quote " + quoteId + " was not created from load post "
                        + post.getId() + " — get fresh matches first");
            }
            if (!quote.getCustomer().getId().equals(poster.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This quote does not belong to you");
            }
            // The poster may not book their own truck (Part 4, Rule 4).
            if (quote.getTruck().getOwner().getId().equals(poster.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You cannot book your own truck");
            }
            BookingResponse booking = bookingService.createBooking(poster,
                    new com.truckconnect.booking.dto.CreateBookingRequest(quoteId));
            // Link the new booking to this post (Edit B) so the live count
            // and the factory's "2 of 3 booked" view work.
            Booking saved = bookingRepository.findById(booking.bookingId())
                    .orElseThrow();
            saved.setLoadPost(post);
            bookingRepository.save(saved);
            created.add(booking);
        }

        // Move the post status from the fresh count, through the state machine.
        refreshPostStatus(post);
        return created;
    }

    // ---------- Cancel a post ----------

    @Transactional
    public LoadPostResponse cancelPost(User user, Long postId) {
        LoadPost post = loadPost(postId);
        if (user.getRole() != Role.ADMIN) {
            requirePoster(user, post);
        }
        if (post.getStatus() == LoadPostStatus.FULLY_BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This load is fully booked — cancel its bookings one by one "
                    + "through Module 4; the post itself has nothing left to cancel");
        }
        if (LoadPostStatus.isFinal(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is already " + post.getStatus());
        }
        // Bookings already made stay booked — cancelling the post only stops
        // further bookings. Their owners have already committed.
        moveStatus(post, LoadPostStatus.CANCELLED);
        return LoadPostMapper.toResponse(post, countLiveBookings(post));
    }

    // ---------- Owner view: posts my trucks can carry ----------

    @Transactional(readOnly = true)
    public List<LoadPostResponse> matchingForOwner(User owner) {
        expireOverduePosts();
        // An owner sees an open post if ANY of their AVAILABLE trucks could
        // carry one share of it: same category, payload >= tonsPerTruck,
        // body matches if the post asked for one. The check runs in Java
        // over the owner's trucks — simple and fast enough for an MVP.
        List<com.truckconnect.truck.Truck> myTrucks =
                truckRepository.findByOwnerIdAndAvailabilityStatus(
                        owner.getId(),
                        com.truckconnect.truck.AvailabilityStatus.AVAILABLE);
        return postRepository.findByStatusInOrderByCreatedAtDesc(
                        List.of(LoadPostStatus.OPEN, LoadPostStatus.PARTIALLY_BOOKED))
                .stream()
                .filter(p -> myTrucks.stream().anyMatch(t ->
                        t.getCategory().getId().equals(p.getCategory().getId())
                        && t.getPayloadTons().compareTo(p.getTonsPerTruck()) >= 0
                        && (p.getBodyType() == null
                                || t.getBodyType() == p.getBodyType())))
                .map(p -> LoadPostMapper.toResponse(p, countLiveBookings(p)))
                .toList();
    }

    // ---------- Helpers: counting, status, expiry, ownership ----------

    /** Live bookings linked to this post (Module 4 ACTIVE set). */
    public int countLiveBookings(LoadPost post) {
        return bookingRepository.countByLoadPostIdAndStatusIn(
                post.getId(), BookingStatus.ACTIVE);
    }

    /** Recompute the post status from the live count — the ONLY place
     *  the status moves because of a booking. Called after every booking,
     *  rejection and cancellation that touches this post (see Edit C). */
    @Transactional
    public void refreshPostStatus(LoadPost post) {
        if (LoadPostStatus.isFinal(post.getStatus())) return;
        int live = countLiveBookings(post);
        LoadPostStatus target = live == 0 ? LoadPostStatus.OPEN
                : live >= post.getTrucksNeeded() ? LoadPostStatus.FULLY_BOOKED
                : LoadPostStatus.PARTIALLY_BOOKED;
        moveStatus(post, target);
    }

    /** The ONLY place a post's status changes. Every move is checked here. */
    private void moveStatus(LoadPost post, LoadPostStatus to) {
        if (post.getStatus() == to) return; // already there — nothing to do
        if (!LoadPostStatus.canMoveTo(post.getStatus(), to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid load-post status change from "
                    + post.getStatus() + " to " + to);
        }
        post.setStatus(to);
        postRepository.save(post);
    }

    /** Any post whose pickup date passed while trucks were still needed. */
    private void expireOverduePosts() {
        List<LoadPost> overdue = postRepository.findByStatusInAndPickupDateBefore(
                List.of(LoadPostStatus.OPEN, LoadPostStatus.PARTIALLY_BOOKED),
                LocalDate.now());
        for (LoadPost p : overdue) {
            moveStatus(p, LoadPostStatus.EXPIRED);
        }
    }

    private LoadPost loadPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Load post not found: " + postId));
    }

    private void requirePoster(User user, LoadPost post) {
        if (!post.getPostedBy().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This load post is not yours");
        }
    }

    private String ownerNameOf(Long truckId) {
        return truckRepository.findById(truckId)
                .map(t -> t.getOwner().getFullName()).orElse("Unknown owner");
    }
}
```

Four things to notice in this code:

1. **`matches()` calls `searchService.search(...)`, it does not price anything itself.** The `SearchRequest` is built from the post — `loadTons` is `tonsPerTruck`, the category is the post's category, the body type is the post's body type. The quotes that come back are ordinary Module 3 quotes, saved in the `quotes` table with a 24-hour `validUntil`, and then tagged with the post. If the factory waits more than 24 hours to book, the quotes expire and Module 4 refuses them — the factory gets fresh matches. That is the same rule as a single-truck search, not a new one.
2. **`bookFromPost` locks the post, counts, then books — in that order, in one transaction.** The count check and the bookings are unbroken: no second request can slip in between the count and the booking, because the second request waits on the post lock. The truck-level lock inside `createBooking` is the second layer (Part 4, Rule 2).
3. **The `suggestEvenSplit` message does real work.** A `400` that only says "must divide evenly" leaves the factory guessing. The message names the two nearest workable totals — for 50 tons / 3 trucks it says "48 tons (16 each) or 51 tons (17 each)". Small thing, big difference for a user typing on a phone.
4. **Two repository methods are assumed here and must exist:** `BookingRepository.countByLoadPostIdAndStatusIn(Long, Collection<BookingStatus>)` (add it next to Module 4's `existsByTruckIdAndStatusIn`) and `TruckRepository.findByOwnerIdAndAvailabilityStatus(Long, AvailabilityStatus)` (add it next to Module 2's `findByAvailabilityStatus`). Both are one-line method names — Spring Data writes the queries. And `LoadPostService` needs a `TruckRepository` field — add it to the constructor alongside the others (it is used by `matchingForOwner` and `ownerNameOf`).

> **Check Module 3 first:** `searchService.search(user, search)` stamps the quotes with the `user` passed in. When an owner browses matches for a post that is not theirs, the quotes are stamped with the *owner's* id — and then `bookFromPost` refuses them, because the quote's customer is not the poster. That is correct behaviour (only the poster books), but it means an owner's browsing creates quote rows under the owner's name. Harmless — quotes are cheap rows — but do not be surprised to see them in the `quotes` table. If it bothers you, the owner view (`matchingForOwner`) returns posts without quotes, and only the poster calls `matches()` — that is the tidy version, and it is already how the controllers below are wired: the owner controller has no matches endpoint.

---

## Part 8 (continued) — The controllers

### File 10 of 11 — `loadpost/LoadPostController.java`

**Simple meaning:** The factory's doors, plus the board everyone reads. Role checks are `@PreAuthorize` on the method; ownership checks are in the service, because a role check can say "you are a customer" but only the service knows *whose* post this is. Note the admin has no book endpoint here — deliberately (Part 4, Rule 4).

```java
package com.truckconnect.loadpost;

import com.truckconnect.booking.dto.BookingResponse;
import com.truckconnect.loadpost.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/load-posts")
public class LoadPostController {

    private final LoadPostService loadPostService;

    public LoadPostController(LoadPostService loadPostService) {
        this.loadPostService = loadPostService;
    }

    /** Pin a notice on the board. CUSTOMER (person or business) or TRANSPORTER. */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER')")
    public ResponseEntity<LoadPostResponse> create(
            @AuthenticationPrincipal User poster,
            @Valid @RequestBody CreateLoadPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadPostService.createPost(poster, request));
    }

    /** The board: open posts, newest first. Any logged-in user. */
    @GetMapping("/open")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LoadPostResponse>> openPosts() {
        return ResponseEntity.ok(loadPostService.openPosts());
    }

    /** My own posts, newest first. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER')")
    public ResponseEntity<List<LoadPostResponse>> myPosts(
            @AuthenticationPrincipal User poster) {
        return ResponseEntity.ok(loadPostService.myPosts(poster));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoadPostResponse> getOne(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(loadPostService.getPost(user, id));
    }

    /** Matching trucks + instant prices for one post, cheapest first. */
    @GetMapping("/{id}/matches")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER', 'OWNER', 'ADMIN')")
    public ResponseEntity<LoadPostMatchResponse> matches(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(loadPostService.matches(user, id));
    }

    /** Book 1..N trucks from this post, instantly. Poster only. */
    @PostMapping("/{id}/book")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER')")
    public ResponseEntity<List<BookingResponse>> book(
            @AuthenticationPrincipal User poster,
            @PathVariable Long id,
            @Valid @RequestBody BookFromLoadPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadPostService.bookFromPost(poster, id, request));
    }

    /** Take the notice down. Poster or admin. Bookings already made stay booked. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<LoadPostResponse> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(loadPostService.cancelPost(user, id));
    }
}
```

### File 11 of 11 — `loadpost/OwnerLoadPostController.java`

**Simple meaning:** The owner's door to the board. One endpoint: show me only the open posts my trucks can actually carry. No quotes, no prices, no phone numbers here — the owner sees the load, and if they want the job they wait for the factory to book their truck (booking is the factory's action, through `matches` + `book`). The phone number appears later, on the booking, exactly as in Module 4.

```java
package com.truckconnect.loadpost;

import com.truckconnect.loadpost.dto.LoadPostResponse;
import com.truckconnect.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/load-posts")
@PreAuthorize("hasRole('OWNER')")
public class OwnerLoadPostController {

    private final LoadPostService loadPostService;

    public OwnerLoadPostController(LoadPostService loadPostService) {
        this.loadPostService = loadPostService;
    }

    /** Open posts at least one of my AVAILABLE trucks can carry. */
    @GetMapping("/matching")
    public ResponseEntity<List<LoadPostResponse>> matching(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(loadPostService.matchingForOwner(owner));
    }
}
```

---

## Part 9 — The three small edits (do these first)

### Edit A — `quote/Quote.java`: link a quote to the post it came from

Add this field to the Module 3 `Quote` entity (with getter and setter, same pattern as the other fields). It is nullable: a normal Module 3 search quote has `loadPost = null`.

```java
import com.truckconnect.loadpost.LoadPost;

// ... inside the Quote class, after the truck field:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "load_post_id")
private LoadPost loadPost;

public LoadPost getLoadPost() { return loadPost; }
public void setLoadPost(LoadPost p) { this.loadPost = p; }
```

### Edit B — `booking/Booking.java`: link a booking to the post it came from

Add this field to the Module 4 `Booking` entity (with getter and setter). It is nullable: a normal Module 4 booking has `loadPost = null`.

```java
import com.truckconnect.loadpost.LoadPost;

// ... inside the Booking class, after the quote field:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "load_post_id")
private LoadPost loadPost;

public LoadPost getLoadPost() { return loadPost; }
public void setLoadPost(LoadPost p) { this.loadPost = p; }
```

### Edit C — `booking/BookingService.java`: refresh the post when a booking is rejected or cancelled

Without this, a rejected truck eats its slot forever: the live count never drops, the post stays `FULLY_BOOKED`, and the factory can never fill its load. Add one line at the end of `rejectBooking` and one at the end of `doCancel`, and inject `LoadPostService` into `BookingService` — but **careful: `LoadPostService` already depends on `BookingService`**, so a plain constructor injection would be a circular dependency and Spring will refuse to start. Break the circle with `@Lazy` on the injected `LoadPostService`:

```java
// In BookingService, add the field + constructor parameter:
import org.springframework.context.annotation.Lazy;
import com.truckconnect.loadpost.LoadPostService;

private final LoadPostService loadPostService;

public BookingService(BookingRepository bookingRepository,
                      BookingMessageRepository messageRepository,
                      QuoteRepository quoteRepository,
                      TruckRepository truckRepository,
                      @Lazy LoadPostService loadPostService) {
    // ... existing assignments ...
    this.loadPostService = loadPostService;
}

// At the end of rejectBooking(...), after changeStatus(...):
if (booking.getLoadPost() != null) {
    loadPostService.refreshPostStatus(booking.getLoadPost());
}

// At the end of doCancel(...), after the truck is released:
if (booking.getLoadPost() != null) {
    loadPostService.refreshPostStatus(booking.getLoadPost());
}
```

> **Why `@Lazy` and not a restructure?** The clean restructure would move the refresh into an event (`ApplicationEventPublisher`), and that is the right answer for a bigger team. For a beginner working alone, `@Lazy` is one annotation, it is honest about what it does (Spring builds a proxy and only loads the real service when first used), and it is flagged here so you know it is a shortcut, not a pattern to copy everywhere. If you later add events, delete the `@Lazy` injection and publish a `BookingRejectedEvent` / `BookingCancelledEvent` instead — `refreshPostStatus` stays exactly as it is.

### Two one-line repository additions

Add these next to the methods already in those repositories — Spring Data writes both queries from the names:

```java
// In booking/BookingRepository.java (Module 4), next to existsByTruckIdAndStatusIn:
int countByLoadPostIdAndStatusIn(Long loadPostId,
        java.util.Collection<BookingStatus> statuses);

// In truck/TruckRepository.java (Module 2), next to findByAvailabilityStatus:
List<Truck> findByOwnerIdAndAvailabilityStatus(Long ownerId,
        AvailabilityStatus status);
```

---

## Part 10 — Test checklist (do these in order)

You need: the app running, one CUSTOMER token (the factory), a second CUSTOMER token (a stranger), one TRANSPORTER token, one OWNER token (owns at least three 12-tyre trucks, payload 20 tons, all `AVAILABLE`), one ADMIN token. Use the Module 3 worked example numbers — Bengaluru → Hyderabad, 570 km, toll ₹1,500, loading ₹1,000, platform fee ₹0.

| # | Test | Expected result |
| --- | --- | --- |
| 1 | `POST /api/load-posts` as CUSTOMER: steel coils, 60 tons, 3 trucks, 12-tyre category, OPEN body, 570 km, pickup tomorrow. | `201 Created`. Status `OPEN`, `tonsPerTruck` 20, `trucksBooked` 0, `trucksRemaining` 3. |
| 2 | Same post with 50 tons, 3 trucks. | `400 Bad Request` — "must divide evenly", with the suggestion ("48 tons (16 each) or 51 tons (17 each)"). No post is saved — check `GET /api/load-posts/mine`, it is not there. |
| 3 | Same post with 75 tons, 3 trucks, 12-tyre category (25 tons per truck). | `400` — each truck would carry 25 tons but the 12-tyre category targets 20 tons. Post with the 14-tyre category instead and it succeeds (25 tons per truck). |
| 4 | `GET /api/load-posts/{id}/matches` on the 60-ton post. | The three 12-tyre trucks returned, cheapest first. Each quote: per-truck total **₹33,280** (570 × 2.70 × 20 = 30,780 + 1,500 + 1,000 + 0). Summary: cheapest per-truck fare ₹33,280, estimated total for 3 trucks **₹99,840**, labelled as an estimate. |
| 5 | `POST /api/load-posts/{id}/book { "quoteIds": [<first two>] }` as the factory. | `201 Created`, 2 bookings returned, each `PENDING`, each total ₹33,280, each with its own booking id. `GET /api/load-posts/{id}` now says `PARTIALLY_BOOKED`, 2 booked, **1 remaining**. |
| 6 | Over-booking: book the same post again with 2 quote ids (only 1 slot left). | `409 Conflict` — "you can book at most 1 more". **Nothing** is booked: `GET /api/load-posts/{id}` still says 2 booked, and no new booking appears in `GET /api/bookings/mine`. |
| 7 | Book the last slot with 1 fresh quote id (get fresh matches first — the old quotes from test 4 are now `BOOKED`). | `201`, 1 booking. Post is now `FULLY_BOOKED`, 0 remaining. `GET /api/load-posts/open` no longer lists it. |
| 8 | Try to book from the `FULLY_BOOKED` post. | `409` — post is `FULLY_BOOKED`. |
| 9 | Owner rejects one of the three bookings (`POST /api/owner/bookings/{id}/reject { "reason": "Driver on leave" }`). | Booking is `REJECTED`. The post drops back to `PARTIALLY_BOOKED`, 2 booked, **1 remaining** — the slot opened again. Book a different truck for it and the post is `FULLY_BOOKED` again. |
| 10 | Owner view: `GET /api/owner/load-posts/matching` as the owner of the 12-tyre trucks, with a fresh open post. | The post appears. Now mark all three of that owner's trucks `UNAVAILABLE` and call again — the post disappears. Mark one `AVAILABLE` — it reappears. |
| 11 | Stranger rules: the second CUSTOMER tries to cancel the factory's post, and tries to book from it. | `403 Forbidden` both times. The same calls as ADMIN for cancel succeed (cancel) — and the 2 live bookings stay `PENDING`, they are not cancelled with the post. |
| 12 | Cancel rules: cancel a `PARTIALLY_BOOKED` post as its poster. | Post is `CANCELLED`; its live bookings are untouched. Try to cancel the `FULLY_BOOKED` post from test 7. | `409` — cancel its bookings one by one first. |
| 13 | Expiry: create a post with `pickupDate` today, then in MySQL set its `pickup_date` to yesterday and call `GET /api/load-posts/open`. | The post is gone from the open list, and `GET /api/load-posts/{id}` says `EXPIRED`. Booking from it returns `409`. |
| 14 | Snapshot, again: book from a post, then change the 12-tyre rate to ₹2.80 as ADMIN, then `GET /api/bookings/{id}`. | The booking still says **₹33,280** and rate ₹2.70. Change the rate back to ₹2.70 afterwards. |
| 15 | Own truck refused: the factory account also owns a truck (second account as OWNER will not do — use the test where the poster's id equals a truck's owner id by posting from an account that owns trucks, if your test data has one; otherwise check the code path). | `409` — "You cannot book your own truck". |
| 16 | Full happy path, second example: 50 tons, 2 trucks, 14-tyre, 500 km, toll ₹1,500, loading ₹1,000. | Matches show per-truck **₹35,000** (500 × 2.60 × 25 = 32,500 + 2,500). Book both: post `FULLY_BOOKED`, total of the two bookings **₹70,000**. Owner accepts both; each booking has its own chat and its own `tel:` link. |
| 17 | Unauthenticated: any load-post endpoint with no token. | `401 Unauthorized`. |
| 18 | Wrong role: `POST /api/load-posts` as OWNER; `GET /api/owner/load-posts/matching` as CUSTOMER. | `403 Forbidden` in both cases. |

### Common mistakes

- **Dividing the tons wrong.** `totalTons / trucksNeeded` is the share **each** truck carries. If you price the whole 60 tons on one truck, the fare is 3× too big and no truck matches (60 > 36). Price one share, then multiply the *fare* by the number of trucks.
- **Putting toll and loading in once for the whole load.** They are **per truck** in this module, because each booking is priced per truck. ₹1,500 toll on a 3-truck load is ₹1,500 per truck (₹4,500 in total), not ₹1,500 split three ways. If the factory's toll is really for the whole load, divide it before posting.
- **Storing `trucksBooked` as a column.** The count must come from live bookings every time. A stored counter drifts the first time a booking is cancelled outside the post flow (Module 4 cancel) and nobody updates it. Count, don't store.
- **Forgetting Edit C (the rejection hook).** Without it, a rejected booking leaves the post `FULLY_BOOKED` with a hole in it, and the factory can never fill its load. Test 9 exists to catch exactly this.
- **Letting the client send `tonsPerTruck` or `status`.** The request has no such fields. If you add them "for convenience", a user can post 60 tons / 3 trucks with `tonsPerTruck: 1` and match trucks that cannot carry the load.
- **Booking the same quote twice in one request.** The duplicate check in `bookFromPost` catches it with a clear `400`. Without it, Module 4 fails on the second one with a confusing `409` about the quote no longer being `ACTIVE` — and because the whole method is one transaction, the first booking rolls back too, so the factory gets nothing and no error that explains why.
- **Showing the poster's phone on the open board.** The phone is only for owners whose truck matches, and for the parties to a booking. If `GET /api/load-posts/open` ever returns a phone number, any stranger can harvest factory numbers. The response object in this module has no phone field at all — keep it that way.
- **Using `double` for money.** Same rule as Modules 3–4: `BigDecimal`, never `double`. ₹99,840.00 must stay exactly that.

---

## What changes in the frontend (Module 7), and what is next

**Module 7 additions (do these when you build the frontend, not now):**
- Customer "Post a Load" page — the same fields as `CreateLoadPostRequest`, with the even-division rule checked live in the form ("60 tons ÷ 3 trucks = 20 tons each" shown under the inputs, red warning if it does not divide evenly, before the user presses Post).
- Customer "My Load Posts" page — each post with its status pill (`OPEN` / `PARTIALLY_BOOKED` / `FULLY_BOOKED`), "2 of 3 trucks booked" progress, and a "Find Trucks" button that opens the matches list with per-truck fares and the estimated total.
- Owner "Load Board" page — `GET /api/owner/load-posts/matching`, showing only posts their trucks can carry, with no phone numbers.
- Booking from a post reuses the Module 7 booking-confirm page — one booking per truck, each with its own call/chat, exactly as Module 4 designed it. No new booking screen is needed.

**Module 10 — next (only when you say so):** the remaining gaps from Modules 1–8, in priority order — (1) trip dates and date-based truck availability (the overlapping-date check Module 4 deliberately left out; a load post's `pickupDate` is the first real trip date in the system, so this is the natural place to build it), (2) Google Maps live distance to replace the typed kilometres, (3) real-time chat to replace polling, (4) the PRD v1.1 rewrite (rename TruckConnect → Truck Booking, platform fee ₹0, the per-ton-per-km rates, and this module). Tell me which one you want first; do not start all four at once.

**After Module 9:** run locally first — MySQL database `truck_booking`, backend on port 8080, frontend on port 5173 — then follow Module 8's free deployment guide (GitHub → TiDB Cloud → Render → Vercel, ₹0). The Java code in this guide is complete on paper but **not compiled or run** — run `./mvnw test` in VS Code, module by module, and work through the checklist above before moving on.
