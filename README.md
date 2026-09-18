# Easy Backup — Android (native Kotlin + Jetpack Compose)

This is the Android counterpart to the iOS Easy Backup app, brought up to
parity with every design and flow change made on iOS: neumorphic UI, manual
per-folder backup, a storage-device intro step, a 1 GB free quota with a
one-time unlock purchase, delete-from-device with a floating slide gesture,
and the rename + new icon. Where a platform difference made a literal port
impossible, this README says so plainly rather than papering over it.

## What's the same as iOS

- **Manual per-folder backup.** Tapping "Backup Photos"/"Backup Videos"
  lists every folder up front with counts — nothing copies until you tap
  that folder's own icon. Every other folder stays untouched.
- **Only the small trailing icon starts a backup** — tapping the folder
  name or icon does nothing, on purpose.
- **Delete from this device**: once a folder finishes, dragging left-to-right
  anywhere on the row reveals a floating "slide to delete" pill hovering
  above the row (the folder name stays fully visible underneath, never
  fading or covered) — matching Android's own power-menu slider shape. A
  persistent toast also offers the same thing immediately on completion,
  with no auto-timeout. Either path leads to an in-app confirmation dialog,
  and then Android's own system confirmation
  (`MediaStore.createDeleteRequest` on Android 11+) before anything is
  actually deleted — two confirmations, matching iOS exactly.
- **1 GB free quota, tracked by actual bytes copied**, then a one-time
  "Unlock Unlimited Backups" purchase. Hitting the limit mid-folder marks
  that folder `quotaBlocked` (lock icon) and resumes from exactly where it
  stopped once unlocked — no re-copying already-backed-up files.
- **Free access for specific accounts** via a hardcoded allowlist —
  long-press the "Easy Backup" title to reveal your signed-in account
  for collection.
- **Account required to use the app at all** — a full-screen gate blocks
  everything until signed in, since the quota and allowlist both depend on
  knowing who's using the app.
- **Neumorphic design**: soft dual-shadow surfaces on a warm-gray base,
  storage gauge, action tiles, last-backup card, hairline-separated folder
  list — visually matching the iOS app as closely as Compose allows.
- **Rename + icon**: package is now `com.example.easybackup`, app label
  "Easy Backup", and the same vertical USB-drive + wordmark icon as iOS.

## Where Android genuinely differs from iOS — read this before assuming parity

### Google Sign-In needs real setup (like iOS needed App Store Connect)

Google Sign-In requires an **OAuth client ID** registered in Google Cloud
Console, tied to this app's package name and signing SHA-1 fingerprint.
Without that one-time setup, sign-in fails with a developer error — this
isn't a code bug, it's the Android equivalent of the iOS app needing an
App Store Connect record before its in-app purchase would load. Steps:

1. Create (or open) a project in [Google Cloud Console](https://console.cloud.google.com/).
2. APIs & Services → Credentials → Create OAuth client ID → Android.
3. Enter this app's package name (`com.example.easybackup`, or whatever you
   rename it to) and your signing certificate's SHA-1 (get it via
   `./gradlew signingReport` in the project root, or from Play Console once
   uploaded there).
4. No further code change needed — `GoogleSignInOptions.DEFAULT_SIGN_IN` in
   `AccountGate.kt` picks up the registered client automatically.

### The 1 GB quota is per-device here, not per-account — an honest gap

iOS uses iCloud's key-value store, which syncs per signed-in Apple ID, so a
reinstall on the same account doesn't quietly reset the free quota. Android
has no equivalent built into the platform for free — the closest options
(Google Drive's hidden appDataFolder API, or a Firebase project) both need
real setup (a Google Cloud project, API keys, possibly billing enabled)
beyond what's wired up in this build. `UsageTracker.kt` currently stores the
byte count in plain `SharedPreferences`, which means **a reinstall does
reset the free quota** on Android, unlike iOS. This is disclosed rather than
silently different — if true per-account persistence matters to you, wiring
in Firebase or Drive's appDataFolder is the concrete next step; the
Google Sign-In requirement is already in place to make that migration
straightforward later (the account identity is already available).

### The allowlist can use a real email, not just an opaque token

iOS has no API returning a human-readable Apple ID, so its allowlist has to
key on an opaque per-device token. Android's Google Sign-In actually
**exposes the account email directly** (`GoogleSignInAccount.email`), so
`AllowList.kt` keys on the real email address — simpler and more
transparent than the iOS version. Add someone by putting their email in
`AllowList.freeEmails` in `AccountGate.kt`.

### Play Billing needs Play Console setup (like iOS needed an IAP product)

1. Create this app's listing in [Google Play Console](https://play.google.com/console) if you haven't.
2. Create a **one-time (managed) in-app product**, not a subscription, with
   Product ID exactly `unlock_unlimited_backups` — or change
   `Billing.PRODUCT_ID` in `Billing.kt` to match whatever you use. These two
   must be identical or the purchase will never load.
3. Set your own price there.
4. **Play Billing requires at least an internal testing release uploaded**
   before real product details come back — there's no local StoreKit-style
   test configuration file equivalent for Play Billing. For testing without
   a full release, add yourself as a **license tester** in Play Console →
   Setup → License testing, which lets test purchases go through free.

### Deleting media: system confirmation, same idea, different API

Android 11+ uses `MediaStore.createDeleteRequest`, which returns a
`PendingIntent` you launch for a result — the system then shows its own
"Allow app to delete these items?" dialog, exactly analogous to iOS's
`PHAssetChangeRequest.deleteAssets` confirmation and just as unskippable.
On Android 10 and below, deletion is attempted directly per-item and can
throw a `RecoverableSecurityException` requiring its own consent prompt —
handled the same way, via an `IntentSender` the caller launches.

### The storage-intro and paywall are real Material bottom sheets

iOS spent several iterations chasing down a "black border" artifact caused
by `UIKit`'s `UISheetPresentationController` always shrinking the presenting
view to show a system-level peek gap. Android's `ModalBottomSheet` (Material
3) doesn't have this problem at all — it's a proper system-integrated sheet
with no equivalent visual defect, so no custom-overlay workaround was
needed here the way iOS eventually required.

## Setup

1. Open the project folder in Android Studio (Hedgehog or newer). It will
   generate the Gradle wrapper and `local.properties` on first sync.
2. Complete the Google Sign-In and Play Billing setup above — the app will
   compile and run without them, but the account gate and purchase flow
   won't function until they're done.
3. Plug in a USB-C / OTG external drive, connect your Android device
   (USB debugging on), and Run.

## Honest caveats

- This project was carefully written and reviewed but **not compiled here**
  — there's no Android/Kotlin toolchain in this environment. Treat the
  first Android Studio build as the real test. Gradle/AGP/Compose BOM
  versions drift quickly; you may need to bump versions to match your
  installed Android Studio.
- The neumorphic shadow effect (`NeumorphicSurface` in `Theme.kt`) uses
  Compose's `Modifier.blur()`, which needs API 31+ to actually render via
  `RenderEffect` — this matches the project's `minSdk 31`, so there's no
  fallback path needed, but it does mean this visual effect won't degrade
  gracefully if you ever lower the minimum SDK.
- Copying remains sequential (one file at a time) per folder — the same
  memory-safety-first choice as iOS, prioritizing "must not crash" over
  maximum throughput.
- The app icon is a plain static PNG per density bucket, not a true Android
  adaptive icon (separate foreground/background layers with system-driven
  shape masking). It'll look fine but won't get the fancy shape-adaptive
  treatment some launchers apply — a reasonable follow-up if you want to
  polish it further.
