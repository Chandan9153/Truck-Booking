# Truck Booking — Module 6: Payments

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1 to 5 keep working; rename the package later only if you want to)
**Stack:** Java 21, Spring Boot 3, Spring Data JPA + Hibernate, MySQL 8, Spring Security + JWT — no new dependencies
**Builds on:** Module 1 — Authentication & Security (roles, JWT, `com.truckconnect.user.User`) · Module 2 — Truck Categories & Registration, slim version (`Truck` with `id`, `registrationNumber`, `category`, `owner`, `bodyType`, `payloadTons`, `availabilityStatus`; no documents/paperwork) · Module 3 — Search, Matching & Pricing (`Quote` with `totalFare` snapshot; rates 12-tyre/20t ₹2.70, 14/25t ₹2.60, 18/30t ₹2.50, 22/36t ₹2.40 per ton per km; platform fee ₹0 flat per booking) · Module 4 — Booking (assumed, see Part 0) · Module 5 — Trip (assumed, see Part 0)
**This module only:** Taking payment for a booking, paying the owner their share, and handling refunds. No new booking rules, no trip tracking, no frontend — Module 7 builds the React screens on top of these endpoints.

## How to use this guide

1. Read Part 0 first — it tells you exactly which Module 4 / Module 5 names this module assumes, and which lines to change if your Modules 4–5 named them differently. Do that check before you add Module 6 code.
2. Read Parts 1 to 5 so you understand *why* the code is shaped this way — especially the honest MVP rule in Part 1: **no real money moves in this module**.
3. Add one file at a time in VS Code, in the order given in Part 6.
4. Start the app and work through the test checklist in Part 10.
5. Modules 1–4 must already be working — you need a login token and a CONFIRMED booking before you can test anything here.

> **The one rule of this module:** **never mark a payment SUCCESS without verification.** In this MVP the only verified payment is cash counted by the owner on delivery, confirmed through the owner endpoint. An online payment is never marked SUCCESS by a request from the customer, by a frontend, or by guessing — only by a verified gateway webhook, and the gateway is not connected yet (Part 3). If you break this rule, you will create money that was never paid.

---

## Part 0 — What this module assumes from Modules 4–5 (check this first)

Modules 4 and 5 were being built at the same time as this module, so this guide does **not** wait for them — it assumes the contracts below. If your Module 4 used different names, change only the lines listed here. Nothing else in Module 6 changes.

### Assumed Module 4 contract

| Assumed name | What it is | If yours differs, change this line |
| --- | --- | --- |
| `com.truckconnect.booking.Booking` | The booking entity | The `import com.truckconnect.booking.Booking;` line in `Payment.java`, `PaymentService.java`, `PaymentController.java` |
| `com.truckconnect.booking.BookingStatus` | Enum with `PENDING, CONFIRMED, DRIVER_ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, COMPLETED, REJECTED, CANCELLED, PAYMENT_PENDING, PAYMENT_FAILED, DISPUTED` | The status comparisons in `PaymentService.java` (Part 8) and the mapping table in Part 4 |
| `com.truckconnect.booking.BookingRepository` | `JpaRepository<Booking, Long>` | The constructor parameter in `PaymentService.java` |
| `booking.getCustomer()` | Returns the `User` who booked | The ownership check in `PaymentService.initiatePayment` |
| `booking.getTruck()` then `truck.getOwner()` | Returns the `User` who owns the truck | The owner lookup in `PaymentService` / `SettlementService` — **if your `Booking` has a direct `getOwner()` method, use that instead and delete the `getTruck().getOwner()` call** |
| `booking.getTotalFare()` | `BigDecimal`, the snapshot price copied from the quote in Module 4 — never recalculated | The amount line in `PaymentService.initiatePayment`: `booking.getTotalFare()` is the **only** source of the payment amount |
| `booking.getStatus()` / `booking.setStatus(...)` | Read and change the booking status | The status updates in `PaymentService.confirmCashPayment` |
| `booking.getId()` | The booking id | The idempotency lookup `paymentRepository.findByBookingId(booking.getId())` |

### Assumed Module 5 contract

- One `Trip` per booking, and delivery proof must be recorded **before** a booking becomes `DELIVERED`. Module 6 trusts that rule: the owner can only confirm a cash payment when the booking is already `DELIVERED`, so a payment can never be confirmed for a load that has not arrived. Module 6 does not check trip proof itself — that is Module 5's job.

### Assumed Module 2 / 3 names (already fixed)

- `com.truckconnect.user.User` (Module 1) — the logged-in user, from `@AuthenticationPrincipal User` exactly as in Modules 1 and 3.
- `truck.getOwner()`, `truck.getRegistrationNumber()` (Module 2, slim version).
- Platform fee is **₹0 flat per booking**, already included inside `totalFare` (Module 3, Part 3). Module 6 never adds a second fee.

> **If any assumed name is wrong:** fix the import or the one getter call named in the table above, and leave the rest of the file alone. Do not redesign Module 6 to fit a different Module 4 — the design below is deliberately small, and renaming one getter is a one-line job.

---

## Part 1 — What this module does, and why it comes sixth

### What this module does

Module 1 answered *who are you?* Module 2 answered *what trucks exist?* Module 3 answered *what will it cost?* Module 4 answered *is the truck booked?* Module 5 answered *where is the load?* Module 6 answers *who paid whom, how much, and did the owner get their share?*

This module gives Truck Booking:

- A `payments` table — one payment per booking, for the full booking amount, with a clear status at every step
- A `PaymentGateway` interface — the plug socket that Razorpay will fit into later, with a manual (cash) plug in it today and a Razorpay plug that honestly says "not connected yet"
- A COD (Cash on Delivery) flow that works with **no payment gateway, no API key, and no real money moving through the app** — the default for the MVP
- An `owner_settlements` table — the arithmetic that records every successful payment as platform fee ₹0 and owner amount = the full gross amount, and an admin step that marks the settlement PAID (for online payments later, after a manual bank transfer; for COD, it only records that the owner already holds the cash)
- A `refunds` table — a manual, admin-approved refund record for the rare case where money was already marked paid and the booking is then cancelled
- Ownership and role checks on every endpoint, so a customer can only see their own payments and an owner can only see their own settlements

### Why it comes sixth

You cannot take payment for a booking that does not exist, and you cannot pay an owner for a delivery that has not happened. Payments need the booking (Module 4), its snapshot price (Module 3), and its delivery state (Module 5) before any of the code below makes sense. If payments came earlier, there would be nothing to pay for.

### What this module does not do

- **No real online payment.** No Razorpay account, no API key, no SDK, no test key, no real money. The Razorpay class in this module is a stub that throws an error on purpose (Part 3). Do not "fix" it by adding a key — that is a later module, done with a real account, after this MVP works.
- **No automatic payout to owners.** The admin marks a settlement PAID after doing a manual bank transfer outside the app (UPI / NEFT / IMPS from the business bank account). There is no payout API in this module.
- **No automatic refund to a customer's bank/UPI.** A refund is a record the admin approves, then processes manually outside the app. There is no refund API call in this module.
- **No partial payment, no advance, no EMI, no wallet.** One payment per booking, for the full amount, in the MVP. Those are later features, and the tables below are designed so they can be added without changing the `payments` table shape (Part 5 explains how).

---

## Part 2 — The big picture in simple words

Think of Truck Booking like a market with a cashier's desk.

1. **The customer books a truck** (Module 4) and the price is written on the booking: say ₹33,280. That number is now frozen — it is a snapshot, and nobody can change it.
2. **The cashier opens a payment slip** for that booking. There is exactly one slip per booking (Part 5 explains why). The slip says: booking, customer, owner, amount ₹33,280, method COD, status PENDING. The amount is copied from the booking — the customer never writes the amount on the slip themselves, or they could write ₹1.
3. **The trip happens** (Module 5). The truck delivers the load, delivery proof is recorded, and the booking becomes DELIVERED.
4. **The customer hands cash to the owner (or the driver, for the owner) on delivery.** The owner counts it, then presses "Confirm cash received" in the app. Only then does the slip change to SUCCESS, the booking becomes COMPLETED, and the cashier writes a second slip — the **settlement** — that says: gross ₹33,280, platform fee ₹0, owner gets ₹33,280.
5. **The admin pays the owner later, by bank transfer, outside the app** — wait, no: read that again. In COD the owner *already has the cash in hand*. So what does the settlement mean for COD? See the honest note in Part 4 — this is the one place beginners get confused, and it matters.
6. **If something goes wrong** — the booking is cancelled before pickup — the unpaid slip is simply cancelled. No money moved, so there is nothing to refund. If money *had* already been marked paid (only possible for online payments later), a refund slip is opened and the admin handles it by hand.

### The flow, step by step

```text
CUSTOMER logs in (Module 1 JWT)
  -> POST /api/payments/initiate  { bookingId, method: "COD" }
       booking must be CONFIRMED (or further along, up to DELIVERED)
       amount is copied from booking.totalFare — never from the request
       first call creates the payment (PENDING); second call returns the SAME payment
  -> GET  /api/payments/{id}            customer sees only their own payment
  -> GET  /api/payments/mine            customer lists their own payments

Trip happens (Module 5) -> booking becomes DELIVERED

OWNER logs in (Module 1 JWT)
  -> POST /api/owner/payments/{paymentId}/confirm-cash
       only if booking is DELIVERED, payment is PENDING, method is COD
       -> payment SUCCESS, paidAt = now
       -> booking DELIVERED -> COMPLETED
       -> settlement row created: gross 33,280 / fee 0 / owner 33,280, status PENDING
  -> GET  /api/owner/settlements        owner sees only their own settlements

ADMIN logs in (Module 1 JWT)
  -> GET  /api/admin/settlements              all settlements, PENDING first
  -> POST /api/admin/settlements/{id}/mark-paid
       admin has done the manual bank transfer outside the app
       -> settlement PENDING -> PAID, settledAt = now
  -> POST /api/admin/refunds/{id}/process    approve / reject a refund (manual refund outside app)

ONLINE payment (LATER — not in this module):
  -> POST /api/payments/initiate  { bookingId, method: "UPI" }
       -> 503 Service Unavailable: "Online payment is not connected yet"
       -> NO payment row is created, NO money moves
```

### Who can do what

| Action | CUSTOMER | OWNER | DRIVER | TRANSPORTER | ADMIN |
| --- | --- | :---: | :---: | --- | --- |
| Initiate a COD payment for my own booking | Yes | No | No | No | No |
| Initiate an online payment | No — 503, gateway not connected yet (everyone gets this for now) | No | No | No | No |
| See my own payment(s) | Yes | No | No | No | Yes, all |
| Confirm cash received on delivery | No | Yes, only for my own trucks' bookings | No | No | No |
| See my own settlements | No | Yes | No | No | Yes, all |
| Mark a settlement PAID | No | No | No | No | Yes |
| Request a refund on my own paid booking | Yes | No | No | No | No |
| Process (approve / reject) a refund | No | No | No | No | Yes |
| See another customer's payment or another owner's settlement | No | No | No | No | Yes, all |

---

## Part 3 — The MVP payment model, honestly: COD now, Razorpay later

### Method (a) — Cash on Delivery / pay-to-owner on delivery (COD): the default

This is the whole MVP. It needs no gateway, no account, and no key, which is why it works on the free deployment stack (Vercel + Render + TiDB Cloud) with ₹0 extra cost.

- The customer pays **in cash, in person, on delivery** — to the owner, or to the driver acting for the owner.
- The app never touches the cash. The app only **records** that the owner says the cash was received.
- That is why the confirm step belongs to the **owner**, not the customer: the person who counted the money is the person who says "I received it". If the customer could mark their own payment SUCCESS, they could mark it paid without paying.
- The risk is honest and must be stated: the app trusts the owner's confirm click. If the owner confirms without receiving cash, the app cannot know. That is the same trust a cash market runs on, and it is acceptable for an MVP between a customer and an owner who can also call/chat each other (your Module 4 decision: instant booking + owner call/chat). A later module can add driver confirmation, OTP on delivery, or photo proof — do not build those here.

### Method (b) — Online payment (UPI / card / netbanking via Razorpay): LATER, not in this module

- Razorpay is the **named future gateway** because it is the standard Indian choice for UPI/cards/netbanking, but **nothing about Razorpay is set up in this module**: no account, no key id, no key secret, no SDK in `pom.xml`, no test mode, no real or test money.
- The code is built so Razorpay can plug in later **without changing the `payments` or `bookings` tables**. How: every payment goes through the `PaymentGateway` interface (Part 7). Today the interface has two implementations — `ManualPaymentGateway` (real, used for COD) and `RazorpayPaymentGateway` (a stub that throws `UnsupportedOperationException` with a clear message). Later, the stub is replaced by a real implementation that talks to Razorpay, and `PaymentService` does not change — it only calls the interface.
- The online methods in the `PaymentMethod` enum — `UPI`, `CARD`, `NETBANKING` — exist in the code **only so the later gateway has values to use**. Sending one of them to the initiate endpoint today returns **503 Service Unavailable** with the message `"Online payment is not connected yet. Please pay by cash on delivery."` No payment row is created for that attempt, so there is no half-created online payment sitting in the database.
- **Never mark a payment SUCCESS without verification** — for the gateway-later path, this is how verification will work, in words (no code that moves money today):
  1. The customer pays on Razorpay's page/app. Razorpay, not our app, takes the money.
  2. Razorpay calls our server back (a **webhook**) and says "payment X for order Y succeeded".
  3. Our server must **verify the webhook signature** — Razorpay signs the message with a secret only Razorpay and our server know, and we recompute the signature and compare. If it does not match, the message is fake and we ignore it. If we skipped this step, anyone could send a fake "payment succeeded" message and mark their own booking paid for free.
  4. Only after the signature matches do we set the payment to SUCCESS. The customer is never trusted to tell us "I paid".
  5. The stub in Part 7 contains a `verifyWebhookSignature(...)` method that throws `UnsupportedOperationException` today, with a comment marking exactly where the real HMAC-SHA256 check goes later. There is no secret value anywhere in this module — not even a placeholder that looks like a real key.

### The two-method summary

| | COD (now) | Online via Razorpay (later) |
| --- | --- | --- |
| Needs a gateway account? | No | Yes — created in a later module, not here |
| Needs an API key / SDK? | No | Yes — later; `pom.xml` is **not** changed in this module |
| When is the payment created? | Customer initiates after booking is CONFIRMED | Customer initiates at booking time (later design — see Part 4) |
| When does it become SUCCESS? | Owner confirms cash on delivery | Razorpay webhook, signature verified |
| What if the gateway is down? | Not applicable | Booking goes to `PAYMENT_FAILED` (Part 4 table); customer can retry or switch to COD |
| Money moved by the app? | Never — cash changes hands in person | Later, by Razorpay — never by our server holding card/UPI details |

---

## Part 4 — The payment flow and the booking-status mapping

### The one clear rule

Pick this rule and do not mix it with the other one:

> **COD is paid after DELIVERED. Online (later) is paid at booking time.**

- **COD:** the payment is created PENDING when the customer initiates it (any time from CONFIRMED up to DELIVERED), and it is marked SUCCESS by the owner **only after the booking is DELIVERED**. Paying before delivery would mean the customer hands over cash for a load that has not arrived.
- **Online (later):** the payment is initiated at booking time, before the trip starts, because the gateway collects the money up front. That is a different flow for a later module — the status mapping below already reserves the states it will need (`PAYMENT_PENDING`, `PAYMENT_FAILED`), so Module 6 does not need to be rewritten when Razorpay arrives.

### The exact booking-status mapping

This table is the contract. If the code ever disagrees with this table, the table is right and the code is wrong.

| Event | Payment status before → after | Booking status before → after |
| --- | --- | --- |
| Customer initiates COD for a CONFIRMED / DRIVER_ASSIGNED / PICKED_UP / IN_TRANSIT / DELIVERED booking | (none) → `PENDING` | Unchanged (stays CONFIRMED, etc.) |
| Customer initiates COD for a PENDING booking (owner has not accepted yet) | No payment created | Unchanged — **400 Bad Request**: "Booking is not confirmed yet" |
| Customer initiates COD for a COMPLETED / CANCELLED / REJECTED booking | No payment created | Unchanged — **400 Bad Request** |
| Customer initiates COD a second time for the same booking | Existing `PENDING` payment returned, **no new row** (idempotency, Part 5) | Unchanged |
| Customer initiates online (UPI / CARD / NETBANKING) today | No payment created — **503**: "Online payment is not connected yet" | Unchanged |
| Owner confirms cash, booking is DELIVERED, payment is PENDING + COD | `PENDING` → `SUCCESS`, `paidAt` = now | `DELIVERED` → `COMPLETED` |
| Owner tries to confirm cash before DELIVERED | Unchanged (`PENDING`) | Unchanged — **400 Bad Request**: "Booking is not delivered yet" |
| Owner tries to confirm cash for a payment that is already SUCCESS | Unchanged (`SUCCESS`) | Unchanged — **400 Bad Request**: "Payment is already successful" |
| Online payment fails at the gateway (later, when Razorpay exists) | `INITIATED` → `FAILED` | → `PAYMENT_FAILED`, then back to `CONFIRMED` when the customer retries or switches to COD (the booking itself is not cancelled by a failed payment) |
| Online payment succeeds at the gateway (later) | `INITIATED` → `SUCCESS`, `gatewayReference` = Razorpay payment id | If booking was `PAYMENT_PENDING` → back to `CONFIRMED`; if booking was already `DELIVERED` → `COMPLETED` |
| Booking is cancelled before pickup, payment is PENDING (COD) | `PENDING` → `CANCELLED` | → `CANCELLED` (Module 4's cancel flow calls `PaymentService.cancelPendingPaymentForBooking(...)` — see Part 8) |
| Booking is cancelled, payment was already SUCCESS (online, later only) | `SUCCESS` → `REFUNDED` or `PARTIAL_REFUND` (only after the admin processes the refund, Part 5) | → `CANCELLED` |
| Booking is DISPUTED (Module 4) | Unchanged — payment is frozen until the dispute is resolved; nobody can confirm cash or process a refund while the booking is DISPUTED | → `DISPUTED` (Module 4) |

Two things to notice:

1. **COD never uses `PAYMENT_PENDING` on the booking.** The booking keeps its normal trip status (CONFIRMED → … → DELIVERED) while the COD payment sits PENDING in the background. `PAYMENT_PENDING` is reserved for the online flow, where the booking genuinely has to wait for the gateway before the trip can start.
2. **A failed online payment never cancels the booking.** It only parks the booking in `PAYMENT_FAILED` so the customer can retry or switch to COD. Cancelling is a separate decision by the customer or owner in Module 4.

### Settlement — the honest COD note (read this before you get confused)

The settlement arithmetic is the same for COD and online, but **who holds the money is different**, and pretending otherwise is the most common beginner mistake in this module:

- **Online (later):** the money lands in the platform's Razorpay account first. The platform then owes the owner their share, and the admin's manual bank transfer actually sends money the platform holds. The settlement row is a real "money owed" record.
- **COD (MVP):** the customer hands cash **directly to the owner/driver**. The owner already holds the full ₹33,280, and the platform fee is ₹0, so there is nothing for the owner to transfer to the platform and nothing for the admin to send to the owner. The settlement row still records the split (gross ₹33,280, fee ₹0, owner keeps ₹33,280), and the admin's "mark PAID" step simply records that the owner has already received the full cash — it moves no money. The `SettlementResponse` carries a `note` field so the admin can write, for example, "Cash received by owner on delivery".

> **Do not build a COD flow in which the admin "pays" the owner money the owner already has.** The settlement row is the record of the split; with a ₹0 platform fee there is no fee going to the platform for COD, and the owner's share going to the owner only applies for online payments (later), where the platform holds the money first. A later module can add a proper ledger that tracks both directions. For this MVP, the note field and this paragraph are the ledger.

### Settlement arithmetic — the Module 3 example, worked by hand

Module 3, Example 1: Bengaluru → Hyderabad, 12-tyre truck, 20-ton load, 570 km.

| Line | Amount |
| --- | --- |
| Base fare: 570 km × ₹2.70 × 20 tons | ₹30,780 |
| Toll charges | ₹1,500 |
| Loading charges | ₹1,000 |
| Platform fee (flat, inside the total) | ₹0 |
| **Total fare = gross amount paid by the customer** | **₹33,280** |
| Platform fee (the platform's money) | ₹0 |
| **Owner amount = 33,280 − 0** | **₹33,280** |

Check it: 33,280 + 0 = 33,280. It adds up. The toll and loading charges go to the owner too — they are trip costs, not platform revenue — which is why the owner amount is simply `gross − platformFee`, not `baseFare − platformFee`. The code in `SettlementService` does exactly this one subtraction, with `BigDecimal`, and nothing else. If a future module changes the fee to a percentage, only that one method changes.

---

## Part 5 — The entities: Payment, OwnerSettlement, Refund

### Payment — one slip per booking

**Simple meaning:** One row is one payment for one booking. The amount is a snapshot copied from the booking at initiate time, so a later price change can never change what was paid.

Fields (all required unless marked nullable):

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | Long | Primary key |
| `booking` | `Booking` (ManyToOne) | The booking being paid for. Unique — see below |
| `customer` | `User` (ManyToOne) | Who pays. Copied from `booking.getCustomer()`, never from the request |
| `owner` | `User` (ManyToOne) | Who receives. Copied from `booking.getTruck().getOwner()` (or `booking.getOwner()` if Module 4 has it — Part 0) |
| `amount` | `BigDecimal` (precision 12, scale 2) | Snapshot of `booking.getTotalFare()` at initiate time. Money, so `BigDecimal`, never `double` |
| `method` | `PaymentMethod` enum | `COD`, `UPI`, `CARD`, `NETBANKING` |
| `status` | `PaymentStatus` enum | `PENDING, INITIATED, SUCCESS, FAILED, REFUNDED, PARTIAL_REFUND, CANCELLED` |
| `gatewayReference` | String, nullable | Razorpay order/payment id **later**. Always `null` for COD, always `null` in this MVP |
| `paidAt` | LocalDateTime, nullable | When the payment became SUCCESS. `null` until then |
| `createdAt` | LocalDateTime | When the row was created |

**Idempotency — the one-payment rule, in simple words.** *Idempotency* means "doing the same thing twice has the same result as doing it once". A customer on a slow network can tap "Pay" twice, or the frontend can retry a request that timed out. Without a guard, that creates two payments for one booking and the customer is charged twice. Module 6 has two guards, and both are needed:

1. **Database guard:** a unique constraint on `booking_id` in the `payments` table. The database itself refuses a second row for the same booking. This is the guard that works even if two requests arrive at the exact same millisecond.
2. **Code guard:** `PaymentService.initiatePayment` first looks up `findByBookingId(bookingId)`. If a payment already exists, it returns that existing payment instead of creating a new one. The idempotency key is simply **the booking id** — no extra key, no extra header, nothing for the customer to remember. The response also carries `idempotentReplay: true` on the second call, so the frontend can tell "this is the same payment you already created, not a new one".
3. If the code guard and the database guard race (two requests slip past the lookup at the same instant), the service catches the database's unique-constraint error and re-reads the existing payment instead of returning a 500. That catch block is in Part 8 — do not delete it as "dead code"; it is the race guard.

**The extra status value — `CANCELLED`.** The task list for `PaymentStatus` was `PENDING, INITIATED, SUCCESS, FAILED, REFUNDED, PARTIAL_REFUND`. This module adds **`CANCELLED`** to that list, because Part 4 requires a COD PENDING payment on a cancelled booking to become CANCELLED — and `FAILED` would be wrong (nothing failed; the trip was cancelled) and `REFUNDED` would be wrong (no money moved). One extra enum value, used in exactly one place.

**What `INITIATED` is for.** `PENDING` means "a COD slip exists and we are waiting for delivery". `INITIATED` means "an online payment was started with the gateway and we are waiting for the gateway's answer" — it is only used by the later Razorpay flow. In this MVP no payment ever sits in `INITIATED`, because the online path returns 503 before creating a row. The value exists now so the later module does not have to change the enum (and the database column) to add it.

### OwnerSettlement — the split, written down

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | Long | Primary key |
| `owner` | `User` (ManyToOne) | Who the owner's share belongs to |
| `booking` | `Booking` (ManyToOne) | The booking this settlement is for. Unique — one settlement per booking |
| `payment` | `Payment` (OneToOne) | The successful payment this settlement came from |
| `grossAmount` | `BigDecimal` | Total the customer paid (= payment amount, e.g. ₹33,280) |
| `platformFee` | `BigDecimal` | The platform's ₹0, copied from the payment's booking (Module 3 settings) |
| `ownerAmount` | `BigDecimal` | `grossAmount − platformFee` (e.g. ₹33,280). Calculated by the service, never sent by a client |
| `status` | `SettlementStatus` enum | `PENDING, PAID` |
| `note` | String, nullable | Admin note, e.g. "Cash received by owner on delivery" (see the COD note in Part 4) |
| `settledAt` | LocalDateTime, nullable | When the admin marked it PAID. `null` until then |
| `createdAt` | LocalDateTime | When the settlement was created (at payment SUCCESS) |

A settlement is created **only when a payment becomes SUCCESS** — never at initiate time, never at booking time. A PENDING payment owes nobody anything yet.

### Refund — money back, by hand

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | Long | Primary key |
| `payment` | `Payment` (ManyToOne) | The payment being refunded |
| `amount` | `BigDecimal` | How much to refund. Full amount in the MVP; a smaller amount is a partial refund |
| `reason` | String | Why — e.g. "Booking cancelled before pickup" |
| `status` | `RefundStatus` enum | `PENDING, PROCESSED, REJECTED` |
| `adminNote` | String, nullable | What the admin did, e.g. "Refunded ₹33,280 by UPI on 12 Nov" |
| `createdAt` / `processedAt` | LocalDateTime | Created when the customer requests; processed when the admin decides |

**Cancel rules (from Module 4), applied to money:**

- Booking cancelled **before pickup** and payment is **PENDING (COD)** → the payment becomes `CANCELLED`. No refund record, no money moved, nothing to refund. This is the normal MVP case.
- Booking cancelled **before pickup** and payment is **SUCCESS (online, later only)** → the customer may request a refund. A `Refund` row is created with status `PENDING`. The admin checks it, refunds the money manually outside the app (UPI/bank transfer), then marks the refund `PROCESSED` with a note. Only then does the payment become `REFUNDED` (full amount) or `PARTIAL_REFUND` (part of the amount). If the admin rejects it, the refund becomes `REJECTED` and the payment stays `SUCCESS`.
- Booking cancelled **after pickup** → no automatic refund. The customer can still raise a dispute (Module 4 `DISPUTED`); the admin decides case by case. Do not auto-refund a load that is already on the road.
- A refund is never created for a payment that is not SUCCESS. Refunding a PENDING payment is nonsense — nothing was paid.

---

## Part 6 — Files to create (26 files)

All under `src/main/java/com/truckconnect/`, continuing Modules 1–5. **No new dependency** — do not add anything to `pom.xml` for this module, especially not a Razorpay SDK.

| # | File | What it is |
| --- | --- | --- |
| 1 | `payment/PaymentMethod.java` | Enum — COD, UPI, CARD, NETBANKING |
| 2 | `payment/PaymentStatus.java` | Enum — PENDING, INITIATED, SUCCESS, FAILED, REFUNDED, PARTIAL_REFUND, CANCELLED |
| 3 | `payment/Payment.java` | Entity — one payment per booking |
| 4 | `payment/PaymentRepository.java` | Finds a payment by booking / customer / owner |
| 5 | `payment/PaymentGateway.java` | Interface — the plug socket for any gateway |
| 6 | `payment/ManualPaymentGateway.java` | The real COD implementation of that interface |
| 7 | `payment/RazorpayPaymentGateway.java` | Stub — throws "not connected yet", no keys, no SDK |
| 8 | `payment/dto/InitiatePaymentRequest.java` | What the customer sends (booking id + method only — **no amount**) |
| 9 | `payment/dto/InitiatePaymentResponse.java` | What the customer gets back (with `idempotentReplay`) |
| 10 | `payment/dto/PaymentResponse.java` | The safe public view of a payment |
| 11 | `payment/dto/ConfirmCashPaymentRequest.java` | What the owner sends (a note only — **no amount**) |
| 12 | `payment/PaymentService.java` | The brain: initiate, confirm cash, cancel-on-booking-cancel |
| 13 | `payment/PaymentController.java` | Customer endpoints |
| 14 | `payment/OwnerPaymentController.java` | Owner endpoints (confirm cash, my settlements view) |
| 15 | `settlement/SettlementStatus.java` | Enum — PENDING, PAID |
| 16 | `settlement/OwnerSettlement.java` | Entity — the split of one successful payment |
| 17 | `settlement/OwnerSettlementRepository.java` | Finds settlements by owner / booking |
| 18 | `settlement/dto/SettlementResponse.java` | What the owner/admin sees |
| 19 | `settlement/SettlementService.java` | Creates settlements, marks them PAID |
| 20 | `settlement/AdminSettlementController.java` | Admin endpoints (settlements + refund processing) |
| 21 | `refund/RefundStatus.java` | Enum — PENDING, PROCESSED, REJECTED |
| 22 | `refund/Refund.java` | Entity — one refund request |
| 23 | `refund/RefundRepository.java` | Finds refunds by payment |
| 24 | `refund/dto/RefundRequest.java` | What the customer sends (payment id, amount, reason) |
| 25 | `refund/dto/RefundResponse.java` | What the customer/admin sees |
| 26 | `refund/RefundService.java` | Creates refunds, processes them (admin) |

Create them in this order, top to bottom: enums → entities → repositories → gateway → DTOs → services → controllers.

---

## Part 7 — Enums, entities, repositories, and the gateway plug

### File 1 of 26 — `payment/PaymentMethod.java`

**Simple meaning:** The fixed list of ways to pay. `COD` is the only one that works today; the other three are the values the later Razorpay flow will use.

```java
package com.truckconnect.payment;

public enum PaymentMethod {
    COD,        // Cash on Delivery / pay-to-owner on delivery — the MVP default
    UPI,        // Online, via Razorpay — LATER, not in this module
    CARD,       // Online, via Razorpay — LATER, not in this module
    NETBANKING  // Online, via Razorpay — LATER, not in this module
}
```

### File 2 of 26 — `payment/PaymentStatus.java`

**Simple meaning:** The fixed list of payment states. Read Part 5 for why `CANCELLED` is here even though it was not in the first list.

```java
package com.truckconnect.payment;

public enum PaymentStatus {
    PENDING,        // COD slip created, waiting for delivery + owner confirm
    INITIATED,      // Online payment started with the gateway, waiting for its answer (later only)
    SUCCESS,        // Money verified as received (owner confirm for COD; webhook for online later)
    FAILED,         // Gateway said the online payment failed (later only)
    REFUNDED,       // Full amount refunded by admin after a SUCCESS payment
    PARTIAL_REFUND, // Part of the amount refunded by admin after a SUCCESS payment
    CANCELLED       // Booking was cancelled while the payment was still PENDING; no money moved
}
```

### File 3 of 26 — `payment/Payment.java`

**Simple meaning:** One row is one payment slip for one booking. The `uniqueConstraints` on `booking_id` is the database guard for idempotency (Part 5) — do not remove it.

```java
package com.truckconnect.payment;

import com.truckconnect.booking.Booking;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id"}))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference; // null for COD; Razorpay order/payment id later

    @Column(name = "paid_at")
    private LocalDateTime paidAt; // null until SUCCESS

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Payment() { }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking b) { this.booking = b; }
    public User getCustomer() { return customer; }
    public void setCustomer(User c) { this.customer = c; }
    public User getOwner() { return owner; }
    public void setOwner(User o) { this.owner = o; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod m) { this.method = m; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus s) { this.status = s; }
    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String g) { this.gatewayReference = g; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime p) { this.paidAt = p; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

> **Check Module 4 first:** this entity imports `com.truckconnect.booking.Booking`. If your Module 4 `Booking` lives in a different package, change only that one import line to match yours (Part 0 table).

### File 4 of 26 — `payment/PaymentRepository.java`

```java
package com.truckconnect.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Idempotency lookup: one payment per booking, so this returns at most one row.
    Optional<Payment> findByBookingId(Long bookingId);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Payment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
```

### File 5 of 26 — `payment/PaymentGateway.java`

**Simple meaning:** This interface is the plug socket. `PaymentService` only ever talks to this interface — it never talks to Razorpay directly. That is the whole trick that lets Razorpay plug in later without changing `PaymentService`, `Booking`, or the tables.

```java
package com.truckconnect.payment;

/**
 * A payment gateway is anything that can take money for a Payment.
 * Today only ManualPaymentGateway (COD) is real. RazorpayPaymentGateway
 * is a stub that says "not connected yet". A later module replaces
 * the stub with a real Razorpay implementation — this interface,
 * PaymentService, and the tables do not change.
 */
public interface PaymentGateway {

    /** Which method this gateway handles (COD, UPI, CARD, NETBANKING). */
    PaymentMethod supportedMethod();

    /**
     * Start a payment. For COD this just confirms the PENDING slip.
     * For a real online gateway (later) this creates the gateway order
     * and returns its reference. Never marks a payment SUCCESS by itself.
     */
    GatewayResult initiate(Payment payment);

    /**
     * Verify a gateway callback/webhook. For COD there is no webhook,
     * so the manual gateway returns false. For Razorpay (later) this
     * verifies the webhook signature — see RazorpayPaymentGateway.
     * A payment is NEVER marked SUCCESS unless this returns true
     * (or, for COD, unless the owner confirms cash in person).
     */
    boolean verifyWebhook(String payload, String signature);

    /** The result of starting a payment: what happened, and the gateway's reference (if any). */
    record GatewayResult(PaymentStatus status, String gatewayReference, String message) { }
}
```

### File 6 of 26 — `payment/ManualPaymentGateway.java`

**Simple meaning:** The COD gateway. It does no network call, holds no money, and needs no key — it just says "this is a cash payment; wait for the owner to confirm it in person".

```java
package com.truckconnect.payment;

import org.springframework.stereotype.Component;

@Component
public class ManualPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.COD;
    }

    @Override
    public GatewayResult initiate(Payment payment) {
        // Cash changes hands in person on delivery. There is no gateway
        // order, no reference, and no SUCCESS here — the owner confirms
        // receipt later through PaymentService.confirmCashPayment().
        return new GatewayResult(PaymentStatus.PENDING, null,
                "Pay in cash on delivery. The owner will confirm receipt after delivery.");
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        // Manual (cash) payments have no webhook. There is nothing to verify.
        return false;
    }
}
```

### File 7 of 26 — `payment/RazorpayPaymentGateway.java`

**Simple meaning:** The Razorpay plug — deliberately empty. It exists so the shape of the later work is visible, and so that any code path that tries to use it today fails loudly with a clear message instead of silently pretending a payment worked. **Do not add a key, a secret, or an SDK dependency to make this file "work".**

```java
package com.truckconnect.payment;

import org.springframework.stereotype.Component;

/**
 * RAZORPAY — LATER / NOT IN THIS MODULE.
 *
 * No Razorpay account, no key id, no key secret, no SDK, and no real
 * money exist in this module. This stub exists only so PaymentService
 * can ask for "the gateway for UPI/CARD/NETBANKING" and get a clear
 * "not connected yet" answer instead of a NullPointerException.
 *
 * When Razorpay IS added (a later module, with a real account):
 *  1. Add the Razorpay Java SDK to pom.xml (not now).
 *  2. Store the key id and key secret in environment variables only
 *     (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET) — never in code, never in Git.
 *  3. Replace the bodies below: initiate() creates a Razorpay order and
 *     returns its id as gatewayReference with status INITIATED; and
 *     verifyWebhookSignature() recomputes the HMAC-SHA256 signature
 *     over the webhook payload with the secret and compares it with
 *     the signature Razorpay sent. Only if they match may the payment
 *     become SUCCESS. See PaymentService for where that check is called.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod supportedMethod() {
        // This stub claims UPI; PaymentService only ever asks for ONE
        // gateway per method, so CARD/NETBANKING also route here for now
        // and get the same "not connected yet" answer.
        return PaymentMethod.UPI;
    }

    @Override
    public GatewayResult initiate(Payment payment) {
        throw new UnsupportedOperationException(
                "Online payment is not connected yet. Razorpay will be added in a later module. "
                + "Please pay by cash on delivery (COD).");
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        throw new UnsupportedOperationException(
                "Online payment is not connected yet. Webhook verification will be added "
                + "with Razorpay in a later module.");
    }

    /**
     * STUB — where webhook signature verification will live later.
     * In words, the real version will: take the raw webhook body
     * (payload), the signature Razorpay sent in its header, and the
     * webhook secret (from an environment variable, never hardcoded);
     * compute HMAC-SHA256 of the payload with the secret; and return
     * true only if the computed value equals the sent signature.
     * A payment must NEVER be marked SUCCESS unless this returns true.
     */
    public boolean verifyWebhookSignature(String payload, String signature, String webhookSecret) {
        throw new UnsupportedOperationException(
                "Online payment is not connected yet. Webhook signature verification "
                + "will be added with Razorpay in a later module.");
    }
}
```

> **Why the stub throws instead of returning a polite "failed":** a polite failure could be mistaken for "the payment failed, try again", and a retry loop could one day mark something paid. An exception is unmistakable: this path does not exist yet. `PaymentService` catches it at the boundary and turns it into the 503 the customer sees (Part 8).

---

## Part 8 — The DTOs and the services

### Files 8–11 of 26 — the payment DTOs

**`payment/dto/InitiatePaymentRequest.java`** — what the customer sends. Notice what is **not** here: there is **no `amount` field**. The amount always comes from the booking snapshot (Part 9 rule 1). A request that tried to send an amount would have nowhere to put it.

```java
package com.truckconnect.payment.dto;

import com.truckconnect.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull Long bookingId,
        @NotNull PaymentMethod method
) { }
```

**`payment/dto/InitiatePaymentResponse.java`** — what the customer gets back. `idempotentReplay` is `true` when this call returned an already-existing payment instead of creating a new one.

```java
package com.truckconnect.payment.dto;

import com.truckconnect.payment.PaymentMethod;
import com.truckconnect.payment.PaymentStatus;
import java.math.BigDecimal;

public record InitiatePaymentResponse(
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        boolean idempotentReplay,
        String message
) { }
```

**`payment/dto/PaymentResponse.java`** — the safe public view of a payment. It carries ids and the amount, but no internal notes and no gateway secret (there is none to leak).

```java
package com.truckconnect.payment.dto;

import com.truckconnect.payment.Payment;
import com.truckconnect.payment.PaymentMethod;
import com.truckconnect.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String gatewayReference,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getBooking().getId(), p.getAmount(),
                p.getMethod(), p.getStatus(), p.getGatewayReference(),
                p.getPaidAt(), p.getCreatedAt());
    }
}
```

**`payment/dto/ConfirmCashPaymentRequest.java`** — what the owner sends. Again, **no amount**: the owner confirms receipt of the payment's own amount, they do not state an amount. The optional note is for the record (e.g. "Received ₹33,280 in cash from customer").

```java
package com.truckconnect.payment.dto;

public record ConfirmCashPaymentRequest(
        String note
) { }
```

### File 12 of 26 — `payment/PaymentService.java`

**Simple meaning:** This is the brain of the module. The controllers only receive requests; every rule lives here: who owns what, which booking states allow payment, the amount snapshot, idempotency, the COD confirm, and the cancel hook Module 4 calls. Read it method by method — each method is one rule from Part 4.

```java
package com.truckconnect.payment;

import com.truckconnect.booking.Booking;
import com.truckconnect.booking.BookingRepository;
import com.truckconnect.booking.BookingStatus;
import com.truckconnect.payment.dto.ConfirmCashPaymentRequest;
import com.truckconnect.payment.dto.InitiatePaymentRequest;
import com.truckconnect.payment.dto.InitiatePaymentResponse;
import com.truckconnect.payment.dto.PaymentResponse;
import com.truckconnect.settlement.SettlementService;
import com.truckconnect.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ManualPaymentGateway manualGateway;
    private final RazorpayPaymentGateway razorpayGateway;
    private final SettlementService settlementService;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          ManualPaymentGateway manualGateway,
                          RazorpayPaymentGateway razorpayGateway,
                          SettlementService settlementService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.manualGateway = manualGateway;
        this.razorpayGateway = razorpayGateway;
        this.settlementService = settlementService;
    }

    /**
     * Customer starts a payment for one of their own bookings.
     * Rule 1: the amount comes ONLY from booking.getTotalFare().
     * Rule 2: a second call for the same booking returns the existing
     * payment (idempotency key = booking id). No duplicate is created.
     * Rule 3: online methods return 503 today — the Razorpay stub throws,
     * and we turn that into "gateway not connected yet" with NO row created.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(User customer, InitiatePaymentRequest req) {
        Booking booking = bookingRepository.findById(req.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + req.bookingId()));

        // Ownership: a customer pays only for their own booking.
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This booking does not belong to you");
        }

        // Idempotency (code guard): return the existing payment, do not create another.
        var existing = paymentRepository.findByBookingId(booking.getId());
        if (existing.isPresent()) {
            Payment p = existing.get();
            return new InitiatePaymentResponse(p.getId(), booking.getId(), p.getAmount(),
                    p.getMethod(), p.getStatus(), true,
                    "Payment already exists for this booking — returning the existing payment.");
        }

        // Booking must be confirmed (or further along, up to DELIVERED) before anyone pays.
        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not confirmed yet — the owner has not accepted it");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REJECTED) {
            throw new IllegalStateException("Booking is already " + booking.getStatus() + " — it cannot be paid");
        }

        // Online methods: gateway is not connected yet. No row is created.
        if (req.method() != PaymentMethod.COD) {
            // The stub throws UnsupportedOperationException on purpose.
            razorpayGateway.initiate(null);
            // Unreachable — the line above always throws. It is here so the
            // intent is visible: online goes through the gateway, never past it.
            throw new IllegalStateException("Online payment is not connected yet");
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setCustomer(booking.getCustomer());
        // If your Module 4 Booking has a direct getOwner(), use it here instead:
        //   payment.setOwner(booking.getOwner());
        payment.setOwner(booking.getTruck().getOwner());
        payment.setAmount(booking.getTotalFare()); // snapshot — never from the client
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setGatewayReference(null); // COD has no gateway reference

        manualGateway.initiate(payment); // returns PENDING; no money moves

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            // Race guard: two requests slipped past the lookup at the same
            // instant and the database's unique constraint on booking_id
            // refused the second row. Return the payment that did get saved.
            Payment saved = paymentRepository.findByBookingId(booking.getId())
                    .orElseThrow(() -> ex);
            return new InitiatePaymentResponse(saved.getId(), booking.getId(), saved.getAmount(),
                    saved.getMethod(), saved.getStatus(), true,
                    "Payment already exists for this booking — returning the existing payment.");
        }

        return new InitiatePaymentResponse(payment.getId(), booking.getId(), payment.getAmount(),
                payment.getMethod(), payment.getStatus(), false,
                "COD payment created. Pay in cash on delivery; the owner will confirm receipt.");
    }

    /**
     * Owner confirms cash was received, in person, on delivery.
     * Only allowed when: the caller owns the truck, the booking is
     * DELIVERED, the payment is PENDING, and the method is COD.
     * On success: payment SUCCESS + paidAt, booking DELIVERED -> COMPLETED,
     * and a settlement row is created (gross 33,280 / fee 0 / owner 33,280).
     */
    @Transactional
    public PaymentResponse confirmCashPayment(User owner, Long paymentId,
                                              ConfirmCashPaymentRequest req) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!payment.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("This payment does not belong to your truck");
        }
        if (payment.getMethod() != PaymentMethod.COD) {
            throw new IllegalStateException("Only COD payments can be confirmed in cash");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Payment is already successful");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is " + payment.getStatus() + " — it cannot be confirmed");
        }
        Booking booking = payment.getBooking();
        if (booking.getStatus() != BookingStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Booking is not delivered yet (it is " + booking.getStatus() + ") — "
                    + "cash can only be confirmed after delivery");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(java.time.LocalDateTime.now());
        payment = paymentRepository.save(payment);

        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        // The settlement is created here, and only here — at SUCCESS.
        settlementService.createSettlement(payment);

        return PaymentResponse.from(payment);
    }

    /**
     * Called by Module 4 when a booking is cancelled (before pickup).
     * A PENDING COD payment becomes CANCELLED — no money moved, so there
     * is no refund. A SUCCESS payment is left alone: the customer must
     * request a refund through RefundService (Part 5), and the admin
     * decides. Do NOT auto-refund here.
     */
    @Transactional
    public void cancelPendingPaymentForBooking(Long bookingId) {
        paymentRepository.findByBookingId(bookingId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        });
    }

    /** Customer reads one of their own payments. */
    @Transactional(readOnly = true)
    public PaymentResponse getMyPayment(User customer, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (!payment.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This payment does not belong to you");
        }
        return PaymentResponse.from(payment);
    }

    /** Customer lists their own payments, newest first. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> myPayments(User customer) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(PaymentResponse::from).toList();
    }
}
```

Three things to notice in this code:

1. **`initiatePayment` never trusts the client for money.** The request has no amount field at all, and the amount is set from `booking.getTotalFare()` in exactly one line. If a customer sends `amount: 1` in JSON, Spring ignores the unknown field and the payment is still for the full fare.
2. **The online path throws before any row exists.** `razorpayGateway.initiate(null)` throws `UnsupportedOperationException`, which the controller (Part 9) turns into a 503. Because the throw happens before `paymentRepository.save(...)`, there is no half-created online payment to clean up later.
3. **`confirmCashPayment` is the only place in the whole project that sets a payment to SUCCESS in this MVP.** Search the codebase for `setStatus(PaymentStatus.SUCCESS)` — it should appear exactly once, in this method. If it ever appears in a controller or a DTO, that is a bug.

> **Getter names to check:** this code calls `booking.getCustomer()`, `booking.getTruck().getOwner()`, `booking.getTotalFare()`, `booking.getStatus()`, `booking.setStatus(...)`. If your Module 4 named any of these differently, change only those calls (Part 0 table).

### Files 15–19 of 26 — settlement

**`settlement/SettlementStatus.java`**

```java
package com.truckconnect.settlement;

public enum SettlementStatus {
    PENDING, // Settlement created at payment SUCCESS; admin has not settled it yet
    PAID     // Admin has done the manual transfer / received the fee and marked it settled
}
```

**`settlement/OwnerSettlement.java`**

**Simple meaning:** One row is the split of one successful payment: what the customer paid, what the platform keeps, what the owner keeps. One settlement per booking — a second SUCCESS for the same booking must never create a second settlement.

```java
package com.truckconnect.settlement;

import com.truckconnect.booking.Booking;
import com.truckconnect.payment.Payment;
import com.truckconnect.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "owner_settlements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id"}))
public class OwnerSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "owner_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal ownerAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(length = 500)
    private String note;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OwnerSettlement() { }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User o) { this.owner = o; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking b) { this.booking = b; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment p) { this.payment = p; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal v) { this.grossAmount = v; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal v) { this.platformFee = v; }
    public BigDecimal getOwnerAmount() { return ownerAmount; }
    public void setOwnerAmount(BigDecimal v) { this.ownerAmount = v; }
    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus s) { this.status = s; }
    public String getNote() { return note; }
    public void setNote(String n) { this.note = n; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime t) { this.settledAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**`settlement/OwnerSettlementRepository.java`**

```java
package com.truckconnect.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OwnerSettlementRepository extends JpaRepository<OwnerSettlement, Long> {
    Optional<OwnerSettlement> findByBookingId(Long bookingId);
    List<OwnerSettlement> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<OwnerSettlement> findByStatusOrderByCreatedAtDesc(SettlementStatus status);
}
```

**`settlement/dto/SettlementResponse.java`**

```java
package com.truckconnect.settlement.dto;

import com.truckconnect.settlement.OwnerSettlement;
import com.truckconnect.settlement.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementResponse(
        Long settlementId,
        Long bookingId,
        Long paymentId,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal ownerAmount,
        SettlementStatus status,
        String note,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {
    public static SettlementResponse from(OwnerSettlement s) {
        return new SettlementResponse(
                s.getId(), s.getBooking().getId(), s.getPayment().getId(),
                s.getGrossAmount(), s.getPlatformFee(), s.getOwnerAmount(),
                s.getStatus(), s.getNote(), s.getSettledAt(), s.getCreatedAt());
    }
}
```

**`settlement/SettlementService.java`**

**Simple meaning:** One subtraction, done once, at the right moment. `createSettlement` runs only when a payment becomes SUCCESS. `markPaid` is the admin saying "the manual transfer is done" — it moves no money itself.

```java
package com.truckconnect.settlement;

import com.truckconnect.payment.Payment;
import com.truckconnect.payment.PaymentStatus;
import com.truckconnect.pricing.PlatformSettingsRepository;
import com.truckconnect.settlement.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettlementService {

    private final OwnerSettlementRepository settlementRepository;
    private final PlatformSettingsRepository settingsRepository;

    public SettlementService(OwnerSettlementRepository settlementRepository,
                             PlatformSettingsRepository settingsRepository) {
        this.settlementRepository = settlementRepository;
        this.settingsRepository = settingsRepository;
    }

    /**
     * Create the settlement for a SUCCESSFUL payment.
     * gross  = payment amount (e.g. 33,280) — the snapshot from the booking.
     * fee    = platform fee from Module 3 settings (0) — never hardcoded here.
     * owner  = gross - fee (e.g. 33,280). That is the whole arithmetic.
     * Called only from PaymentService.confirmCashPayment (and, later,
     * from the Razorpay webhook handler when a payment becomes SUCCESS).
     */
    @Transactional
    public OwnerSettlement createSettlement(Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Settlement can only be created for a SUCCESSFUL payment");
        }
        // One settlement per booking: if it already exists, return it.
        var existing = settlementRepository.findByBookingId(payment.getBooking().getId());
        if (existing.isPresent()) return existing.get();

        BigDecimal fee = settingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Platform settings not found"))
                .getPlatformFee();

        OwnerSettlement settlement = new OwnerSettlement();
        settlement.setOwner(payment.getOwner());
        settlement.setBooking(payment.getBooking());
        settlement.setPayment(payment);
        settlement.setGrossAmount(payment.getAmount());
        settlement.setPlatformFee(fee);
        settlement.setOwnerAmount(payment.getAmount().subtract(fee));
        settlement.setStatus(SettlementStatus.PENDING);
        return settlementRepository.save(settlement);
    }

    /**
     * Admin marks a settlement PAID after doing the manual bank transfer
     * OUTSIDE the app (for online payments later; for COD there is no
     * transfer — the owner already holds the cash, see Part 4). This method
     * moves no money; it records that the money already moved.
     */
    @Transactional
    public SettlementResponse markPaid(Long settlementId, String note) {
        OwnerSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));
        if (settlement.getStatus() == SettlementStatus.PAID) {
            throw new IllegalStateException("Settlement is already PAID");
        }
        settlement.setStatus(SettlementStatus.PAID);
        settlement.setSettledAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) settlement.setNote(note);
        return SettlementResponse.from(settlementRepository.save(settlement));
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> mySettlements(Long ownerId) {
        return settlementRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(SettlementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> allSettlements() {
        return settlementRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(SettlementResponse::from).toList();
    }
}
```

> **Check Module 3 first:** this service reads the fee from `PlatformSettingsRepository` (`findById(1L)` → `getPlatformFee()`), the same settings row Module 3 seeds with ₹0. If your Module 3 named the getter differently, change only that one call. If you ever change the fee in the admin rate screen, new settlements use the new fee and old settlements keep the fee they were created with — because the fee is copied onto the settlement row, exactly like the rate is copied onto the quote row in Module 3.

### Files 21–26 of 26 — refunds

**`refund/RefundStatus.java`**

```java
package com.truckconnect.refund;

public enum RefundStatus {
    PENDING,   // Customer asked; admin has not decided yet
    PROCESSED, // Admin approved AND refunded the money manually outside the app
    REJECTED   // Admin refused; the payment stays SUCCESS
}
```

**`refund/Refund.java`**

```java
package com.truckconnect.refund;

import com.truckconnect.payment.Payment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public Refund() { }

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment p) { this.payment = p; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }
    public String getReason() { return reason; }
    public void setReason(String r) { this.reason = r; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus s) { this.status = s; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String n) { this.adminNote = n; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime t) { this.processedAt = t; }
}
```

**`refund/RefundRepository.java`**

```java
package com.truckconnect.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    List<Refund> findByStatusOrderByCreatedAtDesc(RefundStatus status);
}
```

**`refund/dto/RefundRequest.java`** — what the customer sends. The amount must be ≤ the payment amount; the service checks that (a customer cannot refund more than was paid).

```java
package com.truckconnect.refund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RefundRequest(
        @NotNull Long paymentId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String reason
) { }
```

**`refund/dto/RefundResponse.java`**

```java
package com.truckconnect.refund.dto;

import com.truckconnect.refund.Refund;
import com.truckconnect.refund.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
    public static RefundResponse from(Refund r) {
        return new RefundResponse(
                r.getId(), r.getPayment().getId(), r.getAmount(), r.getReason(),
                r.getStatus(), r.getAdminNote(), r.getCreatedAt(), r.getProcessedAt());
    }
}
```

**`refund/RefundService.java`**

**Simple meaning:** Two methods, two roles. The customer asks (only for a SUCCESS payment, only their own, only up to the amount paid). The admin decides — and "PROCESSED" means "I have already sent the money back by hand outside the app", not "the app sent it". The payment status only changes to REFUNDED / PARTIAL_REFUND when the admin processes it, never when the customer asks.

```java
package com.truckconnect.refund;

import com.truckconnect.payment.Payment;
import com.truckconnect.payment.PaymentRepository;
import com.truckconnect.payment.PaymentStatus;
import com.truckconnect.refund.dto.RefundRequest;
import com.truckconnect.refund.dto.RefundResponse;
import com.truckconnect.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Customer requests a refund for one of their own SUCCESSFUL payments. */
    @Transactional
    public RefundResponse requestRefund(User customer, RefundRequest req) {
        Payment payment = paymentRepository.findById(req.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + req.paymentId()));
        if (!payment.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This payment does not belong to you");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Only a SUCCESSFUL payment can be refunded (this one is " + payment.getStatus() + "). "
                    + "A PENDING COD payment on a cancelled booking is simply cancelled — no refund is needed.");
        }
        if (req.amount().compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund amount cannot be more than the payment amount");
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setAmount(req.amount());
        refund.setReason(req.reason());
        refund.setStatus(RefundStatus.PENDING);
        return RefundResponse.from(refundRepository.save(refund));
    }

    /**
     * Admin processes a refund. approve = true means "I have ALREADY
     * refunded this money manually outside the app (UPI / bank transfer)".
     * The payment becomes REFUNDED (full amount) or PARTIAL_REFUND (less).
     * approve = false means REJECTED; the payment stays SUCCESS.
     */
    @Transactional
    public RefundResponse processRefund(Long refundId, boolean approve, String adminNote) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("Refund is already " + refund.getStatus());
        }
        Payment payment = refund.getPayment();
        if (approve) {
            refund.setStatus(RefundStatus.PROCESSED);
            payment.setStatus(refund.getAmount().compareTo(payment.getAmount()) == 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIAL_REFUND);
            paymentRepository.save(payment);
        } else {
            refund.setStatus(RefundStatus.REJECTED);
        }
        refund.setAdminNote(adminNote);
        refund.setProcessedAt(LocalDateTime.now());
        return RefundResponse.from(refundRepository.save(refund));
    }
}
```

---

## Part 9 — The controllers

### File 13 of 26 — `payment/PaymentController.java` (customer)

**Simple meaning:** The customer's doors. The customer comes from the JWT (Module 1), never from the request body — so a customer can only ever pay for, and see, their own bookings. The 503 for online payments is produced here, from the stub's exception, so the customer gets a clear message instead of a stack trace.

```java
package com.truckconnect.payment;

import com.truckconnect.payment.dto.*;
import com.truckconnect.refund.RefundService;
import com.truckconnect.refund.dto.RefundRequest;
import com.truckconnect.refund.dto.RefundResponse;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InitiatePaymentResponse> initiate(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody InitiatePaymentRequest request) {
        try {
            return ResponseEntity.ok(paymentService.initiatePayment(customer, request));
        } catch (UnsupportedOperationException ex) {
            // Razorpay stub: online payment is not connected yet.
            // 503 (not 400, not 500) — the service is unavailable for now, not broken.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    new InitiatePaymentResponse(null, request.bookingId(), null,
                            request.method(), null, false,
                            "Online payment is not connected yet. Please pay by cash on delivery (COD)."));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> getOne(
            @AuthenticationPrincipal User customer,
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getMyPayment(customer, id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<PaymentResponse>> mine(
            @AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(paymentService.myPayments(customer));
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<RefundResponse> requestRefund(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundService.requestRefund(customer, request));
    }
}
```

> **Note on `POST /api/payments/refunds`:** it creates a refund *request* (status PENDING), not a refund itself. The name says "request" for a reason — no money moves in this call. The admin's process step in the next controller is what completes it.

### File 14 of 26 — `payment/OwnerPaymentController.java` (owner)

**Simple meaning:** The owner's doors. Confirming cash is the most sensitive action in this module — it creates money in the system — so it is restricted to the owner of that truck, and the service re-checks everything (Part 8) even though the role check already passed.

```java
package com.truckconnect.payment;

import com.truckconnect.payment.dto.ConfirmCashPaymentRequest;
import com.truckconnect.payment.dto.PaymentResponse;
import com.truckconnect.settlement.SettlementService;
import com.truckconnect.settlement.dto.SettlementResponse;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/owner/payments")
public class OwnerPaymentController {

    private final PaymentService paymentService;
    private final SettlementService settlementService;

    public OwnerPaymentController(PaymentService paymentService,
                                  SettlementService settlementService) {
        this.paymentService = paymentService;
        this.settlementService = settlementService;
    }

    @PostMapping("/{paymentId}/confirm-cash")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PaymentResponse> confirmCash(
            @AuthenticationPrincipal User owner,
            @PathVariable Long paymentId,
            @Valid @RequestBody ConfirmCashPaymentRequest request) {
        return ResponseEntity.ok(
                paymentService.confirmCashPayment(owner, paymentId, request));
    }

    @GetMapping("/settlements")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<SettlementResponse>> mySettlements(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(settlementService.mySettlements(owner.getId()));
    }
}
```

> **TRANSPORTER note:** a transporter manages many trucks and drivers (Module 1 roles), but in this MVP only the truck's `owner` can confirm cash. If a transporter owns trucks through the same `owner` link, the same check works. If your Module 2 gives transporters a separate link to trucks, add `hasAnyRole('OWNER','TRANSPORTER')` here and check ownership through that link instead — one line, in this controller and in `PaymentService.confirmCashPayment`.

### File 20 of 26 — `settlement/AdminSettlementController.java` (admin)

**Simple meaning:** The admin's doors. Every method here records that money already moved by hand outside the app — none of them moves money. The class-level `@PreAuthorize("hasRole('ADMIN')")` means every endpoint in it is admin-only; a customer or owner calling any of them gets 403 before the service runs.

```java
package com.truckconnect.settlement;

import com.truckconnect.refund.RefundService;
import com.truckconnect.refund.dto.RefundResponse;
import com.truckconnect.settlement.dto.SettlementResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private final SettlementService settlementService;
    private final RefundService refundService;

    public AdminSettlementController(SettlementService settlementService,
                                     RefundService refundService) {
        this.settlementService = settlementService;
        this.refundService = refundService;
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<SettlementResponse>> allSettlements() {
        return ResponseEntity.ok(settlementService.allSettlements());
    }

    /** Admin has done the manual bank transfer outside the app — record it. */
    @PostMapping("/settlements/{id}/mark-paid")
    public ResponseEntity<SettlementResponse> markPaid(
            @PathVariable Long id,
            @RequestBody(required = false) MarkPaidRequest request) {
        String note = request == null ? null : request.note();
        return ResponseEntity.ok(settlementService.markPaid(id, note));
    }

    /** Admin processes a refund: approve = "I already refunded it by hand". */
    @PostMapping("/refunds/{id}/process")
    public ResponseEntity<RefundResponse> processRefund(
            @PathVariable Long id,
            @Valid @RequestBody ProcessRefundRequest request) {
        return ResponseEntity.ok(
                refundService.processRefund(id, request.approve(), request.adminNote()));
    }

    public record MarkPaidRequest(String note) { }

    public record ProcessRefundRequest(boolean approve, String adminNote) { }
}
```

> **Error handling:** `IllegalArgumentException` and `IllegalStateException` from the services return 400 through Module 1's `GlobalExceptionHandler`, and `AccessDeniedException` returns 403 — the same handler already covers both, so no new error class is needed in this module. `UnsupportedOperationException` from the Razorpay stub is caught in `PaymentController` and returned as 503; if it ever escapes anywhere else, the generic handler returns 500 — which is correct, because an online payment reaching any other path would be a real bug.

### Security — the rules, in one place

| Rule | Where it is enforced | What happens if it is broken |
| --- | --- | --- |
| No token at all | `SecurityConfig` (Module 1): `/api/**` needs a login | 401 Unauthorized |
| Wrong role (e.g. CUSTOMER calls an admin endpoint) | `@PreAuthorize` on the controller | 403 Forbidden |
| Customer pays for someone else's booking | `PaymentService.initiatePayment` ownership check | 403 Forbidden (`AccessDeniedException`) |
| Customer reads someone else's payment | `PaymentService.getMyPayment` ownership check | 403 Forbidden |
| Owner confirms cash for someone else's truck | `PaymentService.confirmCashPayment` ownership check | 403 Forbidden |
| Owner reads someone else's settlements | `SettlementService.mySettlements(owner.getId())` — the id comes from the JWT, never the URL | Owner only ever sees their own rows |
| Amount tampering (customer sends a smaller amount) | Impossible by design — the request DTOs have **no amount field** for initiate/confirm; the amount is copied from `booking.getTotalFare()` | The tampered field is ignored; the payment is for the full fare |
| Payment SUCCESS without verification | Only `PaymentService.confirmCashPayment` (owner, after DELIVERED) sets SUCCESS in this MVP; the gateway path requires a verified webhook later | No endpoint, DTO, or frontend call can set SUCCESS directly |
| Double payment for one booking | Unique constraint on `payments.booking_id` + lookup-first in the service + race catch | Second call returns the existing payment with `idempotentReplay: true` |

---

## Part 10 — Test checklist (do these in order)

You need: the app running, one ADMIN token, one CUSTOMER token, a second CUSTOMER token (a different customer), one OWNER token, and one booking in CONFIRMED status with `totalFare` ₹33,280 (Module 3, Example 1: 570 km, 20 tons, 12-tyre — check the `bookings` table to confirm the snapshot before you start). Move the booking to DELIVERED through the Module 5 trip flow when a test needs it; do not edit the status directly in the database unless you are testing the service and say so in your notes.

| # | Test | Expected result |
| --- | --- | --- |
| 1 | `POST /api/payments/initiate` as CUSTOMER: `{ bookingId, method: "COD" }`. | 200. Payment created, status PENDING, amount **₹33,280** (copied from the booking), `gatewayReference: null`, `idempotentReplay: false`. Check the `payments` table: exactly 1 row for this booking. |
| 2 | Send the **same** initiate request again (same booking, same customer). | 200. **Same payment id**, same amount, `idempotentReplay: true`. Check the table: still exactly 1 row. This is the idempotency test — if a second row appears, the unique constraint or the lookup is missing. |
| 3 | Send initiate with an extra field in JSON: `{ bookingId, method: "COD", amount: 1 }`. | 200 (or 400 for the unknown field, depending on your Jackson settings — either is fine). The payment amount is still **₹33,280**. Amount tampering is ignored, because the amount never comes from the client. |
| 4 | `POST /api/payments/initiate` as CUSTOMER with `method: "UPI"` (then try `"CARD"`). | **503 Service Unavailable**, message "Online payment is not connected yet…". Check the table: **no new payment row** was created for that attempt. |
| 5 | Initiate COD for a booking that is still PENDING (owner has not accepted). | 400: "Booking is not confirmed yet". No payment row. |
| 6 | Owner confirms cash **before** delivery: `POST /api/owner/payments/{id}/confirm-cash` while the booking is CONFIRMED (not DELIVERED). | 400: "Booking is not delivered yet". Payment stays PENDING, booking stays CONFIRMED. |
| 7 | Move the booking to DELIVERED (Module 5 trip flow, with delivery proof), then owner confirms cash. | 200. Payment SUCCESS with `paidAt` set; booking DELIVERED → **COMPLETED**; a settlement row exists: gross **₹33,280**, platform fee **₹0**, owner amount **₹33,280**, status PENDING. Check by hand: 33,280 + 0 = 33,280. |
| 8 | Owner confirms the **same** payment a second time. | 400: "Payment is already successful". No second settlement row — check `owner_settlements`: still 1 row for this booking. |
| 9 | Admin marks the settlement PAID: `POST /api/admin/settlements/{id}/mark-paid` with note "Cash received by owner on delivery". | 200. Settlement PAID, `settledAt` set, note saved. Mark it PAID again → 400: "already PAID". |
| 10 | `GET /api/payments/{id}` as the **second** customer (not the booking's customer). | 403 Forbidden. Same for `GET /api/payments/mine` — the second customer must not see the first customer's payment in their list. |
| 11 | Owner B (a different owner) calls confirm-cash on owner A's payment. | 403 Forbidden. Payment stays SUCCESS / PENDING as it was — nothing changes. |
| 12 | Customer calls `POST /api/owner/payments/{id}/confirm-cash`, and owner calls `POST /api/admin/settlements/{id}/mark-paid`. | 403 Forbidden both times (wrong role). Role checks run before the service. |
| 13 | Cancel a **different** booking before pickup that has a PENDING COD payment (call Module 4 cancel, which calls `cancelPendingPaymentForBooking`). | Payment becomes **CANCELLED**. No refund row exists. Booking is CANCELLED. |
| 14 | Request a refund for the PENDING (now CANCELLED) payment from test 13: `POST /api/payments/refunds`. | 400: "Only a SUCCESSFUL payment can be refunded". Correct — nothing was paid, so there is nothing to refund. |
| 15 | No token at all: `GET /api/payments/mine` with no `Authorization` header. | 401 Unauthorized. |
| 16 | **Payment SUCCESS without verification is impossible:** search the codebase for `setStatus(PaymentStatus.SUCCESS)`. | It appears **exactly once** — in `PaymentService.confirmCashPayment`. There is no endpoint, DTO field, or frontend call that can set SUCCESS directly. (In the later Razorpay module, a second occurrence will appear only inside the verified-webhook handler.) |

### Common mistakes

- **Letting the client send the amount.** If `InitiatePaymentRequest` or `ConfirmCashPaymentRequest` ever gains an `amount` field, a customer can pay ₹1 for a ₹33,280 trip. The amount comes from `booking.getTotalFare()` and nowhere else. If a test ever shows a payment amount that is not the booking's total fare, this is the first place to look.
- **Confirming cash before delivery.** The confirm endpoint checks `booking.getStatus() == DELIVERED` for a reason. Removing that check "to make testing easier" lets an owner mark a load paid before it arrives — and the customer loses their only leverage. Keep the check; move the booking to DELIVERED properly in tests instead.
- **Creating a settlement at initiate time.** A PENDING payment owes nobody anything. If settlements appear for unpaid bookings, the admin will try to pay owners for trips that never happened. Settlements are created only at SUCCESS, in `confirmCashPayment`.
- **Treating the COD settlement as "admin pays the owner ₹33,280".** The owner already has the cash (Part 4). The admin step only records that the owner received it; with a ₹0 platform fee there is no fee to settle. If you wire a real bank payout to the COD settlement amount, you will pay the owner twice.
- **Adding a Razorpay key "just to test".** There is no test key in this module on purpose. A test key in code becomes a real key in Git, and a real key in Git becomes someone else's money. The stub throwing `UnsupportedOperationException` is the correct behaviour — do not "fix" it until the later module, with environment variables and a real account.
- **Forgetting the unique constraint on `booking_id`.** The lookup-first check in the service is not enough on its own — two requests in the same millisecond can both pass the lookup. The database constraint is the real guard; the service catch block turns its error into the existing payment instead of a 500.
- **Using `double` for money.** `0.1 + 0.2` is `0.30000000000000004` in a double. `BigDecimal` with scale 2 is the only correct choice — and the settlement check (33,280 + 0 = 33,280) is the hand calculation that catches a rounding bug before a customer does.
- **Marking a payment SUCCESS from the frontend or from a refund request.** A refund request is PENDING until the admin processes it; a payment is PENDING until the owner confirms cash or a verified webhook arrives. Any code path that sets SUCCESS from a plain HTTP request with no verification is a hole, not a shortcut.

---

## What is next: Module 7 — React Frontend

**Module 7 — React Frontend (next):** the screens that use these endpoints — customer: initiate COD payment, see my payments, request a refund; owner: confirm cash received, see my settlements; admin: see all settlements, mark a settlement PAID, process refunds. The frontend must show the payment breakdown (gross, fee, owner share) from `SettlementResponse`, must never send an amount to the initiate/confirm endpoints (there is no amount field to send), and must show a clear "Online payment is not connected yet — please pay by cash on delivery" message when the initiate call returns 503, instead of showing a generic error.

**After Module 7:** admin screens and deployment — the same free stack as ShopKart (Vercel + Render + TiDB Cloud free MySQL, platform fee ₹0 to run), and only then the later Razorpay module, with a real account, environment-variable keys, and webhook signature verification replacing the stub in Part 7.
