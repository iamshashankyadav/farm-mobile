# Android notes

Working notes for the Android overhaul (farm-mobile #52). The brief is
`farm-backend/docs/android-handoff.md`.

## Baseline (main at b336349, 2026-11-22)

**Build:** on a clean checkout `./gradlew assembleDebug testDebugUnitTest` passes with JDK 21 and
Android SDK platform 35 (build-tools 36). Both debug APKs are produced and the 11 existing unit
tests pass (`CheckoutTest` 6, `RouteTest` 5). The only blocker was that `gradlew` was not
executable in git, so `./gradlew` failed with "Permission denied" on Linux and macOS; it is now
marked executable.

**How the screens were assessed:** by reading each screen's data path. They have not been run on an
emulator yet (the machine used has no emulator image), so there are no screenshots in this
baseline. Add one per row after a run on a Pixel 6 / API 34 emulator against the demo backend.

"Fake" means the screen can show data that does not come from the server.

### Customer app

| Screen | State | Why |
| --- | --- | --- |
| Login | Fake path | "Mock … Login" buttons create a local session with no token (`AuthManager.devMockLogin`) |
| Home, Produce tab | Fake on error | Any failed search falls back to `assets/seed_listings.json` (`Browse.kt` ~188-216) and shows it as real |
| Listing detail | Fake on error | Falls back to the matching seed listing (`Browse.kt` ~219-235) |
| Cart | Works | Local cart by design (no `/cart` API) |
| Checkout | Works | Real payment intent and `POST /order/orders` with an Idempotency-Key |
| Order placed | Works | Shows the `Checkout` the server returned |
| Orders list | Works, hides errors | Real `GET /order/orders`; failures are a catch-all ("Load more" failures are silently ignored) |
| Order detail | Works | Real `GET /order/orders/{id}`; 6-step timeline, no cancel |
| Tracking and rating | Works | Real track and rating calls; the map is a plotted canvas |
| Notifications | Works, hides errors | Real inbox; failures are a catch-all, no unread badge |
| Land, Explore | Fake on error | Falls back to `seed_plots.json`; real plots get `"Local Village"`, `"Local District"`, and `"Loam"` / `"Borewell"` when soil or water is missing (`Land.kt` ~155-171) |
| Land, Reserve | Fake | `submitReservation` ignores the API error (`// Simulated offline creation`) and invents a `res-<millis>` reservation |
| Land, My reservations | Fake on error | Falls back to `seed_reservations.json`; real ones get `"Reserved Farmland Plot"`, `"Active Season"`, `"Assigned Farmer"` |
| Crop timeline | Fake | Never calls `GET /land/reservations/{id}/updates`; shows local data only |
| Deposit payment | Missing | No screen pays the reservation deposit, so a reservation cannot reach ACTIVE from the app |
| Harvest choice | Works | Real `POST /order/orders/harvest`; offered whenever an order exists, not only in FULFILLING |
| Team | Works | Static team data |

### Delivery app

| Screen | State | Why |
| --- | --- | --- |
| Login | Fake path | Same mock login buttons as the customer app |
| Home | Works | Shows the signed-in partner |
| Route | Works, hides errors | Real `GET /logistics/my-route`; one refresh path is a catch-all (`RouteScreen.kt` ~153) |
| Pickup / deliver sheets | Works | Real pickup, deliver with OTP and photo upload |
| Location pings | Works | Every 30 s while the route is active; a failed ping is dropped silently by design |

### Order of fixes

The customer order-side issues (#39, #40, #42, #43, #44) are in review as PRs #53 to #57. The
fake paths above are removed in #36; sign-in and token refresh are #37.

## After #36

Every "Fake" row above now loads from the server only. With the server off, each screen that
loads data shows the error state (the server's message or "Cannot reach the server at <URL>",
Retry, Change server); a refused reservation shows the server's reason and creates nothing.
The login screen keeps password and OTP sign-in, sign-up and the server address.

## Server address

See `README.md`, section "API server": a Gradle property at build time, or the login screen at run
time.

## Backend issues

Problems in the backend found while building the apps. Each entry: the request, the response, and
what we expected. The backend owner fixes them; the apps do not work around them.

1. **Track returns another customer's delivery code.**
   Request: `GET /logistics/orders/{orderId}/track` as any signed-in user.
   Response: `200` with `stop.otp` for that order's drop stop, even when the order belongs to
   someone else (the route only checks that the caller is signed in).
   Expected: `404` (or `403`) unless the caller is the order's customer, the assigned partner or
   an admin.
