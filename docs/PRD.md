# Truck Booking — PRD (summary)

The full PRD v1.0 (17 pages, November 2026) is the PDF
`truckconnect-prd-architecture.pdf` in the workspace. It is not copied here
as a binary file.

**Two things in that PDF are out of date — the code in this repo is newer:**

1. **Name:** the PDF calls the product **TruckConnect**. The product is now
   **Truck Booking** (chosen 11 Nov 2026).
2. **Platform fee:** the PDF's worked example charges a **Rs 500** platform
   fee (total Rs 33,780 for Bengaluru → Hyderabad, 570 km, 20t, 12-tyre).
   The fee is now **Rs 0** (decision 11 Nov 2026). The correct total is
   **Rs 33,280** — see the README pricing table.

Everything else in the PRD still applies: modular monolith, React + Tailwind
frontend, Java 21 Spring Boot backend, MySQL 8, 5 roles, 12/14/18/22-tyre
categories, booking lifecycle, free deployment (Vercel + Render + TiDB Cloud).

A PRD v1.1 rewrite (renamed, Rs 0 fee) is still to do.
