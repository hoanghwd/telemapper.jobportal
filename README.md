# Telemapper — the employee field app (Android)

The phone app field employees use for their workday: sign in, confirm the week's schedule, clock in and out, track the route, knock doors (D2D) or check in at stores (S2S), and see their hours. It talks to the job portal at the address in `app/app-config.properties` (`https://jobportal.huynhdous.com/api/employee/`) over HTTPS.

Package `com.huynhdous.employeefield` · version in `app/build.gradle` (`versionName`) · minSdk 26, targetSdk/compileSdk 35 · Java 17.

## What the employee can do

The menu (top-left) shows these screens, in this order. Some are only for one kind of employee, which the office sets on the web (Program Assignment).

| Screen | For | What it does |
|---|---|---|
| **Profile** | everyone | Name and profile photo (camera or gallery), and changing your own password. |
| **Schedule** | everyone | The week's schedule, a daily time clock (clock in/out, meal and break), hours rings for today and the week. **The week's schedule must be confirmed first** — until then every other screen and all clock buttons are dimmed and a banner says so. |
| **My Trip** | everyone | The day's recorded GPS trail on a map (any date), the assigned territory and door outcomes. A line at the top says whether location tracking is on (with a button to turn it on if the permission was refused). *Refresh* and *Where am I* sit under the map. |
| **Time Sheet** | everyone | This week's worked hours: the total, whether the week is accepted, and each day (with in-progress, missing clock-out and conflict flags). |
| **Events** | S2S | "My Events": the retail events a manager has staffed the rep to, with dates, location and what to promote. |
| **D2D** | D2D | Door-to-door work: start a door from a map dot or *Start a New Door Here* (house confirmed, optional business name), the timer, *Finish* with a required photo and outcome, today's doors (tap one to correct it), doors that failed to upload ("Needs attention"), My Leads (callbacks), and *Preview Route* at the bottom. |
| **Programs** | D2D | What the rep is selling right now (the program a manager assigned, while it is active). |
| **Check In** | S2S | Today's worksite: *I'm Arrived* (GPS must be at the site, then selfie + store photo(s)), *I'm done*, and fixing photos afterwards (retake, delete, add a store photo). |

### Sign-in and tracking

1. The office generates a login (Employee onboarding profile → Login Credential → Generate login). The first sign-in uses the temporary password and the app then requires a replacement (12–72 UTF-8 bytes).
2. On the very first sign-in on a phone the employee must confirm a notice that the work location is shared with the office (asked once per employee on that phone; *Sign out* is the other choice).
3. The app then asks for precise location and notifications and starts a visible foreground service that records GPS positions about as often as `tracking.positions_per_minute` says (default one per minute). It keeps running with the screen locked; its notification has a Stop action. Android restrictions, poor GPS reception or lost connectivity can cause gaps.
4. Positions go into a private SQLite queue (`LocationQueue`) that survives connectivity loss, is kept per employee and retries without duplicates; points older than seven days are dropped. Positions carry coordinates, capture time, accuracy, session and a simulated-location flag.
5. Tokens stay in memory. Reopening the app can continue a sign-in whose tracking service is still running; killing the process or restarting the phone requires signing in again. *Sign out* first sends any finished doors still waiting on the phone (or asks what to do if there is no signal), then stops tracking and revokes the token.

Finished doors work the same way offline-first: the photo and outcome are saved on the phone (`DispositionQueue`) and uploaded in the background; the server's final say (for example a repeat sale at an address) shows up under "Needs attention" where the rep can fix and retry.

## Settings — no addresses or limits in the code

Every URL and tunable number is in **`app/app-config.properties`** (server address, map tiles, geocoder, tracking rate, photo size/quality, door-photo distance, store-photo limit, GPS wait, work-hour targets, meal-break reminder). The build turns each line into a constant in `core/config/Config`; a missing key stops the build with a clear message. To try a value only on your machine (for example a test server), put the key in `app/app-config.local.properties` (same folder; overrides the shared file).

Keep that file plain ASCII with no BOM (write `&copy;` instead of ©) — editing it with Windows PowerShell 5.1 corrupts it.

## How the code is organised

Everything starts in **`main.java`** (the one Android screen). It does nothing but pass what Android tells it — start, pause, saved state, camera and permission answers, Back — to **`app/App`**, which wires the parts together and is the only thing the feature screens talk to.

```
main.java ──► app/App ─┬─ core/session/Session        who is signed in
                       ├─ core/net/Requester          every server call (one at a time, error pop-ups, 401 = signed out)
                       ├─ auth/AuthFlow               sign-in → change password → "who am I" → one-time notice → home
                       │     SignInScreen · ChangePasswordScreen · TrackingNotice · SignOut
                       ├─ location/TrackingStarter    asks for permissions, starts/stops TrackingService
                       ├─ core/tab/TabManager         mounts the tabs, shows one, routes camera/permission answers back
                       └─ home/HomeScreen             greeting card, title bar, schedule banner, menu, and the list of tabs
```

| Package | Contents |
|---|---|
| `app` | `App` — the coordinator. |
| `auth` | Sign-in, change-password, first-sign-in notice, sign-out. |
| `home` | `HomeScreen` (the tab list and menu order), `GreetingCard`, `GateBanner`, `Drawer`. |
| `schedule` · `trip` · `timesheet` · `profile` · `events` · `programs` · `checkin` · `doors` | One feature each. A feature is a `TabModule` plus the classes it needs (e.g. `doors/`: `DoorsTab`, `StartDoor`, `FinishDoor`, `DoorEdit`, `FailedFinishes`, `RoutePreview`, `MyLeads`, `OutcomeForm`, `DispositionQueue`). |
| `location` | `TrackingService` (background GPS), `LocationQueue`, `TrackingStarter`. |
| `core/tab` | `TabModule` (what a screen must provide), `AppHost` (what a screen may ask of the app), `Tabs` (slot numbers), `TabManager`, `ScheduleGate`. |
| `core/net` · `core/media` · `core/location` · `core/ui` · `core/config` · `core/session` | Shared code: server calls and multipart upload, photo shrinking and avatars, distance and GPS fixes, the look (`Theme`, `Popup`, `ActionSheet`, `Insets`), settings, session. |

Rules of the layout: a feature never reaches into another feature or into `main`; if two features need the same thing it goes into `core`. Camera and permission answers are routed to the tab that asked, also after Android closed the app while the camera was open (each tab saves and restores its own state).

**To add a screen:** make a package with a class extending `TabModule` (title, icon, `isAvailableFor`, `buildContent`, `onShown`, optional `requestCodes` / `saveState` / `restoreState`), give it a number in `core/tab/Tabs`, mount it in `HomeScreen.show()` and put it in `HomeScreen.MENU_ORDER`.

The maps are small bundled web pages (`app/src/main/assets/leaflet/`) shown in a WebView; they only ever load our own HTML/JS.

## Build, test, install

Android Studio (JVM 17+, SDK 35), or from a terminal on Windows with Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline
adb -s <phone-serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep the application ID (`com.huynhdous.employeefield`) so installs update the app already on the phone. If the phone is not on USB, use its wireless-debugging entry from `adb devices` as the serial. Unit tests cover the door-upload queue's decision logic (`doors/DispositionQueueDrainOutcomeTest`).

## Testing on a real phone

Emulator locations are flagged as simulated and are not joined into a verified trail, so use a real phone:

1. Sign in, allow precise location and notifications, and confirm the tracking notification and the status line on My Trip.
2. Confirm the week's schedule; clock in and out; check Time Sheet.
3. Walk with the screen locked for several minutes and compare capture times in the web report (Reports → Employee GPS Trail).
4. Turn the network off and on: queued points upload once.
5. D2D: start a door, finish it with a photo, correct it, try one with no signal. S2S: check in at a site, fix a photo, check out.
6. Sign out: tracking stops and no new positions arrive.

Real-device screen-lock and battery tests remain required before an employee rollout.


## Validation and recovery

Run `gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug` with JVM 21.
The release APK is unsigned until release signing is configured. Regression tests exercise camera-state recovery,
session mutation ownership and bounded input reads. Real-phone screen-lock, camera and permission checks remain required.
Sign-out waits for a photo upload to finish; the notification's Stop action stops GPS immediately and delays token
revocation until the upload finishes. Failed check-ins retain photos; retry checks assignment status first.

Ready-to-send S2S check-ins now save their photos and assignment details in employee-scoped private storage before upload.
Reopening the app restores the waiting check-in without an activity-state Bundle; retry checks the server first.
Starting another arrival offers to continue or explicitly discard the saved check-in. Discard and successful completion
delete owned draft files, while rotation preserves upload inputs. Tokens are never stored with drafts.
Real-phone camera capture, offline retry, app restart, and discard checks remain required.
