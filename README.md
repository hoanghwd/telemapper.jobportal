# JbTelemapper — employee login and GPS tracking

Version 0.3.0 connects to https://jobportal.huynhdous.com/api/employee/ over HTTPS.

1. In the employee onboarding profile, use Login Credential → Generate login.
2. Sign in on Android with the generated username and temporary password.
3. The temporary password expires 24 hours after generation. The app requires a replacement password before access. Expired unused credentials can be renewed by a recruiter.
4. Permanent passwords use 12–72 UTF-8 bytes. Tokens stay in memory. Reopening the activity can reuse its active tracking service; process termination or phone restart requires sign-in. Sign out stops tracking immediately and requests token revocation.

After login, allow precise location and notifications to start visible GPS reporting at a target interval of 30 seconds. The location foreground service continues with the screen locked and holds a wake lock. Its notification includes Stop and sign out. Android restrictions, poor GPS reception, or lost connectivity can cause gaps; exact 30-second delivery is not guaranteed. Access sessions expire after 12 hours.

Fresh positions include coordinates, capture time, accuracy, session ID, and simulated-location flag. A private SQLite queue survives connectivity interruptions, isolates records by employee, and retries uploads without duplicate records. Offline points expire after seven days; uploads resume when that same employee signs in.

Recruiters use Reports → Employee GPS Trail to view the daily route and assign multiple worksites such as Costco and Walmart. The office timezone determines daily grouping, while stores are separate pins. Assignment pins do not prove attendance.

Build in Android Studio with JVM 21, SDK 35: Build → Generate App Bundles or APKs → Generate APKs. Keep the existing application ID to update the installed prototype.

Backend verification: 32 isolated GPS/worksite checks and 31 authentication checks passed. Android APK generation is blocked in the automation environment by Windows SDK access errors. Build in Android Studio, then test on a real phone:

1. Sign in, allow precise location and notifications, and confirm the tracking notification.
2. Assign two stores to today in the report. Walk with the phone, including several minutes with its screen locked; enable 30-second map refresh and inspect capture timestamps.
3. Disable/re-enable networking and confirm queued points upload only once.
4. Sign out and verify no new captures. Old history remains visible.

Emulator locations are flagged simulated and are not joined into a verified trail. Real-device screen-lock and battery tests remain required before employee rollout.
