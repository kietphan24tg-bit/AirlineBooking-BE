# Flight Booking System — Project Spec

## Overview
Simulate a real airline booking flow: search → book → pay → check-in → board.

---

## User Roles
- **Passenger**: Main user — search, book, pay, check-in, manage booking
- **Admin/Manager**: Optional — manage flights, seats, reports

---

## Core Flow
1. **Search** — airport (from/to), date, passengers, trip type (one-way / round-trip / multi-leg)
2. **Select** — flight + fare class (Economy / Business) with benefits shown
3. **Passenger Info** — name, gender, DOB, nationality, passport → system creates PNR (`PENDING`)
4. **Add-ons** — seat map, meal, extra baggage
5. **Payment** — QR / e-wallet / bank transfer → success: PNR → `CONFIRMED`, ticket issued
6. **Manage Booking** — change seat/service via PNR (locked after check-in)
7. **Check-in Online** — 24h before departure, closes 40min (domestic) / 60min (international)
8. **Boarding Pass** — QR code with gate, time, seat
9. **Board** — staff scans QR → `is_boarded = true`

---

## PNR & Seat Locking Logic
- PNR created at checkout → seat status: `RESERVED`
- **15-min timeout**: PNR → `EXPIRED`, seat → `AVAILABLE`
- Payment success: PNR → `CONFIRMED`, ticket generated
- `checked_in` status → all edits blocked

---

## Feature Groups

### MVP (Required)
| ID | Feature |
|----|---------|
| A | Auth — register, email verify, profile |
| B | Flight search — time, price, availability |
| C | Browse flights (no booking) |
| D | Seat map + add-ons (baggage, meal, lounge) |
| E | Payment processing |
| F | E-ticket + PNR generation |
| G | Booking management via PNR |
| H | Online check-in |
| I | Boarding pass (QR) |
| J | Booking history + transaction detail |

### Advanced (Optional)
- **Notifications** — confirmation email, check-in reminder, flight change alert
- **Promotions** — discount codes applied at checkout
- **Recommendations** — personalized flight suggestions

---

## EPICs
`EPIC-1` Auth · `EPIC-2` Flight Search · `EPIC-3` Booking Flow · `EPIC-4` Payment · `EPIC-5` Ticket & PNR · `EPIC-6` Check-in · `EPIC-7` Notification · `EPIC-8` Promotion · `EPIC-9` System Jobs (cron)

---

## Key Technical Notes
- **Double booking**: lock seat via `flight_seats.status = reserved` + 15-min timeout
- **Auto-expire**: cron job releases unpaid bookings on `pnr_expires_at`
- **Search perf**: index on `departure_airport_id`, `arrival_airport_id`, `flight_date`
- **Status sync**: auto-notify on flight `DELAY` / `CANCEL`

---

## Rollout Plan
1. **Phase 1 — Core**: Search, Booking, Passenger Info, Mock Payment, Ticket
2. **Phase 2 — Services**: Seat Selection, Baggage, Meal
3. **Phase 3 — Finalize**: Check-in, Boarding Pass, PNR Management
