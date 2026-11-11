# Truck Booking

Truck Booking & Logistics Marketplace — book a truck across India as easily as
booking a cab. Customers (businesses and individuals) search trucks, see an
instant per-ton-per-km price, book instantly, and call or chat with the truck
owner. Factory owners can also post a load (tonnage + trucks needed) and book
matching trucks from it.

- **Both B2B + B2C** — companies and individual customers
- **Instant booking** + owner call / chat after booking
- **All India** launch scope
- **Platform fee: Rs 0** (decision 11 Nov 2026 — the PRD's worked example still
  shows the old Rs 500 fee; see `docs/` for the note. The code charges Rs 0.)
- **Slim truck registration** — only registration number, category, body type
  and payload tons. No owner documents, insurance or other paperwork in the
  app, and no admin paper check (Module 3, Part 0 correction).

## Stack

- Backend: Java 21, Spring Boot 3, Spring Security + JWT, Spring Data JPA /
  Hibernate, MySQL 8, Maven
- Frontend: Vite + React 18 + JavaScript + Tailwind CSS + React Router
- Free deployment: GitHub + TiDB Cloud (free MySQL) + Render (free) + Vercel
  (free) — see Module 8

## Modules

| Module | What it does |
| --- | --- |
| 1 | Authentication & Security — register/login, BCrypt, JWT (15-min access + 7-day refresh), 5 roles |
| 2 | Truck Categories & Registration — categories as admin data, slim registration |
| 3 | Search, Matching & Pricing — per-ton-per-km rates, cheapest first |
| 4 | Booking — instant booking from a quote, lifecycle, no double booking, owner call/chat |
| 5 | Driver & Trip Management — assignment, trip timeline, delivery proof |
| 6 | Payments — COD only in MVP; Razorpay is a stub, not connected |
| 7 | React Frontend — customer, owner, driver and admin pages |
| 8 | Admin Dashboard & Analytics + final integration, testing & free deployment |
| 9 | Factory Load Posting — post tonnage + trucks needed, book matching trucks |

The full beginner guides for every module are in `docs/`.

## Pricing

Fare = distance (km) x rate per ton per km x chargeable tons + toll + loading
+ **Rs 0 platform fee**. Chargeable tons = max(load, 50% of truck capacity).

| Category | Rate per ton per km |
| --- | --- |
| 12-tyre / 20t | Rs 2.70 |
| 14-tyre / 25t | Rs 2.60 |
| 18-tyre / 30t | Rs 2.50 |
| 22-tyre / 36t | Rs 2.40 |

Example: Bengaluru → Hyderabad, 570 km, 20t, 12-tyre = 570 x 2.70 x 20
= Rs 30,780 + Rs 1,500 toll + Rs 1,000 loading + Rs 0 fee = **Rs 33,280**.

## Run locally

```bash
# 1. MySQL: create the database (or let the seeders run)
mysql -u root -p -e "CREATE DATABASE truck_booking;"

# 2. Backend (needs Java 21 + Maven; ./mvnw needs the wrapper files from
#    Spring Initializr — see docs/module-8 for the deployment setup)
cd backend
export DB_USERNAME=root
export DB_PASSWORD=your-local-mysql-password
export JWT_SECRET=$(openssl rand -base64 32)
./mvnw spring-boot:run        # backend on http://localhost:8080

# 3. Frontend
cd ../frontend
cp .env.example .env          # VITE_API_BASE_URL=http://localhost:8080/api
npm install
npm run dev                   # frontend on http://localhost:5173
```

Test with `./mvnw test` in `backend/` (H2 test database, no MySQL needed).

## Secrets

Never commit a real secret. `application.properties` reads the database
password and JWT secret from environment variables only. `frontend/.env` is
git-ignored; only `.env.example` (no secrets, just the API address) is
committed. `application-prod.properties` holds only `${...}` placeholders —
real values come from Render environment variables.

## Status — honest notes

- The Java code was extracted from the module guides and has **not been
  compiled or run on this machine** (no Java/Maven here). Run `./mvnw test`
  in VS Code before trusting it.
- No live deployment yet — this repo is only the code. Deployment steps are
  in Module 8 (`docs/`).
- No real payment gateway (COD only), no Google Maps live distance (the
  customer types the distance in km for now), no real-time chat (polling).
- The PRD PDF still says "TruckConnect" and shows a Rs 500 platform fee in
  its worked example. The product name is now **Truck Booking** and the
  platform fee is **Rs 0**; the PRD will be rewritten as v1.1.
