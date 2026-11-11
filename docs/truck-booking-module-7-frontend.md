# Truck Booking — Module 7: React Frontend

**For:** A beginner learning step by step, one module at a time
**Project name:** Truck Booking (renamed from TruckConnect on 11 Nov 2026 — the code package stays `com.truckconnect` so Modules 1–6 keep working; rename the package later only if you want to)
**Stack:** React 18 + Vite, JavaScript (no TypeScript), Tailwind CSS, React Router, axios — one single `api.js` for all backend calls
**Builds on:** Module 1 — Authentication & Security (JWT, roles) · Module 2 — Truck Categories & Registration (slim version) · Module 3 — Search, Matching & Pricing (quotes) · Module 4 — Booking (assumed, see Part 0) · Module 5 — Drivers & Trips (assumed) · Module 6 — Payments (assumed)
**This module only:** The screens a real person sees and taps — customer search and booking, owner trucks and trips, driver trips, and admin management. No new backend code, no deployment, no real payment — Module 8 covers deployment.

## How to use this guide

1. Read Part 0 first — it lists every backend endpoint this frontend expects, and flags the ones that are assumed (Modules 4–6 are being built in parallel). If an endpoint name differs in your backend, change it in **one place only** — the service file named in Part 0 — not in every page.
2. Read Parts 1 and 2 so you understand *why* the frontend is shaped this way.
3. Set up the project in Part 3, then add one file at a time in VS Code, in the order given in Part 4.
4. Start the backend first, then the frontend, and work through the test checklist in Part 12.
5. Modules 1 and 3 must already be working — you need a login token and real quotes before you can test anything here. Modules 4–6 should be working too for the full flow; if one of them is not ready yet, the page that needs it will show an error, and Part 12 tells you which tests to skip until it is ready.

> **The one rule of this module:** the frontend **never decides a price**. A price always comes from the backend — from a quote (Module 3) or from a booking that was created from a quote (Module 4). The frontend only *shows* the price, with its full breakdown. If you ever find yourself typing a fare calculation into a React file, stop — that calculation belongs on the server.

---

## Part 0 — What this frontend assumes (read this first)

Modules 4, 5 and 6 are being built in parallel, so this module does **not** wait for them. It assumes the API contracts below. Every assumed endpoint is marked **[ASSUMED — check against Modules 4–6]**. If the real endpoint differs, change only the one line in the service file — the pages never call `fetch` or `axios` directly.

### ⚠️ Endpoint prefix — check this first

Module 1 (the real, delivered code) uses **`/api/auth/register`**, **`/api/auth/login`**, **`/api/auth/refresh`**, and **`GET /api/auth/me`** — **not** `/api/v1/auth/...`. This guide follows Module 1, because Module 1 is the code that actually runs. If your backend was later moved to `/api/v1/...`, change only the `baseURL` in `src/services/api.js` (Part 5) — every other file stays the same.

Auth response (Module 1, confirmed): `POST /api/auth/login` returns `{ userId, fullName, email, role, accessToken, refreshToken, tokenType: "Bearer" }`. Access token lives 15 minutes, refresh token 7 days, refresh request body is `{ refreshToken }`, and the refresh token has a `type: "REFRESH"` claim. Register returns no token — the user then logs in. Register rejects `role: "ADMIN"`.

### API contract table

| Area | Endpoint | Used by | Status |
| --- | --- | --- | --- |
| Auth | `POST /api/auth/register` `{ fullName, email, phone, password, role }` | Register page | Module 1 — confirmed |
| Auth | `POST /api/auth/login` `{ email, password }` | Login page | Module 1 — confirmed |
| Auth | `POST /api/auth/refresh` `{ refreshToken }` | `api.js` (automatic) | Module 1 — confirmed |
| Auth | `GET /api/auth/me` | Profile page | Module 1 — confirmed |
| Quotes | `POST /api/quotes/search` `{ pickup, destination, distanceKm, loadTons, goodsType, bodyType?, categoryId?, tollCharges?, loadingCharges? }` | Home / Search Results | Module 3 — confirmed |
| Quotes | `GET /api/admin/rates` | Admin Rate Management | Module 3 — confirmed |
| Quotes | `PUT /api/admin/rates/{id}` `{ ratePerTonKm, active? }` | Admin Rate Management | Module 3 — confirmed |
| Trucks | `GET /api/trucks/my` — owner's own trucks **[ASSUMED — check Module 2]** | Owner My Trucks | Assumed |
| Trucks | `POST /api/trucks` `{ registrationNumber, categoryId, bodyType, payloadTons }` **[ASSUMED — check Module 2]** | Owner Add Truck | Assumed |
| Trucks | `PATCH /api/trucks/{id}/availability` `{ availabilityStatus }` **[ASSUMED — check Module 2]** | Owner availability toggle | Assumed |
| Trucks | `GET /api/trucks/categories` — list of 4 categories **[ASSUMED — check Module 2]** | Search form, Add Truck form | Assumed |
| Bookings | `POST /api/bookings` `{ quoteId }` **[ASSUMED — check Module 4]** | Booking Confirm | Assumed |
| Bookings | `GET /api/bookings/my` **[ASSUMED — check Module 4]** | My Bookings, Owner Dashboard | Assumed |
| Bookings | `GET /api/bookings/{id}` **[ASSUMED — check Module 4]** | Tracking | Assumed |
| Bookings | `POST /api/bookings/{id}/accept` / `POST /api/bookings/{id}/reject` **[ASSUMED — check Module 4]** | Owner Incoming Bookings | Assumed |
| Bookings | `POST /api/bookings/{id}/cancel` **[ASSUMED — check Module 4]** | My Bookings (customer cancel) | Assumed |
| Messages | `GET /api/bookings/{id}/messages`, `POST /api/bookings/{id}/messages` `{ text }` **[ASSUMED — check Module 4]** | Tracking chat box | Assumed |
| Drivers | `GET /api/drivers/my`, `POST /api/drivers`, `PUT /api/drivers/{id}`, `PATCH /api/drivers/{id}/deactivate` **[ASSUMED — check Module 5]** | Owner My Drivers | Assumed |
| Trips | `GET /api/trips/my`, `POST /api/trips/{id}/assign` `{ driverId }`, `POST /api/trips/{id}/status` `{ status, deliveryProofNote?, deliveryProofUrl? }` **[ASSUMED — check Module 5]** | Owner My Trips, Driver My Trips | Assumed |
| Payments | `GET /api/payments/booking/{bookingId}`, `GET /api/payments/settlements/my` **[ASSUMED — check Module 6]** | Tracking, Owner Earnings | Assumed |
| Admin | `GET /api/admin/users`, `PATCH /api/admin/users/{id}/deactivate` **[ASSUMED — check Module 6/admin work]** | Admin User Management | Assumed |
| Admin | `GET /api/admin/bookings`, `GET /api/admin/settlements`, `POST /api/admin/settlements/{id}/mark-paid` **[ASSUMED]** | Admin Dashboard / Settlements | Assumed |
| Admin | `GET /api/admin/categories`, `POST /api/admin/categories` **[ASSUMED — check Module 2]** | Admin Category Management | Assumed |

> **How to use this table when something breaks:** if a page shows an error, open the browser DevTools → Network tab, look at the exact URL that failed, and compare it with this table. Nine times out of ten the fix is one wrong URL in one service file — not a problem in the page.

### What the frontend never sends

- Never a price, fare, total, or rate. The quote carries the price; the booking carries it onward.
- Never another user's id. The backend knows who you are from the JWT token; `GET /api/bookings/my` means "my bookings" because of the token, not because the frontend sent a user id.
- Never a role the user chose for themselves at login. The role comes from the token (Part 5), so a customer cannot pretend to be an admin by editing the page.

---

## Part 1 — What this module does, and why it comes seventh

### What this module does

Modules 1–6 built the engine: accounts, trucks, prices, bookings, trips, payments. But an engine with no steering wheel is not a vehicle anyone can drive. Module 7 is the steering wheel, the dashboard, and the doors — the screens for four kinds of people:

- **Customer** — search for a truck, see the fare breakdown, book instantly from a quote, watch the trip, chat with and call the owner.
- **Owner** — see incoming bookings, accept or reject them, manage trucks and drivers, assign trips, update trip status, see earnings.
- **Driver** — see assigned trips and update their status (read-only dashboard in this MVP — see Part 9 for the honest note about driver login).
- **Admin** — see counts, manage users, categories, rates, bookings, settlements, and disputed bookings.

### Why it comes seventh

A frontend built before the backend exists is guesswork: every button would call an endpoint that does not exist yet, and you would rewrite every page when the real API arrived. Now the APIs exist (or are fixed by the contracts in Part 0), so each page has a real job: call one endpoint, show what came back, and send back only what the backend asked for.

### What this module does not do

- No backend code at all — if an endpoint is missing, the fix is in Modules 1–6, not here.
- No deployment — Vercel (frontend) and Render (backend) come in Module 8. Here you run on your own computer only: frontend on `http://localhost:5173`, backend on `http://localhost:8080`.
- No real online payment screen — payments are COD by default in Module 6; Razorpay comes later. The frontend only *shows* the payment status the backend reports.
- No Google Maps — the customer still types the distance in km (Module 3, Part 3 explains why). A map can fill that field later without changing any page.
- No driver login — see Part 9. Do not invent one here.

---

## Part 2 — The big picture in simple words

Think of Truck Booking like a railway station with four different counters.

1. **The customer counter.** A customer walks up and says "I have 20 tons of rice, Bengaluru to Hyderabad." The clerk (the search form) writes it down, checks the rate board, and shows a list of trains — sorry, trucks — cheapest first, with every rupee explained. The customer picks one and books it on the spot. That is instant booking.
2. **The owner counter.** The owner sees a bell ring: "New booking!" They look at it, press Accept or Reject, pick a driver, and watch the truck move along its journey. When the goods arrive, they mark it delivered.
3. **The driver counter.** The driver sees only their own trips — "pick up here, deliver there" — and taps a button each time the trip moves to the next step.
4. **The manager's office (admin).** The manager sees how many users, bookings and trucks there are, changes the rate board when diesel prices move, and marks owner payments as paid.

One building, four counters, one shared rule: **nobody sees another person's counter**. A customer who types `/owner/trucks` in the address bar is sent back to the login page or to their own home page. That rule is enforced twice — by the frontend (so the user gets a friendly redirect) and by the backend (so a clever user cannot bypass the frontend).

### The flow, step by step

```text
VISITOR (not logged in)
  -> /login or /register
  -> POST /api/auth/login -> access token (15 min) + refresh token (7 days)
  -> token saved in localStorage, role read from the token
  -> user is sent to THEIR home page by role:
       CUSTOMER -> /           (search)
       OWNER / TRANSPORTER -> /owner
       ADMIN -> /admin
       DRIVER -> /driver       (see Part 9 note)

CUSTOMER
  -> Home: search form (pickup, destination, distance, load, goods, body type)
  -> POST /api/quotes/search -> quotes, cheapest first
  -> Search Results: QuoteCard with full fare breakdown + minimum-charge note
  -> Booking Confirm: shows the SAME quote price, never recalculated
  -> POST /api/bookings { quoteId } -> booking created, truck becomes busy
  -> My Bookings: list + status badge
  -> Tracking: timeline + chat box + Call Owner (tel: link)

OWNER
  -> Dashboard: active bookings + earnings summary
  -> My Trucks: list + slim add-truck form + availability toggle
  -> Incoming Bookings: Accept / Reject
  -> My Drivers: add / edit / deactivate
  -> My Trips: assign driver, update status, delivery proof
  -> Earnings: settlement list from Module 6

ADMIN
  -> Dashboard: counts -> Users -> Categories -> Rates -> Bookings
  -> Settlements: mark PAID -> Disputes: list DISPUTED bookings

EVERY API CALL (after login)
  -> api.js adds: Authorization: Bearer <access token>
  -> if the server answers 401 (token expired):
       api.js calls POST /api/auth/refresh ONCE, saves the new tokens,
       retries the original call ONCE
  -> if refresh also fails: log out, send the user to /login
```

### Who can see what

| Page | CUSTOMER | OWNER | TRANSPORTER | DRIVER | ADMIN |
| --- | :---: | :---: | :---: | :---: | --- |
| Home / search, My Bookings, Tracking | Yes | No | No | No | No |
| Owner Dashboard, My Trucks, Incoming Bookings, My Drivers, My Trips, Earnings | No | Yes | Yes* | No | No |
| Driver Dashboard, Driver My Trips | No | Yes† | No | Yes† | No |
| Admin Dashboard, Users, Categories, Rates, Bookings, Settlements, Disputes | No | No | No | No | Yes |

\* TRANSPORTER is treated like OWNER in this module — a transport company manages many trucks and drivers the same way an owner does. If Module 5 gives transporters a different trips endpoint, change only `truckService` / `driverService`.
† See Part 9: in this MVP the driver pages sit behind the OWNER role, because there is no driver login yet. A real DRIVER account can see them once Module 5 adds driver login — the route already allows both roles, so nothing else changes.

---

## Part 3 — Project setup: Vite + React + Tailwind

### Step 1 — Create the project

Open a terminal in VS Code, go to the folder where your backend lives (so both projects sit side by side), and run:

```bash
npm create vite@latest truck-booking-frontend -- --template react
cd truck-booking-frontend
npm install
npm install react-router-dom axios
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
npm run dev
```

Open `http://localhost:5173` in your browser. You should see the Vite + React welcome page. If you see it, the setup works — press `Ctrl+C` in the terminal to stop it for now; you will restart it after adding the files below.

> **Why Vite?** Vite is the standard way to start a React project in 2026. It starts in under a second and reloads the page instantly every time you save a file. `create-react-app` is no longer maintained — do not use it.

> **Why JavaScript, not TypeScript?** You are learning React and JavaScript together. TypeScript adds a second language of type annotations on top. Learn the React ideas first in plain JavaScript; TypeScript can be added later without rewriting the pages.

> **Why no extra UI library?** No Material UI, no Ant Design, no Chakra. Tailwind CSS classes (`bg-blue-600`, `p-4`, `rounded`) are enough for every screen in this module, and they keep the project small and the code readable. One styling system to learn, not two.

### Step 2 — Tailwind setup (3 small files)

**File 1 — `tailwind.config.js`** (replace the file Vite/Tailwind created):

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {},
  },
  plugins: [],
};
```

**File 2 — `src/index.css`** (replace the whole file):

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

body {
  font-family: system-ui, -apple-system, sans-serif;
}
```

**File 3 — `.env`** (new file, in the project root, next to `package.json`):

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

**File 4 — `.env.example`** (new file, same folder — this one IS committed to GitHub, the one above is NOT):

```env
# Copy this file to .env and fill in your own values. Never commit a real .env.
VITE_API_BASE_URL=http://localhost:8080/api
```

Then add `.env` to `.gitignore` if it is not there already:

```text
# in .gitignore — add this line
.env
```

> **`.env` rule:** `.env` holds only the backend address — no password, no secret, no token. `VITE_API_BASE_URL` is not a secret; it is just an address, and it is safe to show. The JWT secret lives only on the backend (Module 1) and never appears in this project.

### Step 3 — CORS note (backend side, one check)

Your backend (Module 1, `SecurityConfig`) already allows **one** origin, from `app.cors.allowed-origin`, default `http://localhost:5173`. That is exactly the address Vite uses, so local development works as it is.

If the frontend shows a CORS error in the browser console (`Access-Control-Allow-Origin`), check these, in order:

1. Is the backend running on port 8080? (`http://localhost:8080/api/auth/me` should answer — with a token — or 401 without one.)
2. Is the frontend really on port 5173? Vite prints its address when it starts. If it picked 5174 because 5173 was busy, either stop the other app or change the backend's `CORS_ORIGIN` environment variable to `http://localhost:5174` and restart the backend.
3. Did you change `VITE_API_BASE_URL`? It must end in `/api`, with no trailing slash after it.

**Do not** fix a CORS error by adding `*` (allow everyone) to the backend. That would let any website call your API. Module 8 changes the allowed origin to the real Vercel address when you deploy — that is the only change needed.

### Step 4 — Folder structure

Create these folders under `src/` (right-click `src` in VS Code → New Folder, or `mkdir -p` in the terminal):

```text
truck-booking-frontend/
  .env                  (your local backend address — NOT committed)
  .env.example          (template — committed)
  index.html
  package.json
  tailwind.config.js
  src/
    main.jsx            (entry point — renders <App />)
    index.css           (Tailwind — Part 3, Step 2)
    App.jsx             (all routes — Part 6)
    routes/
      ProtectedRoute.jsx
    context/
      AuthContext.jsx
    services/
      api.js            (axios instance + token refresh — the ONLY file that talks HTTP directly)
      authService.js
      quoteService.js
      truckService.js
      bookingService.js
      driverService.js
      tripService.js
      paymentService.js
      adminService.js
    utils/
      format.js         (rupee + date formatting)
      quote.js          (isQuoteExpired — the validUntil check)
    components/
      Navbar.jsx
      Footer.jsx
      LoadingSpinner.jsx
      ErrorBox.jsx
      StatusBadge.jsx
      TruckCard.jsx
      QuoteCard.jsx
      EmptyState.jsx
    pages/
      Login.jsx
      Register.jsx
      customer/
        Home.jsx
        SearchResults.jsx
        BookingConfirm.jsx
        MyBookings.jsx
        Tracking.jsx
        Profile.jsx
      owner/
        OwnerDashboard.jsx
        MyTrucks.jsx
        IncomingBookings.jsx
        MyDrivers.jsx
        MyTrips.jsx
        Earnings.jsx
      driver/
        DriverDashboard.jsx
        DriverTrips.jsx
      admin/
        AdminDashboard.jsx
        UserManagement.jsx
        CategoryManagement.jsx
        RateManagement.jsx
        BookingManagement.jsx
        SettlementManagement.jsx
        Disputes.jsx
```

**Simple meaning of the folders:** `services/` is the only place that knows backend URLs. `pages/` are the screens. `components/` are the small pieces reused by several pages (a card, a badge, a spinner). `context/` holds the login state for the whole app. If you keep that separation, a backend URL change is one line, and a design change to a card is one file.

### Step 5 — File list (41 files)

| # | File | What it is |
| --- | --- | --- |
| 1 | `.env` / `.env.example` | Backend address (Part 3) |
| 2 | `tailwind.config.js`, `src/index.css` | Tailwind setup (Part 3) |
| 3 | `src/utils/format.js` | Rupee + date formatting |
| 4 | `src/utils/quote.js` | `isQuoteExpired()` — validUntil check |
| 5 | `src/services/api.js` | axios instance, JWT attach, refresh-on-401-once |
| 6 | `src/services/authService.js` | register / login / refresh / me |
| 7 | `src/services/quoteService.js` | search quotes, list rates |
| 8 | `src/services/truckService.js` | owner trucks, categories |
| 9 | `src/services/bookingService.js` | bookings + messages |
| 10 | `src/services/driverService.js` | drivers |
| 11 | `src/services/tripService.js` | trips |
| 12 | `src/services/paymentService.js` | payment status + settlements |
| 13 | `src/services/adminService.js` | admin users / categories / rates / bookings / settlements |
| 14 | `src/context/AuthContext.jsx` | login state for the whole app |
| 15 | `src/routes/ProtectedRoute.jsx` | role check + redirect to login |
| 16 | `src/components/Navbar.jsx` | Top bar, changes by role |
| 17 | `src/components/Footer.jsx` | Bottom bar |
| 18 | `src/components/LoadingSpinner.jsx` | Loading state |
| 19 | `src/components/ErrorBox.jsx` | Error display |
| 20 | `src/components/StatusBadge.jsx` | Coloured badge per booking status |
| 21 | `src/components/TruckCard.jsx` | One truck, in a list |
| 22 | `src/components/QuoteCard.jsx` | One quote with full fare breakdown |
| 23 | `src/components/EmptyState.jsx` | "Nothing here" message |
| 24 | `src/pages/Login.jsx` + `src/pages/Register.jsx` | Auth pages |
| 25 | `src/pages/customer/Home.jsx` | Search form |
| 26 | `src/pages/customer/SearchResults.jsx` | Quotes cheapest first |
| 27 | `src/pages/customer/BookingConfirm.jsx` | Confirm from a quote |
| 28 | `src/pages/customer/MyBookings.jsx` | Customer booking list |
| 29 | `src/pages/customer/Tracking.jsx` | Timeline + chat + Call Owner |
| 30 | `src/pages/customer/Profile.jsx` | Own profile |
| 31 | `src/pages/owner/OwnerDashboard.jsx` | Owner home |
| 32 | `src/pages/owner/MyTrucks.jsx` | Trucks + slim add form + toggle |
| 33 | `src/pages/owner/IncomingBookings.jsx` | Accept / reject |
| 34 | `src/pages/owner/MyDrivers.jsx` | Drivers |
| 35 | `src/pages/owner/MyTrips.jsx` | Assign driver, update status, proof |
| 36 | `src/pages/owner/Earnings.jsx` | Settlements |
| 37 | `src/pages/driver/DriverDashboard.jsx` + `DriverTrips.jsx` | Driver pages (Part 9) |
| 38 | `src/pages/admin/AdminDashboard.jsx` | Admin home |
| 39 | `src/pages/admin/UserManagement.jsx` + `CategoryManagement.jsx` | Admin users + categories |
| 40 | `src/pages/admin/RateManagement.jsx` + `BookingManagement.jsx` | Admin rates + bookings |
| 41 | `src/pages/admin/SettlementManagement.jsx` + `Disputes.jsx` | Admin settlements + disputes |
| — | `src/App.jsx`, `src/main.jsx` | Routes + entry point |

Create them in this order: utils → services → context → routes → components → pages → `App.jsx` last (it imports everything else).


---

## Part 4 — Small helpers first: formatting and the quote-expiry check

### File — `src/utils/format.js`

**Simple meaning:** Every rupee and every date in the app is formatted by these two functions, so ₹33,280 never appears as `33280.0` on one page and `Rs. 33280` on another.

```js
// Format a number as Indian rupees: 33280 -> "₹33,280"
export function formatRupees(amount) {
  if (amount === null || amount === undefined || amount === "") return "—";
  return "₹" + Number(amount).toLocaleString("en-IN", {
    maximumFractionDigits: 2,
  });
}

// Format an ISO date string: "2026-11-12T10:30:00" -> "12 Nov 2026, 10:30 AM"
export function formatDateTime(isoString) {
  if (!isoString) return "—";
  return new Date(isoString).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

// Short date only, for table rows
export function formatDate(isoString) {
  if (!isoString) return "—";
  return new Date(isoString).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}
```

### File — `src/utils/quote.js`

**Simple meaning:** A quote is valid for 24 hours (Module 3). This one function answers "has it expired?" — every page that books from a quote uses it, so the rule lives in one place.

```js
// A quote has a validUntil timestamp from the backend (Module 3).
// Returns true when the quote can no longer be booked.
export function isQuoteExpired(quote) {
  if (!quote || !quote.validUntil) return true;
  return new Date(quote.validUntil).getTime() < Date.now();
}
```

---

## Part 5 — Services: the only files that talk to the backend

**The rule for this whole part:** pages never import `axios` and never write a URL. They import a service function (`searchQuotes(...)`, `createBooking(...)`) and get data back. If a backend URL changes, you change one line in one service file.

### File — `src/services/api.js` (the most important file in this module)

**Simple meaning:** This is the single postman for the whole app. It attaches the access token to every request, and when the token has expired (the server answers 401), it silently gets a new token with the refresh token, retries the original request once, and only gives up — logging the user out — if the refresh fails too.

```js
import axios from "axios";

// Base URL comes from .env. Local default: http://localhost:8080/api
// ⚠️ If your backend uses /api/v1/... instead of /api/..., change ONLY this
//    value in .env (VITE_API_BASE_URL) — no other file needs to change.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
});

// --- Token storage (localStorage, Module 1 learning-project choice) ---
// Module 1 notes this honestly: any script on the page could read
// localStorage. A production app would use an HttpOnly cookie for the
// refresh token instead. For this learning project, localStorage is fine
// and it is what AuthContext (Part 6) reads on page reload.
export function getAccessToken() {
  return localStorage.getItem("accessToken");
}
export function getRefreshToken() {
  return localStorage.getItem("refreshToken");
}
export function saveTokens(accessToken, refreshToken) {
  localStorage.setItem("accessToken", accessToken);
  localStorage.setItem("refreshToken", refreshToken);
}
export function clearTokens() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
}

// Attach the access token to every request (except auth calls, which have none yet)
api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Refresh-on-401-once: the heart of the token flow (Module 1, Part 4)
let isRefreshing = false; // only one refresh at a time, even if 3 calls fail together
let waitingRequests = []; // requests that failed with 401 while a refresh was running

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Not a 401, or this IS the refresh call itself, or we already retried once:
    // give up and pass the error to the page that made the call.
    if (
      error.response?.status !== 401 ||
      originalRequest.url?.includes("/auth/refresh") ||
      originalRequest._retry
    ) {
      return Promise.reject(error);
    }

    // If a refresh is already running, wait for it instead of starting another.
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        waitingRequests.push({ resolve, reject, originalRequest });
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const refreshToken = getRefreshToken();
      if (!refreshToken) throw new Error("No refresh token");

      // NOTE: use plain axios here, not `api` — otherwise this call would
      // go through this same interceptor and loop forever.
      const res = await axios.post(
        `${api.defaults.baseURL}/auth/refresh`,
        { refreshToken }
      );
      saveTokens(res.data.accessToken, res.data.refreshToken);

      // Retry the requests that were waiting, with the new token.
      waitingRequests.forEach(({ resolve, originalRequest: req }) => {
        req.headers.Authorization = `Bearer ${res.data.accessToken}`;
        resolve(api(req));
      });
      waitingRequests = [];

      originalRequest.headers.Authorization = `Bearer ${res.data.accessToken}`;
      return api(originalRequest);
    } catch (refreshError) {
      // Refresh failed (expired, revoked, or invalid): log out everywhere.
      waitingRequests.forEach(({ reject }) => reject(refreshError));
      waitingRequests = [];
      clearTokens();
      window.location.href = "/login";
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);

export default api;
```

**How to read the error the backend sends:** Module 1's `GlobalExceptionHandler` returns `{ status, message, fieldErrors, timestamp }`. A page reads it like this — every service below returns the raw error, and the page shows `error.response?.data?.message`:

```js
// Pattern used in every page in this guide:
try {
  const data = await searchQuotes(form);
  setQuotes(data);
} catch (err) {
  setError(err.response?.data?.message || "Something went wrong. Please try again.");
}
```

### File — `src/services/authService.js`

```js
import api, { saveTokens, clearTokens } from "./api";

// POST /api/auth/register — Module 1. Returns the new user, NO token.
// Role ADMIN is rejected by the backend; the form must not offer it.
export async function registerUser({ fullName, email, phone, password, role }) {
  const res = await api.post("/auth/register", {
    fullName, email, phone, password, role,
  });
  return res.data; // { id, fullName, email, phone, role }
}

// POST /api/auth/login — Module 1. Returns user + both tokens.
export async function loginUser({ email, password }) {
  const res = await api.post("/auth/login", { email, password });
  const data = res.data; // { userId, fullName, email, role, accessToken, refreshToken, tokenType }
  saveTokens(data.accessToken, data.refreshToken);
  localStorage.setItem(
    "user",
    JSON.stringify({
      userId: data.userId,
      fullName: data.fullName,
      email: data.email,
      role: data.role,
    })
  );
  return data;
}

// Logout is frontend-only in Module 1 (JWTs are stateless — the server
// cannot cancel a token early). Deleting the tokens IS the logout.
export function logoutUser() {
  clearTokens();
}

// GET /api/auth/me — the logged-in user's own profile (Module 1).
export async function fetchMyProfile() {
  const res = await api.get("/auth/me");
  return res.data;
}
```

### File — `src/services/quoteService.js`

```js
import api from "./api";

// POST /api/quotes/search — Module 3. Returns quotes cheapest first,
// each with the full fare breakdown (see QuoteCard, Part 7).
export async function searchQuotes(search) {
  const res = await api.post("/quotes/search", {
    pickup: search.pickup,
    destination: search.destination,
    distanceKm: Number(search.distanceKm),
    loadTons: Number(search.loadTons),
    goodsType: search.goodsType || null,
    bodyType: search.bodyType || null,
    categoryId: search.categoryId ? Number(search.categoryId) : null,
    tollCharges: search.tollCharges ? Number(search.tollCharges) : 0,
    loadingCharges: search.loadingCharges ? Number(search.loadingCharges) : 0,
  });
  return res.data; // QuoteResponse[]
}

// GET /api/admin/rates — Module 3. Admin only.
export async function fetchRates() {
  const res = await api.get("/admin/rates");
  return res.data;
}
```

### File — `src/services/truckService.js`

```js
import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 2 and fix the URL here only.
export async function fetchMyTrucks() {
  const res = await api.get("/trucks/my");
  return res.data;
}

// Slim registration (Module 2 correction): only these 4 fields. No documents.
export async function addTruck({ registrationNumber, categoryId, bodyType, payloadTons }) {
  const res = await api.post("/trucks", {
    registrationNumber,
    categoryId: Number(categoryId),
    bodyType,
    payloadTons: Number(payloadTons),
  });
  return res.data;
}

// Toggle AVAILABLE / UNAVAILABLE. A truck ON_TRIP cannot be toggled by hand —
// the backend moves it to ON_TRIP when a booking is accepted (Module 4).
export async function setTruckAvailability(truckId, availabilityStatus) {
  const res = await api.patch(`/trucks/${truckId}/availability`, { availabilityStatus });
  return res.data;
}

// Categories for the search form and the add-truck form (12/20t, 14/25t, 18/30t, 22/36t).
export async function fetchCategories() {
  const res = await api.get("/trucks/categories");
  return res.data;
}
```

### File — `src/services/bookingService.js`

```js
import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 4 and fix the URL here only.

// POST /api/bookings { quoteId } — instant booking. The price is read from
// the saved quote on the server; the frontend sends NO price.
export async function createBooking(quoteId) {
  const res = await api.post("/bookings", { quoteId });
  return res.data;
}

// GET /api/bookings/my — bookings for whoever is logged in (customer sees
// their bookings, owner sees bookings for their trucks).
export async function fetchMyBookings() {
  const res = await api.get("/bookings/my");
  return res.data;
}

export async function fetchBookingById(bookingId) {
  const res = await api.get(`/bookings/${bookingId}`);
  return res.data;
}

// Owner accept / reject (Module 4). Rejecting should free the truck again.
export async function acceptBooking(bookingId) {
  const res = await api.post(`/bookings/${bookingId}/accept`);
  return res.data;
}

export async function rejectBooking(bookingId, reason) {
  const res = await api.post(`/bookings/${bookingId}/reject`, { reason: reason || null });
  return res.data;
}

// Customer cancel — the backend enforces the cancel rules (Module 4);
// the frontend only shows the button when the status allows it.
export async function cancelBooking(bookingId) {
  const res = await api.post(`/bookings/${bookingId}/cancel`);
  return res.data;
}

// --- Owner call / chat (Module 4) ---
// Chat: in-app messages on the booking. Call: NOT an API — the Tracking page
// renders a <a href="tel:..."> link with the owner's phone number, which the
// backend returns on the booking. No call API exists, and none is needed.
export async function fetchMessages(bookingId) {
  const res = await api.get(`/bookings/${bookingId}/messages`);
  return res.data;
}

export async function sendMessage(bookingId, text) {
  const res = await api.post(`/bookings/${bookingId}/messages`, { text });
  return res.data;
}
```

### File — `src/services/driverService.js`

```js
import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 5 and fix the URL here only.
export async function fetchMyDrivers() {
  const res = await api.get("/drivers/my");
  return res.data;
}

export async function addDriver({ fullName, phone, licenceNumber }) {
  const res = await api.post("/drivers", { fullName, phone, licenceNumber });
  return res.data;
}

export async function updateDriver(driverId, { fullName, phone, licenceNumber }) {
  const res = await api.put(`/drivers/${driverId}`, { fullName, phone, licenceNumber });
  return res.data;
}

export async function deactivateDriver(driverId) {
  const res = await api.patch(`/drivers/${driverId}/deactivate`);
  return res.data;
}
```

### File — `src/services/tripService.js`

```js
import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 5 and fix the URL here only.
export async function fetchMyTrips() {
  const res = await api.get("/trips/my");
  return res.data;
}

export async function assignDriver(tripId, driverId) {
  const res = await api.post(`/trips/${tripId}/assign`, { driverId });
  return res.data;
}

// status: ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, COMPLETED (Module 5).
// deliveryProofNote / deliveryProofUrl are sent only on DELIVERED.
export async function updateTripStatus(tripId, { status, deliveryProofNote, deliveryProofUrl }) {
  const res = await api.post(`/trips/${tripId}/status`, {
    status,
    deliveryProofNote: deliveryProofNote || null,
    deliveryProofUrl: deliveryProofUrl || null,
  });
  return res.data;
}
```

### File — `src/services/paymentService.js`

```js
import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 6 and fix the URL here only.
// COD is the default in Module 6; there is no online-payment screen here.
export async function fetchPaymentForBooking(bookingId) {
  const res = await api.get(`/payments/booking/${bookingId}`);
  return res.data; // { bookingId, method: "COD", status, amount, ... }
}

// Owner settlement view: what the platform owes the owner, and what is paid.
export async function fetchMySettlements() {
  const res = await api.get("/payments/settlements/my");
  return res.data;
}
```

### File — `src/services/adminService.js`

```js
import api from "./api";

// ⚠️ ASSUMED endpoints (except rates, which are Module 3 — confirmed).
// Check against Modules 2/4/6 and fix the URL here only.
export async function fetchAdminStats() {
  const res = await api.get("/admin/stats");
  return res.data;
}

export async function fetchAllUsers() {
  const res = await api.get("/admin/users");
  return res.data;
}

export async function deactivateUser(userId) {
  const res = await api.patch(`/admin/users/${userId}/deactivate`);
  return res.data;
}

export async function fetchAdminCategories() {
  const res = await api.get("/admin/categories");
  return res.data;
}

export async function addCategory({ name, tyreCount, targetPayloadTons }) {
  const res = await api.post("/admin/categories", {
    name,
    tyreCount: Number(tyreCount),
    targetPayloadTons: Number(targetPayloadTons),
  });
  return res.data;
}

// Rate edit — Module 3, confirmed. Changing a rate affects NEW quotes only;
// old quotes keep the rate they were calculated with.
export async function updateRate(rateId, { ratePerTonKm, active }) {
  const res = await api.put(`/admin/rates/${rateId}`, { ratePerTonKm, active });
  return res.data;
}

export async function fetchAllBookings() {
  const res = await api.get("/admin/bookings");
  return res.data;
}

export async function fetchAllSettlements() {
  const res = await api.get("/admin/settlements");
  return res.data;
}

export async function markSettlementPaid(settlementId) {
  const res = await api.post(`/admin/settlements/${settlementId}/mark-paid`);
  return res.data;
}
```

---

## Part 6 — Login state: AuthContext and ProtectedRoute

### File — `src/context/AuthContext.jsx`

**Simple meaning:** The whole app needs to know "who is logged in, and what is their role?" — the Navbar, the redirects, and every page. `AuthContext` holds that answer in one place, so no page reads `localStorage` directly.

```jsx
import { createContext, useContext, useState } from "react";
import { loginUser, registerUser, logoutUser } from "../services/authService";

const AuthContext = createContext(null);

// The role lives in the JWT token itself. A JWT is three Base64 parts
// joined by dots; the middle part (payload) is JSON. We decode it WITHOUT
// verifying the signature — verification is the backend's job, and the
// frontend only uses the role to decide which menu to show. The backend
// re-checks the role on every request, so a user who edits their token
// in DevTools gains nothing: the backend will still say 403.
function roleFromToken(token) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.role || payload.roles?.[0] || null;
  } catch {
    return null;
  }
}

// If the backend does not put the role in the token, AuthContext falls back
// to the role saved at login time (loginUser saves the user object).
function savedUser() {
  try {
    return JSON.parse(localStorage.getItem("user"));
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => savedUser());
  const [token, setToken] = useState(() => localStorage.getItem("accessToken"));

  async function login(email, password) {
    const data = await loginUser({ email, password });
    const loggedIn = {
      userId: data.userId,
      fullName: data.fullName,
      email: data.email,
      role: data.role || roleFromToken(data.accessToken),
    };
    setUser(loggedIn);
    setToken(data.accessToken);
    return loggedIn; // the Login page uses the role to choose where to go
  }

  async function register(form) {
    // Register returns no token (Module 1) — the user logs in next.
    await registerUser(form);
  }

  function logout() {
    logoutUser();
    setUser(null);
    setToken(null);
  }

  const value = {
    user,
    token,
    isLoggedIn: !!token && !!user,
    role: user?.role || null,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
```

> **If the role is not in your token:** check what Module 1's `JwtService` puts in the token. The code above handles both cases — role in the token, or role from the login response saved in `localStorage`. If neither works, the Navbar will show the logged-out menu even after login; that is the symptom to look for, and the fix is in `JwtService.buildToken` (add `.claim("role", user role)`), not in this file.

### File — `src/routes/ProtectedRoute.jsx`

**Simple meaning:** A gate in front of a page. No token → go to `/login`. Wrong role → go to your own home page. Right role → show the page.

```jsx
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Usage in App.jsx:
//   <Route element={<ProtectedRoute allowedRoles={["CUSTOMER"]} />}>
//     <Route path="/my-bookings" element={<MyBookings />} />
//   </Route>
//
// allowedRoles omitted (or empty) means "any logged-in user".
export default function ProtectedRoute({ allowedRoles }) {
  const { isLoggedIn, role, user } = useAuth();
  const location = useLocation();

  // 1. Not logged in at all -> login page. Remember where they were going,
  //    so the Login page can send them back after a successful login.
  if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  // 2. Logged in, but this page is not for their role -> their own home page.
  if (allowedRoles && allowedRoles.length > 0 && !allowedRoles.includes(role)) {
    const homeByRole = {
      CUSTOMER: "/",
      OWNER: "/owner",
      TRANSPORTER: "/owner",
      DRIVER: "/driver",
      ADMIN: "/admin",
    };
    return <Navigate to={homeByRole[role] || "/"} replace />;
  }

  // 3. Allowed -> render the page inside this route.
  return <Outlet />;
}
```

---

## Part 7 — Shared components (build these before any page)

### File — `src/components/LoadingSpinner.jsx`

```jsx
export default function LoadingSpinner({ text = "Loading..." }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-gray-600">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
      <p className="mt-3 text-sm">{text}</p>
    </div>
  );
}
```

### File — `src/components/ErrorBox.jsx`

**Simple meaning:** Every error in the app looks the same — a red box with the backend's message in plain words. Pages pass the message string; they never build their own red box.

```jsx
export default function ErrorBox({ message, onRetry }) {
  if (!message) return null;
  return (
    <div className="mb-4 rounded border border-red-300 bg-red-50 p-4 text-red-800">
      <p className="font-medium">Something went wrong</p>
      <p className="text-sm">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-2 text-sm font-medium underline">
          Try again
        </button>
      )}
    </div>
  );
}
```

### File — `src/components/EmptyState.jsx`

```jsx
export default function EmptyState({ title, message, actionLabel, onAction }) {
  return (
    <div className="rounded border border-dashed border-gray-300 bg-gray-50 p-8 text-center">
      <p className="text-lg font-medium text-gray-800">{title}</p>
      <p className="mt-1 text-sm text-gray-600">{message}</p>
      {actionLabel && (
        <button
          onClick={onAction}
          className="mt-4 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}
```

### File — `src/components/StatusBadge.jsx`

**Simple meaning:** One badge, one colour per status, used on every booking and trip list. The customer learns the colours once and reads them everywhere.

```jsx
// Colours: green = good / moving forward, amber = waiting, red = stopped,
// grey = finished or cancelled, purple = needs attention (payment / dispute).
const COLOURS = {
  PENDING: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-blue-100 text-blue-800",
  DRIVER_ASSIGNED: "bg-blue-100 text-blue-800",
  ASSIGNED: "bg-blue-100 text-blue-800",
  PICKED_UP: "bg-indigo-100 text-indigo-800",
  IN_TRANSIT: "bg-indigo-100 text-indigo-800",
  DELIVERED: "bg-green-100 text-green-800",
  COMPLETED: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-gray-200 text-gray-700",
  PAYMENT_PENDING: "bg-purple-100 text-purple-800",
  PAYMENT_FAILED: "bg-red-100 text-red-800",
  DISPUTED: "bg-red-100 text-red-800",
  AVAILABLE: "bg-green-100 text-green-800",
  UNAVAILABLE: "bg-gray-200 text-gray-700",
  ON_TRIP: "bg-indigo-100 text-indigo-800",
  ACTIVE: "bg-green-100 text-green-800",
  INACTIVE: "bg-gray-200 text-gray-700",
};

const LABELS = {
  DRIVER_ASSIGNED: "Driver assigned",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  PAYMENT_PENDING: "Payment pending",
  PAYMENT_FAILED: "Payment failed",
  ON_TRIP: "On trip",
};

export default function StatusBadge({ status }) {
  if (!status) return null;
  const colour = COLOURS[status] || "bg-gray-100 text-gray-700";
  const label = LABELS[status] || status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  return (
    <span className={`inline-block rounded-full px-3 py-1 text-xs font-medium ${colour}`}>
      {label}
    </span>
  );
}
```

### File — `src/components/TruckCard.jsx`

```jsx
import StatusBadge from "./StatusBadge";

export default function TruckCard({ truck }) {
  return (
    <div className="rounded border bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-lg font-semibold">{truck.registrationNumber}</p>
          <p className="text-sm text-gray-600">
            {truck.categoryName || truck.category?.name} · {truck.tyreCount || truck.category?.tyreCount} tyres
          </p>
        </div>
        <StatusBadge status={truck.availabilityStatus} />
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
        <p><span className="text-gray-500">Body: </span>{truck.bodyType}</p>
        <p><span className="text-gray-500">Can carry: </span>{truck.payloadTons} tons</p>
      </div>
    </div>
  );
}
```

### File — `src/components/QuoteCard.jsx` (the most important component in this module)

**Simple meaning:** A quote is never just one number. This card shows every line of the fare breakdown from Module 3, plus the minimum-charge note and the expiry time — because a customer who understands the price trusts it.

```jsx
import { formatRupees, formatDateTime } from "../utils/format";
import { isQuoteExpired } from "../utils/quote";
import TruckCard from "./TruckCard";

export default function QuoteCard({ quote, onBook }) {
  const expired = isQuoteExpired(quote);

  return (
    <div className="rounded border bg-white p-5 shadow-sm">
      <TruckCard truck={quote} />

      {/* --- Fare breakdown: every rupee explained (Module 3) --- */}
      <div className="mt-4 space-y-1 border-t pt-3 text-sm">
        <div className="flex justify-between">
          <span className="text-gray-600">
            Base fare ({quote.distanceKm} km × {formatRupees(quote.ratePerTonKm)}/ton/km × {quote.chargeableTons} tons)
          </span>
          <span>{formatRupees(quote.baseFare)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-600">Toll charges</span>
          <span>{formatRupees(quote.tollCharges)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-600">Loading charges</span>
          <span>{formatRupees(quote.loadingCharges)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-600">Platform fee</span>
          <span>{formatRupees(quote.platformFee)}</span>
        </div>
        <div className="flex justify-between border-t pt-2 text-base font-semibold">
          <span>Total fare</span>
          <span>{formatRupees(quote.totalFare)}</span>
        </div>
      </div>

      {/* --- Minimum-charge note: shown ONLY when it applied --- */}
      {quote.minimumChargeApplied && (
        <p className="mt-3 rounded bg-amber-50 p-3 text-sm text-amber-800">
          Minimum charge applied: your load is smaller than half this truck's
          capacity, so you are charged for {quote.chargeableTons} tons (half the
          truck) instead of your actual load. A full truck load never pays this.
        </p>
      )}

      <p className="mt-3 text-xs text-gray-500">
        Quote valid until {formatDateTime(quote.validUntil)}
      </p>

      <button
        onClick={() => onBook(quote)}
        disabled={expired}
        className="mt-4 w-full rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
      >
        {expired ? "Quote expired — search again" : `Book now — ${formatRupees(quote.totalFare)}`}
      </button>
    </div>
  );
}
```

### File — `src/components/Navbar.jsx`

**Simple meaning:** The top bar changes with the role — a customer sees Search and My Bookings, an owner sees Trucks and Incoming Bookings, an admin sees the admin pages. One component, four menus.

```jsx
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const MENUS = {
  CUSTOMER: [
    { to: "/", label: "Search Trucks" },
    { to: "/my-bookings", label: "My Bookings" },
    { to: "/profile", label: "Profile" },
  ],
  OWNER: [
    { to: "/owner", label: "Dashboard" },
    { to: "/owner/trucks", label: "My Trucks" },
    { to: "/owner/bookings", label: "Incoming Bookings" },
    { to: "/owner/drivers", label: "My Drivers" },
    { to: "/owner/trips", label: "My Trips" },
    { to: "/owner/earnings", label: "Earnings" },
  ],
  TRANSPORTER: [
    { to: "/owner", label: "Dashboard" },
    { to: "/owner/trucks", label: "My Trucks" },
    { to: "/owner/bookings", label: "Incoming Bookings" },
    { to: "/owner/drivers", label: "My Drivers" },
    { to: "/owner/trips", label: "My Trips" },
    { to: "/owner/earnings", label: "Earnings" },
  ],
  DRIVER: [
    { to: "/driver", label: "Dashboard" },
    { to: "/driver/trips", label: "My Trips" },
  ],
  ADMIN: [
    { to: "/admin", label: "Dashboard" },
    { to: "/admin/users", label: "Users" },
    { to: "/admin/categories", label: "Categories" },
    { to: "/admin/rates", label: "Rates" },
    { to: "/admin/bookings", label: "Bookings" },
    { to: "/admin/settlements", label: "Settlements" },
    { to: "/admin/disputes", label: "Disputes" },
  ],
};

export default function Navbar() {
  const { user, isLoggedIn, logout } = useAuth();
  const navigate = useNavigate();
  const links = user?.role ? MENUS[user.role] || [] : [];

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <header className="bg-blue-700 text-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-3">
        <Link to="/" className="text-xl font-bold">Truck Booking</Link>
        <nav className="flex flex-wrap items-center gap-4 text-sm">
          {isLoggedIn ? (
            <>
              {links.map((l) => (
                <Link key={l.to} to={l.to} className="hover:underline">{l.label}</Link>
              ))}
              <span className="text-blue-200">
                {user.fullName} ({user.role})
              </span>
              <button onClick={handleLogout} className="rounded bg-blue-800 px-3 py-1 hover:bg-blue-900">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="hover:underline">Login</Link>
              <Link to="/register" className="rounded bg-white px-3 py-1 font-medium text-blue-700">
                Register
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
```

### File — `src/components/Footer.jsx`

```jsx
export default function Footer() {
  return (
    <footer className="mt-12 border-t bg-gray-50 py-6 text-center text-sm text-gray-500">
      <p>Truck Booking — book a truck across India. Instant booking, upfront fare, owner call &amp; chat.</p>
      <p className="mt-1">Prices are quotes from the rate board. COD is the default payment method.</p>
    </footer>
  );
}
```

---

## Part 8 — App.jsx and main.jsx: all the routes in one place

### File — `src/App.jsx`

```jsx
import { Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import ProtectedRoute from "./routes/ProtectedRoute";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Home from "./pages/customer/Home";
import SearchResults from "./pages/customer/SearchResults";
import BookingConfirm from "./pages/customer/BookingConfirm";
import MyBookings from "./pages/customer/MyBookings";
import Tracking from "./pages/customer/Tracking";
import Profile from "./pages/customer/Profile";
import OwnerDashboard from "./pages/owner/OwnerDashboard";
import MyTrucks from "./pages/owner/MyTrucks";
import IncomingBookings from "./pages/owner/IncomingBookings";
import MyDrivers from "./pages/owner/MyDrivers";
import MyTrips from "./pages/owner/MyTrips";
import Earnings from "./pages/owner/Earnings";
import DriverDashboard from "./pages/driver/DriverDashboard";
import DriverTrips from "./pages/driver/DriverTrips";
import AdminDashboard from "./pages/admin/AdminDashboard";
import UserManagement from "./pages/admin/UserManagement";
import CategoryManagement from "./pages/admin/CategoryManagement";
import RateManagement from "./pages/admin/RateManagement";
import BookingManagement from "./pages/admin/BookingManagement";
import SettlementManagement from "./pages/admin/SettlementManagement";
import Disputes from "./pages/admin/Disputes";

// After login, send each role to its own home page.
function HomeByRole() {
  const { role } = useAuth();
  if (role === "OWNER" || role === "TRANSPORTER") return <Navigate to="/owner" replace />;
  if (role === "ADMIN") return <Navigate to="/admin" replace />;
  if (role === "DRIVER") return <Navigate to="/driver" replace />;
  return <Home />; // CUSTOMER (and fallback) sees the search page
}

export default function App() {
  return (
    <AuthProvider>
      <div className="flex min-h-screen flex-col">
        <Navbar />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/" element={<HomeByRole />} />

            {/* Customer */}
            <Route element={<ProtectedRoute allowedRoles={["CUSTOMER"]} />}>
              <Route path="/search-results" element={<SearchResults />} />
              <Route path="/book/:quoteId" element={<BookingConfirm />} />
              <Route path="/my-bookings" element={<MyBookings />} />
              <Route path="/track/:bookingId" element={<Tracking />} />
              <Route path="/profile" element={<Profile />} />
            </Route>

            {/* Owner + Transporter */}
            <Route element={<ProtectedRoute allowedRoles={["OWNER", "TRANSPORTER"]} />}>
              <Route path="/owner" element={<OwnerDashboard />} />
              <Route path="/owner/trucks" element={<MyTrucks />} />
              <Route path="/owner/bookings" element={<IncomingBookings />} />
              <Route path="/owner/drivers" element={<MyDrivers />} />
              <Route path="/owner/trips" element={<MyTrips />} />
              <Route path="/owner/earnings" element={<Earnings />} />
            </Route>

            {/* Driver pages: OWNER can also open them in this MVP (Part 9).
                DRIVER role is listed so driver login works the day Module 5 adds it. */}
            <Route element={<ProtectedRoute allowedRoles={["DRIVER", "OWNER"]} />}>
              <Route path="/driver" element={<DriverDashboard />} />
              <Route path="/driver/trips" element={<DriverTrips />} />
            </Route>

            {/* Admin */}
            <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/admin/users" element={<UserManagement />} />
              <Route path="/admin/categories" element={<CategoryManagement />} />
              <Route path="/admin/rates" element={<RateManagement />} />
              <Route path="/admin/bookings" element={<BookingManagement />} />
              <Route path="/admin/settlements" element={<SettlementManagement />} />
              <Route path="/admin/disputes" element={<Disputes />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <Footer />
      </div>
    </AuthProvider>
  );
}
```

### File — `src/main.jsx` (replace the Vite default)

```jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
```


---

## Part 9 — Customer pages (full code)

### Auth pages first — Login and Register

**File — `src/pages/Login.jsx`**

```jsx
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import ErrorBox from "../components/ErrorBox";

const HOME_BY_ROLE = {
  CUSTOMER: "/",
  OWNER: "/owner",
  TRANSPORTER: "/owner",
  DRIVER: "/driver",
  ADMIN: "/admin",
};

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!form.email.includes("@")) return setError("Enter a valid email address.");
    if (!form.password) return setError("Enter your password.");
    setLoading(true);
    try {
      const user = await login(form.email.trim().toLowerCase(), form.password);
      // Go back where they were headed, or to their role's home page.
      navigate(location.state?.from || HOME_BY_ROLE[user.role] || "/", { replace: true });
    } catch (err) {
      // Module 1 returns the same message for a wrong email and a wrong
      // password, on purpose. Show it exactly as the backend sent it.
      setError(err.response?.data?.message || "Invalid email or password.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-md rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">Login</h1>
      <p className="mt-1 text-sm text-gray-600">Welcome back to Truck Booking.</p>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <ErrorBox message={error} />
        <div>
          <label className="text-sm font-medium">Email</label>
          <input type="email" value={form.email} onChange={(e) => update("email", e.target.value)}
            className="mt-1 w-full rounded border px-3 py-2" placeholder="you@example.com" />
        </div>
        <div>
          <label className="text-sm font-medium">Password</label>
          <input type="password" value={form.password} onChange={(e) => update("password", e.target.value)}
            className="mt-1 w-full rounded border px-3 py-2" placeholder="Your password" />
        </div>
        <button disabled={loading} className="w-full rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700 disabled:bg-gray-300">
          {loading ? "Logging in..." : "Login"}
        </button>
      </form>
      <p className="mt-4 text-sm text-gray-600">
        No account? <Link to="/register" className="font-medium text-blue-600 hover:underline">Register</Link>
      </p>
    </div>
  );
}
```

**File — `src/pages/Register.jsx`**

```jsx
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import ErrorBox from "../components/ErrorBox";

// ADMIN is deliberately NOT in this list (Module 1 rejects it at the backend).
const ROLES = ["CUSTOMER", "OWNER", "DRIVER", "TRANSPORTER"];

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: "", email: "", phone: "", password: "", role: "CUSTOMER" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: "" }));
  }

  function validate() {
    const e = {};
    if (form.fullName.trim().length < 2) e.fullName = "Full name must be at least 2 characters.";
    if (!form.email.includes("@")) e.email = "Enter a valid email address.";
    if (!/^[6-9][0-9]{9}$/.test(form.phone.trim())) e.phone = "Phone must be a 10-digit Indian mobile number starting with 6–9.";
    if (form.password.length < 8) e.password = "Password must be at least 8 characters.";
    if (!ROLES.includes(form.role)) e.role = "Choose a role.";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!validate()) return;
    setLoading(true);
    try {
      await register({ ...form, email: form.email.trim().toLowerCase(), phone: form.phone.trim() });
      // Register returns no token (Module 1) — send them to log in.
      navigate("/login", { state: { registered: true } });
    } catch (err) {
      // Backend field errors arrive as { fieldErrors: { email: "..." } }.
      const data = err.response?.data;
      if (data?.fieldErrors) setErrors(data.fieldErrors);
      else setError(data?.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  const field = "mt-1 w-full rounded border px-3 py-2";
  const errText = "mt-1 text-sm text-red-600";

  return (
    <div className="mx-auto max-w-md rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">Create your account</h1>
      <p className="mt-1 text-sm text-gray-600">Book trucks, or list your own — choose your role below.</p>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <ErrorBox message={error} />
        <div>
          <label className="text-sm font-medium">Full name</label>
          <input value={form.fullName} onChange={(e) => update("fullName", e.target.value)} className={field} />
          {errors.fullName && <p className={errText}>{errors.fullName}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">Email</label>
          <input type="email" value={form.email} onChange={(e) => update("email", e.target.value)} className={field} />
          {errors.email && <p className={errText}>{errors.email}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">Phone</label>
          <input value={form.phone} onChange={(e) => update("phone", e.target.value)} className={field} placeholder="10-digit mobile number" />
          {errors.phone && <p className={errText}>{errors.phone}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">Password</label>
          <input type="password" value={form.password} onChange={(e) => update("password", e.target.value)} className={field} />
          {errors.password && <p className={errText}>{errors.password}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">I am a...</label>
          <select value={form.role} onChange={(e) => update("role", e.target.value)} className={field}>
            <option value="CUSTOMER">Customer — I need goods moved</option>
            <option value="OWNER">Truck Owner — I own trucks</option>
            <option value="DRIVER">Driver — I drive trucks</option>
            <option value="TRANSPORTER">Transporter — I run a transport company</option>
          </select>
          {errors.role && <p className={errText}>{errors.role}</p>}
        </div>
        <button disabled={loading} className="w-full rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700 disabled:bg-gray-300">
          {loading ? "Creating account..." : "Register"}
        </button>
      </form>
      <p className="mt-4 text-sm text-gray-600">
        Already have an account? <Link to="/login" className="font-medium text-blue-600 hover:underline">Login</Link>
      </p>
    </div>
  );
}
```

### File — `src/pages/customer/Home.jsx` (search form)

**Simple meaning:** The front door of the whole product. One form, seven fields, and a Search button. Nothing is booked here — this page only asks the question; the answers arrive on the next page.

```jsx
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchCategories } from "../../services/truckService";
import ErrorBox from "../../components/ErrorBox";

const BODY_TYPES = ["", "OPEN", "CLOSED", "REFRIGERATED", "TANKER", "FLATBED"];
// ⚠️ If your Module 2 BodyType enum uses different names, change this list
//    to match it exactly — the backend rejects unknown values with a 400.

export default function Home() {
  const navigate = useNavigate();
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState({
    pickup: "", destination: "", distanceKm: "", loadTons: "",
    goodsType: "", bodyType: "", categoryId: "", tollCharges: "", loadingCharges: "",
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");

  useEffect(() => {
    // Categories fill the dropdown. If this call fails, the form still works —
    // the customer can simply leave "Any category" selected.
    fetchCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: "" }));
  }

  function validate() {
    const e = {};
    if (!form.pickup.trim()) e.pickup = "Enter the pickup place.";
    if (!form.destination.trim()) e.destination = "Enter the destination.";
    if (!form.distanceKm || Number(form.distanceKm) < 1 || Number(form.distanceKm) > 5000)
      e.distanceKm = "Distance must be between 1 and 5000 km.";
    if (!form.loadTons || Number(form.loadTons) < 0.1 || Number(form.loadTons) > 40)
      e.loadTons = "Load must be between 0.1 and 40 tons.";
    if (form.tollCharges && Number(form.tollCharges) < 0) e.tollCharges = "Toll charges cannot be negative.";
    if (form.loadingCharges && Number(form.loadingCharges) < 0) e.loadingCharges = "Loading charges cannot be negative.";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!validate()) return;
    // The search inputs travel in router state — SearchResults reads them
    // and calls the API. No price is calculated here, ever.
    navigate("/search-results", { state: { search: form } });
  }

  const input = "mt-1 w-full rounded border px-3 py-2";
  const label = "text-sm font-medium";
  const errText = "mt-1 text-sm text-red-600";

  return (
    <div>
      <h1 className="text-3xl font-bold">Book a truck across India</h1>
      <p className="mt-1 text-gray-600">
        Tell us your load and route. We show the cheapest trucks first, with every rupee explained — no hidden charges.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 rounded border bg-white p-6 shadow-sm">
        <ErrorBox message={error} />
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className={label}>Pickup place *</label>
            <input value={form.pickup} onChange={(e) => update("pickup", e.target.value)} className={input} placeholder="e.g. Bengaluru" />
            {errors.pickup && <p className={errText}>{errors.pickup}</p>}
          </div>
          <div>
            <label className={label}>Destination *</label>
            <input value={form.destination} onChange={(e) => update("destination", e.target.value)} className={input} placeholder="e.g. Hyderabad" />
            {errors.destination && <p className={errText}>{errors.destination}</p>}
          </div>
          <div>
            <label className={label}>Distance (km) *</label>
            <input type="number" min="1" max="5000" value={form.distanceKm} onChange={(e) => update("distanceKm", e.target.value)} className={input} placeholder="e.g. 570 — check Google Maps" />
            {errors.distanceKm && <p className={errText}>{errors.distanceKm}</p>}
            <p className="mt-1 text-xs text-gray-500">Tip: search the route on Google Maps and type the km here.</p>
          </div>
          <div>
            <label className={label}>Load (tons) *</label>
            <input type="number" step="0.1" min="0.1" max="40" value={form.loadTons} onChange={(e) => update("loadTons", e.target.value)} className={input} placeholder="e.g. 20" />
            {errors.loadTons && <p className={errText}>{errors.loadTons}</p>}
          </div>
          <div>
            <label className={label}>Goods type</label>
            <input value={form.goodsType} onChange={(e) => update("goodsType", e.target.value)} className={input} placeholder="e.g. Rice, Steel, Medicine" />
          </div>
          <div>
            <label className={label}>Body type</label>
            <select value={form.bodyType} onChange={(e) => update("bodyType", e.target.value)} className={input}>
              <option value="">Any body type</option>
              {BODY_TYPES.filter(Boolean).map((b) => (<option key={b} value={b}>{b}</option>))}
            </select>
          </div>
          <div>
            <label className={label}>Truck category</label>
            <select value={form.categoryId} onChange={(e) => update("categoryId", e.target.value)} className={input}>
              <option value="">Any category</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name} — {c.targetPayloadTons} tons</option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={label}>Toll (₹, optional)</label>
              <input type="number" min="0" value={form.tollCharges} onChange={(e) => update("tollCharges", e.target.value)} className={input} placeholder="0" />
              {errors.tollCharges && <p className={errText}>{errors.tollCharges}</p>}
            </div>
            <div>
              <label className={label}>Loading (₹, optional)</label>
              <input type="number" min="0" value={form.loadingCharges} onChange={(e) => update("loadingCharges", e.target.value)} className={input} placeholder="0" />
              {errors.loadingCharges && <p className={errText}>{errors.loadingCharges}</p>}
            </div>
          </div>
        </div>
        <button className="mt-6 w-full rounded bg-blue-600 py-3 font-medium text-white hover:bg-blue-700 md:w-auto md:px-8">
          Search trucks →
        </button>
      </form>

      <div className="mt-8 grid gap-4 md:grid-cols-3">
        <div className="rounded border bg-white p-4"><p className="font-semibold">Upfront fare</p><p className="text-sm text-gray-600">Every quote shows the full breakdown — base fare, toll, loading, platform fee.</p></div>
        <div className="rounded border bg-white p-4"><p className="font-semibold">Instant booking</p><p className="text-sm text-gray-600">Book straight from a quote. No waiting for a price call-back.</p></div>
        <div className="rounded border bg-white p-4"><p className="font-semibold">Talk to the owner</p><p className="text-sm text-gray-600">Call or chat with the truck owner after booking, from the tracking page.</p></div>
      </div>
    </div>
  );
}
```

### File — `src/pages/customer/SearchResults.jsx`

```jsx
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { searchQuotes } from "../../services/quoteService";
import QuoteCard from "../../components/QuoteCard";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

export default function SearchResults() {
  const location = useLocation();
  const navigate = useNavigate();
  const search = location.state?.search;
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!search) {
      // Opened directly without a search — send them to the form.
      navigate("/", { replace: true });
      return;
    }
    setLoading(true);
    searchQuotes(search)
      .then((data) => setQuotes(data || []))
      .catch((err) => setError(err.response?.data?.message || "Search failed. Please try again."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // No search in state (e.g. page refreshed): the router state is gone.
  // The user searches again — quotes are not stored in localStorage, because
  // a quote is server data with a 24-hour expiry, not a page setting.
  if (!search) return null;

  if (loading) return <LoadingSpinner text="Finding trucks that can carry your load..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Available trucks</h1>
      <p className="mt-1 text-gray-600">
        {search.pickup} → {search.destination} · {search.distanceKm} km · {search.loadTons} tons
        {search.goodsType ? ` · ${search.goodsType}` : ""}
      </p>
      <p className="mt-1 text-sm text-gray-500">Cheapest first. Prices include toll, loading and the ₹0 platform fee.</p>

      <ErrorBox message={error} onRetry={() => window.location.reload()} />

      {!error && quotes.length === 0 && (
        <div className="mt-6">
          <EmptyState
            title={`No truck can carry ${search.loadTons} tons`}
            message="No available truck matches this load right now. Try a smaller load, a different body type, or another date."
            actionLabel="Change search"
            onAction={() => navigate("/")}
          />
        </div>
      )}

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {quotes.map((q) => (
          <QuoteCard
            key={q.quoteId}
            quote={q}
            onBook={(quote) => navigate(`/book/${quote.quoteId}`, { state: { quote } })}
          />
        ))}
      </div>
    </div>
  );
}
```

### File — `src/pages/customer/BookingConfirm.jsx`

**Simple meaning:** The last chance to check before money is committed. This page shows the quote exactly as the backend calculated it — it never recalculates, never edits the price, and it refuses to book an expired quote.

```jsx
import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { createBooking } from "../../services/bookingService";
import { isQuoteExpired } from "../../utils/quote";
import { formatRupees, formatDateTime } from "../../utils/format";
import ErrorBox from "../../components/ErrorBox";

export default function BookingConfirm() {
  const { quoteId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const quote = location.state?.quote;
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Refreshed or opened directly: the quote is not in router state.
  // Do NOT rebuild the quote from URL parameters — that would let anyone
  // type any price into the address bar. Send them back to search.
  if (!quote) {
    return (
      <div className="rounded border bg-white p-6">
        <h1 className="text-xl font-bold">Quote not found</h1>
        <p className="mt-2 text-gray-600">Please search again — a booking always starts from a fresh quote.</p>
        <button onClick={() => navigate("/")} className="mt-4 rounded bg-blue-600 px-4 py-2 text-white">
          Back to search
        </button>
      </div>
    );
  }

  const expired = isQuoteExpired(quote);

  async function handleBook() {
    if (expired) return;
    setError("");
    setLoading(true);
    try {
      // ONLY the quoteId is sent. The backend reads the price from the saved
      // quote row (Module 4) — the frontend never sends a fare.
      const booking = await createBooking(quote.quoteId || quoteId);
      navigate(`/track/${booking.id}`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || "Booking failed. The quote may have expired or the truck may already be booked.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">Confirm your booking</h1>
      <p className="mt-1 text-sm text-gray-600">Check the details. The price below is the quote price — it will not change at this step.</p>
      <ErrorBox message={error} />

      <div className="mt-6 space-y-2 text-sm">
        <div className="flex justify-between"><span className="text-gray-600">Truck</span><span className="font-medium">{quote.registrationNumber} · {quote.categoryName}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Route</span><span className="font-medium">{quote.pickup || "—"} → {quote.destination || "—"}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Load</span><span>{quote.loadTons} tons ({quote.chargeableTons} tons charged)</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Base fare</span><span>{formatRupees(quote.baseFare)}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Toll + loading</span><span>{formatRupees((quote.tollCharges || 0) + (quote.loadingCharges || 0))}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Platform fee</span><span>{formatRupees(quote.platformFee)}</span></div>
        <div className="flex justify-between border-t pt-2 text-base font-semibold"><span>Total fare</span><span>{formatRupees(quote.totalFare)}</span></div>
        <p className="text-xs text-gray-500">Payment: Cash on Delivery (COD) by default. Valid until {formatDateTime(quote.validUntil)}.</p>
        {quote.minimumChargeApplied && (
          <p className="rounded bg-amber-50 p-3 text-sm text-amber-800">
            Minimum charge applied — you are charged for {quote.chargeableTons} tons (half the truck) instead of your actual load.
          </p>
        )}
      </div>

      <button
        onClick={handleBook}
        disabled={loading || expired}
        className="mt-6 w-full rounded bg-blue-600 py-3 font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
      >
        {expired ? "This quote has expired — search again" : loading ? "Booking..." : `Confirm booking — ${formatRupees(quote.totalFare)}`}
      </button>
      <button onClick={() => navigate("/search-results", { state: { search: null } })} className="mt-2 w-full rounded border py-2 text-sm">
        Back to results
      </button>
    </div>
  );
}
```

### File — `src/pages/customer/MyBookings.jsx`

```jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyBookings, cancelBooking } from "../../services/bookingService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

// Only these statuses can be cancelled by the customer in the UI.
// The backend enforces the same rule (Module 4) — this only hides the button.
const CANCELLABLE = ["PENDING", "CONFIRMED"];

export default function MyBookings() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchMyBookings()
      .then((data) => setBookings(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your bookings."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCancel(id) {
    if (!window.confirm("Cancel this booking? The truck will become available again.")) return;
    try {
      await cancelBooking(id);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not cancel this booking.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your bookings..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Bookings</h1>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="No bookings yet" message="Search for a truck and book your first trip." actionLabel="Search trucks" onAction={() => (window.location.href = "/")} />
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => (
            <div key={b.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Booking #{b.id} · {b.truck?.registrationNumber || b.registrationNumber || ""}</p>
                  <p className="text-sm text-gray-600">{b.pickup} → {b.destination}</p>
                  <p className="text-sm text-gray-500">{formatDateTime(b.createdAt)} · {formatRupees(b.totalFare ?? b.totalAmount)}</p>
                </div>
                <StatusBadge status={b.status} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link to={`/track/${b.id}`} className="rounded bg-blue-600 px-3 py-1 text-sm text-white">Track &amp; chat</Link>
                {CANCELLABLE.includes(b.status) && (
                  <button onClick={() => handleCancel(b.id)} className="rounded border border-red-300 px-3 py-1 text-sm text-red-700">Cancel booking</button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

### File — `src/pages/customer/Tracking.jsx`

**Simple meaning:** One page, three jobs: show where the trip is (timeline), let the customer talk to the owner (chat), and let them phone the owner (a `tel:` link — a real phone call, not an API).

```jsx
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { fetchBookingById, fetchMessages, sendMessage } from "../../services/bookingService";
import { fetchPaymentForBooking } from "../../services/paymentService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import { formatRupees, formatDateTime } from "../../utils/format";
import { useAuth } from "../../context/AuthContext";

// The trip timeline, in order. The backend sends the current status;
// every step before it is shown as done, every step after it as waiting.
// ⚠️ ASSUMED: the booking response carries `status` and (for trips) a
//    `tripEvents` / `statusHistory` list. If Module 5 names it differently,
//    change the mapping in the timeline section below, not the layout.
const TIMELINE = ["CONFIRMED", "DRIVER_ASSIGNED", "PICKED_UP", "IN_TRANSIT", "DELIVERED", "COMPLETED"];
const TIMELINE_LABELS = {
  CONFIRMED: "Booking confirmed",
  DRIVER_ASSIGNED: "Driver assigned",
  PICKED_UP: "Goods picked up",
  IN_TRANSIT: "On the road",
  DELIVERED: "Delivered",
  COMPLETED: "Trip completed",
};

export default function Tracking() {
  const { bookingId } = useParams();
  const { user } = useAuth();
  const [booking, setBooking] = useState(null);
  const [messages, setMessages] = useState([]);
  const [payment, setPayment] = useState(null);
  const [newMessage, setNewMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const [b, m] = await Promise.all([
          fetchBookingById(bookingId),
          fetchMessages(bookingId),
        ]);
        setBooking(b);
        setMessages(m || []);
        // Payment status is a nice-to-have here — if Module 6 is not ready
        // yet, the page still works without it.
        fetchPaymentForBooking(bookingId).then(setPayment).catch(() => setPayment(null));
      } catch (err) {
        setError(err.response?.data?.message || "Could not load this booking.");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [bookingId]);

  async function handleSend(e) {
    e.preventDefault();
    const text = newMessage.trim();
    if (!text) return;
    setSending(true);
    try {
      const saved = await sendMessage(bookingId, text);
      setMessages((list) => [...list, saved]);
      setNewMessage("");
    } catch (err) {
      setError(err.response?.data?.message || "Message could not be sent.");
    } finally {
      setSending(false);
    }
  }

  if (loading) return <LoadingSpinner text="Loading tracking..." />;
  if (!booking) return <ErrorBox message={error || "Booking not found."} />;

  const currentIndex = TIMELINE.indexOf(booking.status);
  const ownerPhone = booking.ownerPhone || booking.owner?.phone;
  const ownerName = booking.ownerName || booking.owner?.fullName || "Owner";

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold">Booking #{booking.id}</h1>
          <p className="text-gray-600">{booking.pickup} → {booking.destination}</p>
          <p className="text-sm text-gray-500">
            {booking.truck?.registrationNumber || booking.registrationNumber || ""} · {formatRupees(booking.totalFare ?? booking.totalAmount)}
            {payment ? ` · Payment: ${payment.method || "COD"} — ${payment.status}` : ""}
          </p>
        </div>
        <StatusBadge status={booking.status} />
      </div>
      <ErrorBox message={error} />

      {/* --- Timeline --- */}
      <div className="mt-6 rounded border bg-white p-5">
        <h2 className="font-semibold">Trip progress</h2>
        <ol className="mt-4 space-y-3">
          {TIMELINE.map((step, i) => {
            const done = currentIndex >= 0 && i <= currentIndex;
            const isCurrent = i === currentIndex;
            return (
              <li key={step} className="flex items-center gap-3">
                <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${done ? "bg-green-600 text-white" : "bg-gray-200 text-gray-600"}`}>
                  {done ? "✓" : i + 1}
                </span>
                <span className={isCurrent ? "font-semibold" : done ? "" : "text-gray-400"}>
                  {TIMELINE_LABELS[step]}{isCurrent ? " — current step" : ""}
                </span>
              </li>
            );
          })}
          {["REJECTED", "CANCELLED"].includes(booking.status) && (
            <li className="text-red-700">This booking was {booking.status.toLowerCase()}.</li>
          )}
        </ol>
        {booking.deliveryProofNote && (
          <p className="mt-4 rounded bg-green-50 p-3 text-sm text-green-800">
            Delivery proof: {booking.deliveryProofNote}
            {booking.deliveryProofUrl && (<> — <a href={booking.deliveryProofUrl} target="_blank" rel="noreferrer" className="underline">View proof</a></>)}
          </p>
        )}
      </div>

      {/* --- Call + Chat with the owner --- */}
      <div className="mt-6 grid gap-6 md:grid-cols-2">
        <div className="rounded border bg-white p-5">
          <h2 className="font-semibold">Contact the owner</h2>
          <p className="mt-1 text-sm text-gray-600">{ownerName}</p>
          {ownerPhone ? (
            // A tel: link is a real phone call on a mobile — no API, no cost,
            // and it works even if the chat backend is down.
            <a href={`tel:${ownerPhone}`} className="mt-4 inline-block rounded bg-green-600 px-4 py-2 font-medium text-white hover:bg-green-700">
              📞 Call Owner — {ownerPhone}
            </a>
          ) : (
            <p className="mt-4 text-sm text-gray-500">The owner's phone number will appear here once the booking is confirmed.</p>
          )}
        </div>

        <div className="rounded border bg-white p-5">
          <h2 className="font-semibold">Chat with the owner</h2>
          <div className="mt-3 max-h-64 space-y-2 overflow-y-auto rounded border bg-gray-50 p-3">
            {messages.length === 0 ? (
              <p className="text-sm text-gray-500">No messages yet. Say hello to the owner here.</p>
            ) : (
              messages.map((m) => (
                <div key={m.id} className={`max-w-[85%] rounded p-2 text-sm ${m.senderId === user?.userId || m.sender?.id === user?.userId ? "ml-auto bg-blue-100" : "bg-white border"}`}>
                  <p>{m.text || m.message}</p>
                  <p className="mt-1 text-xs text-gray-500">{formatDateTime(m.createdAt)}</p>
                </div>
              ))
            )}
          </div>
          <form onSubmit={handleSend} className="mt-3 flex gap-2">
            <input value={newMessage} onChange={(e) => setNewMessage(e.target.value)} className="flex-1 rounded border px-3 py-2" placeholder="Type a message..." />
            <button disabled={sending} className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300">
              {sending ? "..." : "Send"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
```

### File — `src/pages/customer/Profile.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchMyProfile } from "../../services/authService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMyProfile()
      .then(setProfile)
      .catch((err) => setError(err.response?.data?.message || "Could not load your profile."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading your profile..." />;
  if (!profile) return <ErrorBox message={error} />;

  return (
    <div className="mx-auto max-w-xl rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">My Profile</h1>
      <div className="mt-6 space-y-3 text-sm">
        <div className="flex justify-between border-b pb-2"><span className="text-gray-600">Full name</span><span className="font-medium">{profile.fullName}</span></div>
        <div className="flex justify-between border-b pb-2"><span className="text-gray-600">Email</span><span className="font-medium">{profile.email}</span></div>
        <div className="flex justify-between border-b pb-2"><span className="text-gray-600">Phone</span><span className="font-medium">{profile.phone}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Role</span><span className="font-medium">{profile.role}</span></div>
      </div>
      <p className="mt-4 text-xs text-gray-500">To change your name, phone or password, use a future profile-edit step — this page is read-only in Module 7.</p>
    </div>
  );
}
```

---

## Part 10 — Owner pages

> **Shared owner pattern:** every owner page follows the same three lines — `useEffect` loads data through a service, `loading` shows `<LoadingSpinner />`, `error` shows `<ErrorBox />`. Learn it once on MyTrucks and the other five pages will feel familiar.

### File — `src/pages/owner/OwnerDashboard.jsx`

```jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyBookings } from "../../services/bookingService";
import { fetchMySettlements } from "../../services/paymentService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import { formatRupees } from "../../utils/format";

export default function OwnerDashboard() {
  const [bookings, setBookings] = useState([]);
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const b = await fetchMyBookings();
        setBookings(b || []);
        // Settlements are a nice-to-have — if Module 6 is not ready, the
        // dashboard still works with bookings alone.
        fetchMySettlements().then((s) => setSettlements(s || [])).catch(() => setSettlements([]));
      } catch (err) {
        setError(err.response?.data?.message || "Could not load your dashboard.");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) return <LoadingSpinner text="Loading your dashboard..." />;

  const active = bookings.filter((b) => ["PENDING", "CONFIRMED", "DRIVER_ASSIGNED", "PICKED_UP", "IN_TRANSIT"].includes(b.status));
  const pending = bookings.filter((b) => b.status === "PENDING");
  const pendingAmount = settlements.filter((s) => s.status !== "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);
  const paidAmount = settlements.filter((s) => s.status === "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);

  return (
    <div>
      <h1 className="text-2xl font-bold">Owner Dashboard</h1>
      <ErrorBox message={error} />
      <div className="mt-6 grid gap-4 md:grid-cols-4">
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Active bookings</p><p className="text-2xl font-bold">{active.length}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Waiting for your answer</p><p className="text-2xl font-bold text-amber-600">{pending.length}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Earnings pending</p><p className="text-2xl font-bold">{formatRupees(pendingAmount)}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Earnings received</p><p className="text-2xl font-bold text-green-700">{formatRupees(paidAmount)}</p></div>
      </div>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link to="/owner/bookings" className="rounded bg-blue-600 px-4 py-2 text-white">Review incoming bookings ({pending.length})</Link>
        <Link to="/owner/trucks" className="rounded border px-4 py-2">Manage my trucks</Link>
        <Link to="/owner/trips" className="rounded border px-4 py-2">My trips</Link>
      </div>
    </div>
  );
}
```

### File — `src/pages/owner/MyTrucks.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchMyTrucks, addTruck, setTruckAvailability, fetchCategories } from "../../services/truckService";
import TruckCard from "../../components/TruckCard";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

const BODY_TYPES = ["OPEN", "CLOSED", "REFRIGERATED", "TANKER", "FLATBED"];
// ⚠️ Match these names to your Module 2 BodyType enum (see Home.jsx note).

export default function MyTrucks() {
  const [trucks, setTrucks] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ registrationNumber: "", categoryId: "", bodyType: "OPEN", payloadTons: "" });
  const [formError, setFormError] = useState("");

  function load() {
    setLoading(true);
    Promise.all([fetchMyTrucks(), fetchCategories().catch(() => [])])
      .then(([t, c]) => { setTrucks(t || []); setCategories(c || []); })
      .catch((err) => setError(err.response?.data?.message || "Could not load your trucks."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAdd(e) {
    e.preventDefault();
    setFormError("");
    if (!form.registrationNumber.trim()) return setFormError("Enter the registration number (e.g. JH01AB1234).");
    if (!form.categoryId) return setFormError("Choose a truck category.");
    if (!form.payloadTons || Number(form.payloadTons) <= 0) return setFormError("Enter the payload in tons (the weight this truck can actually carry).");
    try {
      // Slim form (Module 2 correction): only 4 fields. No documents, no
      // insurance, no paperwork — the rate comes from the rate table.
      await addTruck(form);
      setForm({ registrationNumber: "", categoryId: "", bodyType: "OPEN", payloadTons: "" });
      load();
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not add this truck.");
    }
  }

  async function handleToggle(truck) {
    const next = truck.availabilityStatus === "AVAILABLE" ? "UNAVAILABLE" : "AVAILABLE";
    try {
      await setTruckAvailability(truck.id, next);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not change availability.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your trucks..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Trucks</h1>
      <ErrorBox message={error} />

      <form onSubmit={handleAdd} className="mt-6 rounded border bg-white p-5">
        <h2 className="font-semibold">Add a truck</h2>
        {formError && <p className="mt-2 text-sm text-red-600">{formError}</p>}
        <div className="mt-4 grid gap-4 md:grid-cols-4">
          <input value={form.registrationNumber} onChange={(e) => setForm({ ...form, registrationNumber: e.target.value.toUpperCase() })} className="rounded border px-3 py-2" placeholder="Registration no. (JH01AB1234)" />
          <select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })} className="rounded border px-3 py-2">
            <option value="">Choose category</option>
            {categories.map((c) => (<option key={c.id} value={c.id}>{c.name} — {c.targetPayloadTons}t</option>))}
          </select>
          <select value={form.bodyType} onChange={(e) => setForm({ ...form, bodyType: e.target.value })} className="rounded border px-3 py-2">
            {BODY_TYPES.map((b) => (<option key={b} value={b}>{b}</option>))}
          </select>
          <input type="number" step="0.5" min="0.5" value={form.payloadTons} onChange={(e) => setForm({ ...form, payloadTons: e.target.value })} className="rounded border px-3 py-2" placeholder="Payload (tons)" />
        </div>
        <button className="mt-4 rounded bg-blue-600 px-4 py-2 text-white">Add truck</button>
        <p className="mt-2 text-xs text-gray-500">No documents needed. The price for your truck comes from the admin's rate table — you do not set a per-km rate.</p>
      </form>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {trucks.length === 0 ? (
          <EmptyState title="No trucks yet" message="Add your first truck above to start receiving bookings." />
        ) : (
          trucks.map((t) => (
            <div key={t.id}>
              <TruckCard truck={t} />
              <button
                onClick={() => handleToggle(t)}
                disabled={t.availabilityStatus === "ON_TRIP"}
                className="mt-2 w-full rounded border px-3 py-2 text-sm disabled:cursor-not-allowed disabled:text-gray-400"
              >
                {t.availabilityStatus === "ON_TRIP"
                  ? "On a trip — cannot change now"
                  : t.availabilityStatus === "AVAILABLE" ? "Mark unavailable" : "Mark available"}
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
```

### File — `src/pages/owner/IncomingBookings.jsx`

```jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyBookings, acceptBooking, rejectBooking } from "../../services/bookingService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

export default function IncomingBookings() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchMyBookings()
      .then((data) => setBookings(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load bookings."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAccept(id) {
    try { await acceptBooking(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not accept this booking."); }
  }

  async function handleReject(id) {
    const reason = window.prompt("Why are you rejecting this booking? (optional)") || "";
    try { await rejectBooking(id, reason); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not reject this booking."); }
  }

  if (loading) return <LoadingSpinner text="Loading bookings..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Incoming Bookings</h1>
      <p className="mt-1 text-sm text-gray-600">Accept a booking to confirm it. Rejecting frees the truck for other customers.</p>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6"><EmptyState title="No bookings yet" message="New bookings for your trucks will appear here." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => (
            <div key={b.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Booking #{b.id} · {b.truck?.registrationNumber || b.registrationNumber || ""}</p>
                  <p className="text-sm text-gray-600">{b.pickup} → {b.destination} · {b.loadTons} tons</p>
                  <p className="text-sm text-gray-500">{formatDateTime(b.createdAt)} · {formatRupees(b.totalFare ?? b.totalAmount)} · {b.customerName || b.customer?.fullName || "Customer"}</p>
                </div>
                <StatusBadge status={b.status} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link to={`/track/${b.id}`} className="rounded border px-3 py-1 text-sm">View &amp; chat</Link>
                {b.status === "PENDING" && (
                  <>
                    <button onClick={() => handleAccept(b.id)} className="rounded bg-green-600 px-3 py-1 text-sm text-white">Accept</button>
                    <button onClick={() => handleReject(b.id)} className="rounded border border-red-300 px-3 py-1 text-sm text-red-700">Reject</button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

### File — `src/pages/owner/MyDrivers.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchMyDrivers, addDriver, updateDriver, deactivateDriver } from "../../services/driverService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

// ⚠️ ASSUMED endpoints — check against Module 5 (see Part 0).
export default function MyDrivers() {
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ fullName: "", phone: "", licenceNumber: "" });
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState("");

  function load() {
    setLoading(true);
    fetchMyDrivers()
      .then((data) => setDrivers(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your drivers."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError("");
    if (form.fullName.trim().length < 2) return setFormError("Enter the driver's full name.");
    if (!/^[6-9][0-9]{9}$/.test(form.phone.trim())) return setFormError("Phone must be a 10-digit Indian mobile number.");
    try {
      if (editingId) await updateDriver(editingId, form);
      else await addDriver(form);
      setForm({ fullName: "", phone: "", licenceNumber: "" });
      setEditingId(null);
      load();
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not save this driver.");
    }
  }

  async function handleDeactivate(id) {
    if (!window.confirm("Deactivate this driver? They will no longer receive new trips.")) return;
    try { await deactivateDriver(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not deactivate this driver."); }
  }

  if (loading) return <LoadingSpinner text="Loading your drivers..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Drivers</h1>
      <ErrorBox message={error} />
      <form onSubmit={handleSubmit} className="mt-6 rounded border bg-white p-5">
        <h2 className="font-semibold">{editingId ? "Edit driver" : "Add a driver"}</h2>
        {formError && <p className="mt-2 text-sm text-red-600">{formError}</p>}
        <div className="mt-4 grid gap-4 md:grid-cols-3">
          <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} className="rounded border px-3 py-2" placeholder="Full name" />
          <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="rounded border px-3 py-2" placeholder="10-digit phone" />
          <input value={form.licenceNumber} onChange={(e) => setForm({ ...form, licenceNumber: e.target.value })} className="rounded border px-3 py-2" placeholder="Driving licence number" />
        </div>
        <div className="mt-4 flex gap-2">
          <button className="rounded bg-blue-600 px-4 py-2 text-white">{editingId ? "Save changes" : "Add driver"}</button>
          {editingId && (<button type="button" onClick={() => { setEditingId(null); setForm({ fullName: "", phone: "", licenceNumber: "" }); }} className="rounded border px-4 py-2">Cancel edit</button>)}
        </div>
      </form>
      <div className="mt-6 space-y-3">
        {drivers.length === 0 ? (
          <EmptyState title="No drivers yet" message="Add a driver above so you can assign trips to them." />
        ) : (
          drivers.map((d) => (
            <div key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded border bg-white p-4">
              <div>
                <p className="font-semibold">{d.fullName}</p>
                <p className="text-sm text-gray-600">{d.phone} · Licence: {d.licenceNumber || "—"} · {d.active === false ? "Deactivated" : "Active"}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => { setEditingId(d.id); setForm({ fullName: d.fullName, phone: d.phone, licenceNumber: d.licenceNumber || "" }); }} className="rounded border px-3 py-1 text-sm">Edit</button>
                {d.active !== false && (<button onClick={() => handleDeactivate(d.id)} className="rounded border border-red-300 px-3 py-1 text-sm text-red-700">Deactivate</button>)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
```

### File — `src/pages/owner/MyTrips.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchMyTrips, assignDriver, updateTripStatus } from "../../services/tripService";
import { fetchMyDrivers } from "../../services/driverService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatDateTime } from "../../utils/format";

// Trip status order (Module 5). The owner can only move a trip forward
// to the NEXT step — never backwards, never skipping.
const NEXT_STATUS = {
  ASSIGNED: "PICKED_UP",
  PICKED_UP: "IN_TRANSIT",
  IN_TRANSIT: "DELIVERED",
  DELIVERED: "COMPLETED",
};
const NEXT_LABEL = {
  PICKED_UP: "Mark picked up",
  IN_TRANSIT: "Mark in transit",
  DELIVERED: "Mark delivered",
  COMPLETED: "Mark completed",
};

// ⚠️ ASSUMED endpoints — check against Module 5 (see Part 0).
export default function MyTrips() {
  const [trips, setTrips] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [proof, setProof] = useState({}); // tripId -> { note, url }

  function load() {
    setLoading(true);
    Promise.all([fetchMyTrips(), fetchMyDrivers().catch(() => [])])
      .then(([t, d]) => { setTrips(t || []); setDrivers(d || []); })
      .catch((err) => setError(err.response?.data?.message || "Could not load your trips."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAssign(tripId, driverId) {
    if (!driverId) return;
    try { await assignDriver(tripId, Number(driverId)); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not assign this driver."); }
  }

  async function handleNextStatus(trip) {
    const next = NEXT_STATUS[trip.status];
    if (!next) return;
    const p = proof[trip.id] || {};
    try {
      await updateTripStatus(trip.id, {
        status: next,
        deliveryProofNote: next === "DELIVERED" ? p.note || null : null,
        deliveryProofUrl: next === "DELIVERED" ? p.url || null : null,
      });
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not update this trip.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your trips..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Trips</h1>
      <ErrorBox message={error} />
      {trips.length === 0 ? (
        <div className="mt-6"><EmptyState title="No trips yet" message="Trips appear here once you accept a booking and assign a driver." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {trips.map((t) => (
            <div key={t.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Trip #{t.id} · Booking #{t.bookingId}</p>
                  <p className="text-sm text-gray-600">{t.pickup} → {t.destination}</p>
                  <p className="text-sm text-gray-500">Driver: {t.driver?.fullName || t.driverName || "Not assigned"} · {formatDateTime(t.createdAt)}</p>
                </div>
                <StatusBadge status={t.status} />
              </div>

              {!t.driver && (
                <div className="mt-3 flex gap-2">
                  <select defaultValue="" onChange={(e) => handleAssign(t.id, e.target.value)} className="rounded border px-3 py-2 text-sm">
                    <option value="">Assign a driver...</option>
                    {drivers.filter((d) => d.active !== false).map((d) => (
                      <option key={d.id} value={d.id}>{d.fullName} — {d.phone}</option>
                    ))}
                  </select>
                </div>
              )}

              {NEXT_STATUS[t.status] && (
                <div className="mt-3">
                  {t.status === "IN_TRANSIT" && (
                    <div className="mb-2 grid gap-2 md:grid-cols-2">
                      <input value={proof[t.id]?.note || ""} onChange={(e) => setProof((p) => ({ ...p, [t.id]: { ...p[t.id], note: e.target.value } }))} className="rounded border px-3 py-2 text-sm" placeholder="Delivery proof note (e.g. Received by Ramesh, gate 2)" />
                      <input value={proof[t.id]?.url || ""} onChange={(e) => setProof((p) => ({ ...p, [t.id]: { ...p[t.id], url: e.target.value } }))} className="rounded border px-3 py-2 text-sm" placeholder="Proof photo / document URL (optional)" />
                    </div>
                  )}
                  <button onClick={() => handleNextStatus(t)} className="rounded bg-blue-600 px-4 py-2 text-sm text-white">
                    {NEXT_LABEL[NEXT_STATUS[t.status]]}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

### File — `src/pages/owner/Earnings.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchMySettlements } from "../../services/paymentService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDate } from "../../utils/format";

// ⚠️ ASSUMED endpoint — check against Module 6 (see Part 0).
// COD is the default: the customer pays cash on delivery, and the platform
// settles the owner's share afterwards. This page only SHOWS settlements;
// it never creates or edits money.
export default function Earnings() {
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchMySettlements()
      .then((data) => setSettlements(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your earnings."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading your earnings..." />;

  const pending = settlements.filter((s) => s.status !== "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);
  const paid = settlements.filter((s) => s.status === "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);

  return (
    <div>
      <h1 className="text-2xl font-bold">Earnings</h1>
      <ErrorBox message={error} />
      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Pending settlement</p><p className="text-2xl font-bold text-amber-600">{formatRupees(pending)}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Received</p><p className="text-2xl font-bold text-green-700">{formatRupees(paid)}</p></div>
      </div>
      {settlements.length === 0 ? (
        <div className="mt-6"><EmptyState title="No settlements yet" message="Settlements appear here after completed trips." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Booking</th><th className="p-3">Amount</th><th className="p-3">Status</th><th className="p-3">Date</th></tr></thead>
          <tbody>
            {settlements.map((s) => (
              <tr key={s.id} className="border-b">
                <td className="p-3">#{s.bookingId}</td>
                <td className="p-3">{formatRupees(s.amount)}</td>
                <td className="p-3">{s.status}</td>
                <td className="p-3">{formatDate(s.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
```

---

## Part 11 — Driver pages and Admin pages

### Driver pages — read this note before the code

> **Honest note — driver has no login in this MVP.** Module 1 lets a user register with role `DRIVER`, and Module 5 manages drivers as records owned by an OWNER (name, phone, licence) — but there is **no driver login flow** in this MVP: a driver record created by an owner has no email/password and no token of its own. So the driver pages below are **read-only views behind the OWNER role** (plus DRIVER, so they work the day driver login is added). The owner opens them to see what the driver would see, and to update trip status on the driver's behalf from the My Trips page (Part 10). **Do not invent a driver login flow here** — no driver register form, no driver password, no fake driver token. Driver login is a later step, after Module 5.

### File — `src/pages/driver/DriverDashboard.jsx`

```jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyTrips } from "../../services/tripService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

export default function DriverDashboard() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchMyTrips()
      .then((data) => setTrips(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load trips."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading your dashboard..." />;

  const today = trips.filter((t) => ["ASSIGNED", "PICKED_UP", "IN_TRANSIT"].includes(t.status));

  return (
    <div>
      <h1 className="text-2xl font-bold">Driver Dashboard</h1>
      <p className="mt-1 text-sm text-gray-600">
        Read-only in this MVP — the owner manages your trips from their account (see Part 11 note). Once driver login is added, this page will show only your own trips.
      </p>
      <ErrorBox message={error} />
      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Trips in progress</p><p className="text-2xl font-bold">{today.length}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Total trips</p><p className="text-2xl font-bold">{trips.length}</p></div>
      </div>
      <Link to="/driver/trips" className="mt-6 inline-block rounded bg-blue-600 px-4 py-2 text-white">View my trips →</Link>
    </div>
  );
}
```

### File — `src/pages/driver/DriverTrips.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchMyTrips, updateTripStatus } from "../../services/tripService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

// Same forward-only status rule as the owner page (Part 10). The driver
// can move their own trip to the next step — nothing else.
const NEXT_STATUS = {
  ASSIGNED: "PICKED_UP",
  PICKED_UP: "IN_TRANSIT",
  IN_TRANSIT: "DELIVERED",
  DELIVERED: "COMPLETED",
};

export default function DriverTrips() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchMyTrips()
      .then((data) => setTrips(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your trips."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleNext(trip) {
    const next = NEXT_STATUS[trip.status];
    if (!next) return;
    try {
      await updateTripStatus(trip.id, { status: next });
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not update this trip.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your trips..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Trips</h1>
      <ErrorBox message={error} />
      {trips.length === 0 ? (
        <div className="mt-6"><EmptyState title="No trips assigned" message="Trips assigned to you will appear here." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {trips.map((t) => (
            <div key={t.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Trip #{t.id} · Booking #{t.bookingId}</p>
                  <p className="text-sm text-gray-600">{t.pickup} → {t.destination}</p>
                </div>
                <StatusBadge status={t.status} />
              </div>
              {NEXT_STATUS[t.status] && (
                <button onClick={() => handleNext(t)} className="mt-3 rounded bg-blue-600 px-4 py-2 text-sm text-white">
                  Mark {NEXT_STATUS[t.status].replaceAll("_", " ").toLowerCase()}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

### Admin pages

> **Admin pattern:** every admin page is a table plus one action. No fancy charts here — counts on the dashboard, lists everywhere else. Analytics charts belong to Module 8.

### File — `src/pages/admin/AdminDashboard.jsx`

```jsx
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchAdminStats } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

// ⚠️ ASSUMED endpoint GET /api/admin/stats — check against the admin work
//    in Modules 2/4/6. If it does not exist yet, this page can count the
//    lists instead (users + bookings + settlements) — see the note below.
export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchAdminStats()
      .then(setStats)
      .catch((err) => setError(err.response?.data?.message || "Could not load admin stats."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading admin dashboard..." />;

  const cards = [
    { label: "Total users", value: stats?.totalUsers, to: "/admin/users" },
    { label: "Total bookings", value: stats?.totalBookings, to: "/admin/bookings" },
    { label: "Active trucks", value: stats?.activeTrucks, to: "/admin/bookings" },
    { label: "Disputed bookings", value: stats?.disputedBookings, to: "/admin/disputes" },
    { label: "Pending settlements", value: stats?.pendingSettlements, to: "/admin/settlements" },
  ];

  return (
    <div>
      <h1 className="text-2xl font-bold">Admin Dashboard</h1>
      <ErrorBox message={error} />
      {stats ? (
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          {cards.map((c) => (
            <Link key={c.label} to={c.to} className="rounded border bg-white p-4 hover:shadow">
              <p className="text-sm text-gray-500">{c.label}</p>
              <p className="text-2xl font-bold">{c.value ?? "—"}</p>
            </Link>
          ))}
        </div>
      ) : (
        <p className="mt-6 text-sm text-gray-600">
          Stats endpoint not available yet — use the pages in the top menu (Users, Bookings, Settlements, Disputes) until <code>GET /api/admin/stats</code> exists.
        </p>
      )}
    </div>
  );
}
```

### File — `src/pages/admin/UserManagement.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchAllUsers, deactivateUser } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

// ⚠️ ASSUMED endpoints — check against the admin work in Modules 1/6.
export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchAllUsers()
      .then((data) => setUsers(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load users."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleDeactivate(id) {
    if (!window.confirm("Deactivate this user? They will not be able to log in.")) return;
    try { await deactivateUser(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not deactivate this user."); }
  }

  if (loading) return <LoadingSpinner text="Loading users..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">User Management</h1>
      <ErrorBox message={error} />
      {users.length === 0 ? (
        <div className="mt-6"><EmptyState title="No users found" message="Registered users will appear here." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Name</th><th className="p-3">Email</th><th className="p-3">Role</th><th className="p-3">Status</th><th className="p-3">Action</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b">
                <td className="p-3">{u.fullName}</td>
                <td className="p-3">{u.email}</td>
                <td className="p-3">{u.role}</td>
                <td className="p-3">{u.enabled === false ? "Deactivated" : "Active"}</td>
                <td className="p-3">
                  {u.enabled !== false && u.role !== "ADMIN" && (
                    <button onClick={() => handleDeactivate(u.id)} className="rounded border border-red-300 px-3 py-1 text-red-700">Deactivate</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
```

### File — `src/pages/admin/CategoryManagement.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchAdminCategories, addCategory } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

// ⚠️ ASSUMED endpoints — check against Module 2.
// The 4 seed categories (12/20t, 14/25t, 18/30t, 22/36t) already exist;
// this page adds a new one only if the business ever needs it.
export default function CategoryManagement() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ name: "", tyreCount: "", targetPayloadTons: "" });

  function load() {
    setLoading(true);
    fetchAdminCategories()
      .then((data) => setCategories(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load categories."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAdd(e) {
    e.preventDefault();
    try {
      await addCategory(form);
      setForm({ name: "", tyreCount: "", targetPayloadTons: "" });
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not add this category.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading categories..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Category Management</h1>
      <ErrorBox message={error} />
      <form onSubmit={handleAdd} className="mt-6 flex flex-wrap gap-2 rounded border bg-white p-4">
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="rounded border px-3 py-2" placeholder="Name (e.g. 10 Tyre Truck)" />
        <input type="number" value={form.tyreCount} onChange={(e) => setForm({ ...form, tyreCount: e.target.value })} className="rounded border px-3 py-2" placeholder="Tyres" />
        <input type="number" step="0.5" value={form.targetPayloadTons} onChange={(e) => setForm({ ...form, targetPayloadTons: e.target.value })} className="rounded border px-3 py-2" placeholder="Target payload (tons)" />
        <button className="rounded bg-blue-600 px-4 py-2 text-white">Add category</button>
      </form>
      <table className="mt-6 w-full rounded border bg-white text-sm">
        <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Name</th><th className="p-3">Tyres</th><th className="p-3">Target payload</th></tr></thead>
        <tbody>
          {categories.map((c) => (
            <tr key={c.id} className="border-b"><td className="p-3">{c.name}</td><td className="p-3">{c.tyreCount}</td><td className="p-3">{c.targetPayloadTons} tons</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

### File — `src/pages/admin/RateManagement.jsx`

**Simple meaning:** The rate board from Module 3, as a screen. Changing a rate here changes **new quotes only** — old quotes and old bookings keep the rate they were calculated with, because the rate is copied onto the quote at search time.

```jsx
import { useEffect, useState } from "react";
import { fetchRates, updateRate } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import { formatRupees } from "../../utils/format";

export default function RateManagement() {
  const [rates, setRates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState({}); // rateId -> new rate string

  function load() {
    setLoading(true);
    fetchRates()
      .then((data) => setRates(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load rates."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleSave(rate) {
    const value = Number(editing[rate.id]);
    // Same guard as the backend (Module 3): ₹0.10–₹20.00 per ton per km.
    // A typo like 270 would price a trip at lakhs — catch it here AND there.
    if (!value || value < 0.1 || value > 20) {
      setError("Rate must be between ₹0.10 and ₹20.00 per ton per km.");
      return;
    }
    try {
      await updateRate(rate.id, { ratePerTonKm: value, active: rate.active });
      setEditing((e) => ({ ...e, [rate.id]: "" }));
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not update this rate.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading rates..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Rate Management</h1>
      <p className="mt-1 text-sm text-gray-600">Changing a rate affects new quotes only. Old quotes keep their original rate.</p>
      <ErrorBox message={error} />
      <table className="mt-6 w-full rounded border bg-white text-sm">
        <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Category</th><th className="p-3">Tyres</th><th className="p-3">Rate / ton / km</th><th className="p-3">New rate</th><th className="p-3">Action</th></tr></thead>
        <tbody>
          {rates.map((r) => (
            <tr key={r.id} className="border-b">
              <td className="p-3">{r.categoryName}</td>
              <td className="p-3">{r.tyreCount}</td>
              <td className="p-3">{formatRupees(r.ratePerTonKm)}</td>
              <td className="p-3">
                <input type="number" step="0.01" min="0.1" max="20" value={editing[r.id] ?? ""} onChange={(e) => setEditing((ed) => ({ ...ed, [r.id]: e.target.value }))} className="w-24 rounded border px-2 py-1" placeholder={String(r.ratePerTonKm)} />
              </td>
              <td className="p-3"><button onClick={() => handleSave(r)} className="rounded bg-blue-600 px-3 py-1 text-white">Save</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

### File — `src/pages/admin/BookingManagement.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchAllBookings } from "../../services/adminService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

// ⚠️ ASSUMED endpoint — check against Module 4. Read-only: the admin
// watches bookings here; they do not accept, reject or cancel them.
export default function BookingManagement() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchAllBookings()
      .then((data) => setBookings(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load bookings."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading bookings..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Booking Management</h1>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6"><EmptyState title="No bookings yet" message="All bookings on the platform will appear here." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">ID</th><th className="p-3">Route</th><th className="p-3">Fare</th><th className="p-3">Status</th><th className="p-3">Created</th></tr></thead>
          <tbody>
            {bookings.map((b) => (
              <tr key={b.id} className="border-b">
                <td className="p-3">#{b.id}</td>
                <td className="p-3">{b.pickup} → {b.destination}</td>
                <td className="p-3">{formatRupees(b.totalFare ?? b.totalAmount)}</td>
                <td className="p-3"><StatusBadge status={b.status} /></td>
                <td className="p-3">{formatDateTime(b.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
```

### File — `src/pages/admin/SettlementManagement.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchAllSettlements, markSettlementPaid } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDate } from "../../utils/format";

// ⚠️ ASSUMED endpoints — check against Module 6.
export default function SettlementManagement() {
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchAllSettlements()
      .then((data) => setSettlements(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load settlements."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleMarkPaid(id) {
    if (!window.confirm("Mark this settlement as PAID? Only do this after the money has actually been sent to the owner.")) return;
    try { await markSettlementPaid(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not mark this settlement as paid."); }
  }

  if (loading) return <LoadingSpinner text="Loading settlements..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Settlement Management</h1>
      <ErrorBox message={error} />
      {settlements.length === 0 ? (
        <div className="mt-6"><EmptyState title="No settlements yet" message="Settlements appear here after completed trips." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Booking</th><th className="p-3">Owner</th><th className="p-3">Amount</th><th className="p-3">Status</th><th className="p-3">Date</th><th className="p-3">Action</th></tr></thead>
          <tbody>
            {settlements.map((s) => (
              <tr key={s.id} className="border-b">
                <td className="p-3">#{s.bookingId}</td>
                <td className="p-3">{s.ownerName || s.owner?.fullName || "—"}</td>
                <td className="p-3">{formatRupees(s.amount)}</td>
                <td className="p-3">{s.status}</td>
                <td className="p-3">{formatDate(s.createdAt)}</td>
                <td className="p-3">
                  {s.status !== "PAID" && (<button onClick={() => handleMarkPaid(s.id)} className="rounded bg-green-600 px-3 py-1 text-white">Mark PAID</button>)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
```

### File — `src/pages/admin/Disputes.jsx`

```jsx
import { useEffect, useState } from "react";
import { fetchAllBookings } from "../../services/adminService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

// Disputes placeholder (Module 7 scope): there is no separate disputes
// endpoint yet, so this page lists bookings whose status is DISPUTED.
// A full dispute workflow (raise dispute, evidence, resolution) is a
// later module — this page makes sure disputed bookings are at least
// visible to the admin instead of hidden.
export default function Disputes() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchAllBookings()
      .then((data) => setBookings((data || []).filter((b) => b.status === "DISPUTED")))
      .catch((err) => setError(err.response?.data?.message || "Could not load disputed bookings."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading disputes..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Disputes</h1>
      <p className="mt-1 text-sm text-gray-600">Bookings marked DISPUTED. Contact the customer and owner outside the app to resolve them — there is no in-app resolution flow yet.</p>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6"><EmptyState title="No disputes" message="No booking is currently disputed. Good news." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => (
            <div key={b.id} className="rounded border bg-white p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Booking #{b.id}</p>
                  <p className="text-sm text-gray-600">{b.pickup} → {b.destination} · {formatRupees(b.totalFare ?? b.totalAmount)}</p>
                  <p className="text-sm text-gray-500">{formatDateTime(b.createdAt)}</p>
                </div>
                <StatusBadge status={b.status} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

---

## Part 12 — State and UX rules (the rules every page follows)

These rules are not suggestions — they are what make the frontend trustworthy. If a page breaks one of them, fix the page.

1. **Never send a price from the client.** No page calculates a fare, and no request body contains a fare, rate, or total. `POST /api/bookings` sends only `{ quoteId }`. If you are tempted to add a price field "so the backend knows", do not — the backend already knows, from the quote row.
2. **Show the fare breakdown always.** Every place a total appears (Search Results, Booking Confirm, My Bookings, Tracking), the breakdown — base fare, toll, loading, platform fee — is shown or one tap away. A single mystery number is never enough.
3. **Show the minimum-charge note whenever it applied.** `QuoteCard` shows it only when `minimumChargeApplied` is `true`, with the charged tons. A customer paying for 10 tons on an 8-ton load must see why.
4. **Disable booking for expired quotes.** `isQuoteExpired()` (Part 4) disables the Book button in `QuoteCard` and in `BookingConfirm`, and `BookingConfirm` refuses to call the API for an expired quote. The backend also rejects it (Module 3, `validUntil`) — the frontend check is for a friendly message, not for security.
5. **Form validation messages sit next to the field that failed.** The Register page shows "Phone must be a 10-digit..." under the phone box, not in a popup at the top. Backend `fieldErrors` are mapped to the same fields.
6. **Every list has three states: loading, empty, error.** Loading → `<LoadingSpinner />`. Empty → `<EmptyState />` with a clear message ("No truck can carry 28 tons") and a next step. Error → `<ErrorBox />` with the backend's message and a retry button. A blank white page is never acceptable.
7. **Loading and error states on every API call.** No button silently does nothing. While a request runs, the button says "Booking..." / "Logging in..." and is disabled, so a double-tap cannot create two bookings.
8. **Role is read from the token, never typed by the user.** `AuthContext` reads it; `ProtectedRoute` enforces it; the Navbar shows the menu for it. The backend re-checks it on every request.
9. **Tokens are refreshed silently, once.** `api.js` retries a 401'd request once after a refresh. If the refresh fails, the user is logged out and sent to `/login` — never stuck on a broken page, never shown a raw 401.
10. **Call is a `tel:` link; chat is an API.** The Call Owner button is `<a href="tel:...">` — it opens the phone app. The chat box calls `POST /api/bookings/{id}/messages`. Neither pretends to be the other.

---

## Part 13 — Test checklist (do these in order)

You need: the backend running on `http://localhost:8080`, the frontend on `http://localhost:5173`, one CUSTOMER account, one OWNER account with at least one AVAILABLE truck, and one ADMIN account (created directly in the database, Module 1). For tests 4–6, use the Module 3 example: Bengaluru → Hyderabad, 570 km, 20 tons, toll ₹1,500, loading ₹1,000 (expected 12-tyre total ₹33,280).

| # | Test | Expected result |
| --- | --- | --- |
| 1 | Register a new CUSTOMER, then log in, then log out (Navbar → Logout). | Register succeeds and sends you to Login (no token yet — Module 1). Login lands on the search page. Logout returns you to Login, and `/my-bookings` now redirects to `/login`. |
| 2 | Log in as CUSTOMER, then type `/admin` in the address bar. Then log in as ADMIN and type `/my-bookings`. | CUSTOMER is sent to `/` (their home page), not to the admin pages. ADMIN is sent to `/admin`. Neither sees a page for the wrong role. |
| 3 | Log in, wait 16 minutes (or change the access token in DevTools → Application → Local Storage to a wrong value), then click My Bookings. | The page still loads — `api.js` refreshed the token silently and retried once. No login screen appears. |
| 4 | Search flow end-to-end: Home → search Bengaluru → Hyderabad, 570 km, 20 tons → pick the cheapest quote → Confirm → Track. | Quotes appear cheapest first with the full breakdown; Booking Confirm shows the **same** total as the quote; after confirming, the Tracking page opens with status PENDING/CONFIRMED and a timeline. |
| 5 | Same search with load 8 tons on the 20-ton truck. | The quote shows the minimum-charge note ("charged for 10 tons") and `chargeableTons: 10`. Total is ₹17,890 with the same toll/loading/fee (Module 3, Example 2). |
| 6 | Search with load 28 tons when the biggest available truck carries 25 tons. | Empty state: "No truck can carry 28 tons" — and **no** truck card is shown. Showing a truck that cannot carry the load would be worse than showing nothing. |
| 7 | On Search Results, find a quote, wait until its `validUntil` passes (or edit `validUntil` in React DevTools state to a past time), then try to book it. | The Book button is disabled and says "Quote expired — search again". Booking Confirm also refuses to call the API. |
| 8 | As OWNER: add a truck (slim form — only registration number, category, body type, payload), then toggle it AVAILABLE → UNAVAILABLE → AVAILABLE. | The truck appears in My Trucks, then disappears from customer search while UNAVAILABLE, and reappears when AVAILABLE again. |
| 9 | As OWNER: open Incoming Bookings, accept one PENDING booking; as CUSTOMER, open My Bookings. | Owner sees the status change to CONFIRMED; the customer sees CONFIRMED on their list and on Tracking. Reject a second booking and check the truck becomes searchable again. |
| 10 | As CUSTOMER on Tracking: type a message and press Send; as OWNER, open the same booking's tracking page. | The message appears in the customer's chat immediately, and the owner sees the same message. The Call Owner button is a `tel:` link — right-click it and check the link starts with `tel:`, and that it shows the owner's phone number. |
| 11 | As ADMIN: open Rate Management, change the 12-tyre rate from 2.70 to 2.80, then search again as CUSTOMER. Then change it back. | New quotes use 2.80. An old quote (from before the change) still shows 2.70 — check the quote on screen, not from memory. The rate input refuses 270 (shows the ₹0.10–₹20.00 error). |
| 12 | Log out, then open `http://localhost:5173/my-bookings` directly. | Redirect to `/login`. After logging in, you are sent back to `/my-bookings` (the page you originally wanted). |
| 13 | As CUSTOMER, try to open `/owner/trucks` and `/admin/users` by typing the URLs. As OWNER, try `/admin/rates`. | All redirect to the user's own home page. The backend would also return 403 if the frontend check were bypassed. |
| 14 | As OWNER: add a driver, edit their phone, deactivate them; then open My Trips and assign a driver to a trip, then move the trip ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED (with a proof note) → COMPLETED. | Each step appears on the trip and on the customer's Tracking timeline. The status buttons only ever offer the *next* step — never backwards, never skipping. |
| 15 | As ADMIN: open Users (deactivate a test user — they can no longer log in), Settlements (mark one PAID), Disputes (shows DISPUTED bookings, or "No disputes"). | Each action works, and the owner's Earnings page reflects the PAID settlement. |
| 16 | Search with distance 0 and with load 50. Leave pickup empty and press Search. | Field-level errors appear next to the exact field ("Distance must be between 1 and 5000 km", "Enter the pickup place"). The API is never called with invalid input. |

### Common mistakes

- **Calling `axios` or `fetch` directly in a page.** Then a URL change means editing ten files. Every call goes through a service in `src/services/` — pages import functions, never URLs.
- **Storing quotes in `localStorage` so a refresh keeps them.** Quotes expire after 24 hours and belong to the server. Refreshing the Search Results page sends the user back to the search form — that is correct, not a bug.
- **Rebuilding a quote from URL parameters on the Confirm page.** Anyone could type `?totalFare=1` into the address bar. Booking Confirm only accepts a quote passed in router state from Search Results; otherwise it sends the user back to search.
- **Showing only the total fare.** The customer will not understand why 8 tons costs almost as much as 20 tons. Always render the breakdown and the minimum-charge note from the quote response.
- **Forgetting the `tel:` in the Call button.** A button that calls an API named "call" does nothing real. The call is `<a href="tel:+91...">` — the phone app does the work.
- **Putting the role in `localStorage` and trusting it.** The role in `localStorage` is only a display shortcut. `ProtectedRoute` is a friendly redirect; the backend's role check is the real security. Never remove the backend check because "the frontend already blocks it".
- **Two refresh calls racing.** Without the `isRefreshing` flag in `api.js`, three 401s at once would fire three refresh calls and the last one would win with a stale token. The flag and the waiting queue exist for exactly this case — do not simplify them away.
- **A CORS error blamed on the frontend.** If the browser console says `Access-Control-Allow-Origin`, the fix is the backend's allowed origin (Part 3, Step 3) — usually the frontend running on 5174 instead of 5173. Do not add `*` to the backend.

---

## What is next: Module 8 Admin Dashboard & Analytics, final integration, testing and free deployment

Module 7 gives every role a working screen. What is left is to make the whole product provably correct and live:

- **Module 8 — Admin Dashboard & Analytics, final integration, testing and free deployment:** richer admin analytics (bookings per day, revenue, top routes, top owners) on top of the Module 7 admin pages; a full end-to-end pass over Modules 1–7 together (register → search → quote → book → accept → assign → trip → delivered → COD → settlement → mark PAID) with every assumed endpoint in Part 0 verified against the real backend; fixing the gaps this guide flagged (driver login, a real disputes workflow, `GET /api/admin/stats`); and free deployment — frontend on Vercel, backend on Render, MySQL on TiDB Cloud free tier, with the CORS origin changed from `http://localhost:5173` to the real Vercel address and `VITE_API_BASE_URL` pointed at the live Render backend.
