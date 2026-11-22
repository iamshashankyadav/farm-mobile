# farm-mobile — native Android (Kotlin + Jetpack Compose)

Native rewrite of the Expo / React Native apps in `apps/` and `packages/`. No Expo Go needed: build an APK and install it.
It talks to the same backend gateway (`farm-backend`, `/api/v1`); nothing in the backend or web app changes.

## Modules

| Module      | Replaces                     | What                                                                 |
| ----------- | ---------------------------- | -------------------------------------------------------------------- |
| `:core`     | `packages/api`, `packages/ui` | HTTP client, models, auth (OTP / password / sign-up), shared UI, login and team screens |
| `:customer` | `apps/customer`              | Produce browse + cart + checkout, Land plots + reservations + timeline, orders, tracking, rating, notifications, harvest |
| `:delivery` | `apps/delivery`              | Today's route, Google Maps navigation, pickup, proof of delivery (photo + OTP), 30 s GPS pings |

## Build

Needs JDK 17 or 21 and the Android SDK (platform 35). Point Gradle at the SDK with `ANDROID_HOME`
or a `local.properties` file containing `sdk.dir=/path/to/Android/Sdk` (Android Studio writes it
for you). Open the `android/` folder in Android Studio and run `customer` or `delivery`, or from a
terminal, one command per app:

```bash
cd android
./gradlew :customer:assembleDebug   # customer/build/outputs/apk/debug/customer-debug.apk
./gradlew :delivery:assembleDebug   # delivery/build/outputs/apk/debug/delivery-debug.apk
./gradlew testDebugUnitTest         # unit tests of both apps
./gradlew assembleRelease           # release APKs, signed with the debug key
```

Install a debug APK on a connected phone or emulator with `adb install -r <apk>`.
Release APKs are signed with the debug key, so they install directly on any phone (allow "install unknown apps").
Use your own keystore for a Play Store release.

## API server

The apps call the backend gateway, `<server>/api/v1`. The address is set in two places:

1. **At build time**, the Gradle property `farmApiBaseUrl`. The default is in `gradle.properties`;
   override it without editing the file:

   ```bash
   ./gradlew :customer:assembleDebug -PfarmApiBaseUrl=http://10.0.2.2/api/v1
   # or: export ORG_GRADLE_PROJECT_farmApiBaseUrl=http://10.0.2.2/api/v1
   ```

2. **At run time**, on the login screen: **Server: … → Change**. The app keeps that address until it
   is changed again (or reset to the built-in default), so a new IP does not need a new APK.

Which address to use:

| Where the backend runs | Device | Base URL |
| --- | --- | --- |
| Team VM (college network or VPN) | any | `http://10.1.75.79:4501/api/v1` (the default) |
| `docker compose up -d` in `farm-backend` on your laptop | Android emulator | `http://10.0.2.2/api/v1` (the emulator's name for the host) |
| `docker compose up -d` on your laptop | phone on the same Wi-Fi | `http://<laptop LAN IP>/api/v1`, e.g. `http://192.168.1.20/api/v1` |

Demo accounts and the demo story are in `farm-backend/docs/DEMO.md` (password `Farm@12345`).
Plain HTTP is allowed (`usesCleartextTraffic`) because the gateway runs without TLS.

The apps show only what the server returns. When it cannot be reached, each screen says
"Cannot reach the server at <address>" with **Retry** and **Change server**; there is no offline
sample data and no dev login. `NOTES.md` has the baseline from before this was cleaned up.

## Differences from the React Native version

- Push notifications (Expo push token) are not registered; the in-app notification inbox works. FCM needs a Firebase project + `google-services.json`.
- The session is stored in app-private SharedPreferences instead of expo-secure-store.
- The maps are plain canvases (as before), now placing pins by real coordinates; no Maps API key needed.
- Fixed: the pickup checklist items could not be ticked (they were disabled until all were ticked); the plot map's "Starting from" price always showed ₹0.
