# VOZ — Architecture, Build, QA & Release Documentation

**Product Name:** VOZ  
**Tagline:** A smarter way to stay connected.  
**Application ID:** `com.aistudio.vozdialer.qxkrpv`  
**Min SDK / Target SDK:** API 24 / API 36

---

## 1. Technical Architecture
VOZ follows a layered, unidirectional MVVM architecture built with Kotlin and Jetpack Compose:
- **Domain Layer (`com.example.domain`):**
  - `PhoneNormalizer`: Converts Bangla digits (`০-৯`) to ASCII (`0-9`), normalizes Bangladeshi (`+8801...` / `01...` / `00880...`) and international phone numbers to canonical keys for comparison while preserving original strings for display and dialing, and identifies emergency (`999`, `333`, `109`, `16263`) and BD mobile operator prefixes.
  - `T9AndBanglaMatcher`: Supports T9 keypad search (`2=ABC` … `9=WXYZ`), Bangla script (`বাংলা`) matching, case-insensitive English matching, and normalized digit matching.
  - `CallHistoryGrouper`: Groups consecutive same-day calls with the same normalized number into `GroupedCallSession` items and formats durations/timestamps in English and Bangla.
- **Data & Persistence Layer (`com.example.data`):**
  - `ContactsRepository`: Queries `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` on `Dispatchers.IO` and deduplicates multiple numbers per contact.
  - `CallLogRepository`: Queries and manages `CallLog.Calls.CONTENT_URI` on `Dispatchers.IO`.
  - `VozDatabase` & `VozAssistRepository` (Room + KSP): Persists local-only Call Assist metadata (`ContactAssistEntity`), user blocklist (`BlockedNumberEntity`), and call-back reminders (`CallReminderEntity`).
  - `UserPreferencesRepository` (Jetpack DataStore): Persists theme mode, interface language (`en` / `bn`), dialpad haptics, DTMF tones, preferred SIM ID, one-handed mode, reduce motion, and confirm-before-call preferences.
- **Telecom & Role Integration (`com.example.telecom`):**
  - `TelecomDialerManager`: Manages `RoleManager.ROLE_DIALER` and `RoleManager.ROLE_CALL_SCREENING`, discovers multi-SIM `PhoneAccountHandle` subscriptions, and places calls via `TelecomManager.placeCall` with safe `Intent.ACTION_DIAL` fallback.
  - `VozInCallService` & `CallSessionController`: Synchronizes real `android.telecom.Call` states, audio routing (Earpiece, Speaker, Bluetooth), mute, hold, call waiting, DTMF tones, and `NotificationCompat.CallStyle` notifications with `InCallActivity`.
  - `VozCallScreeningService`: Enforces the user's local blocklist against incoming calls.
- **UI & Design System (`com.example.ui`):**
  - Editorial Warm Off-White (`#F6F6F1`) and Deep Charcoal (`#0F1412`) themes with Electric Lime (`#B6FF2B`) accents, Space Grotesk / Plus Jakarta Sans / JetBrains Mono typography, adaptive `NavigationBar` / `NavigationRail`, and full Bangla (`values-bn/strings.xml`) localization.

---

## 2. Build & Installation Instructions
- **Run Unit & Robolectric UI Tests:**
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Build Debug APK:**
  ```bash
  gradle :app:assembleDebug
  ```
- **CI/CD Workflow:** `.github/workflows/android.yml` automatically validates/repairs `gradle-wrapper.jar`, runs unit tests, builds the APK, and uploads `voz-debug-apk`.

---

## 3. Release Checklist & Platform Limitations
- [x] `AndroidManifest.xml` declares required `DIAL`, `VIEW (tel:)`, `CALL_BUTTON` intent filters, `BIND_INCALL_SERVICE`, and `BIND_SCREENING_SERVICE`.
- [x] Zero `INTERNET` permission declared; `voz_call_assist.db` excluded from cloud backup.
- [x] English (`values/strings.xml`) and Bengali (`values-bn/strings.xml`) localizations verified.
- [x] Custom adaptive launcher icon (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, and density `mipmap-*` PNGs) configured.
- [x] Automated unit tests (`ExampleUnitTest`) and Robolectric Room/Compose UI tests (`ExampleRobolectricTest`) executed and passing.

### Known Hardware & Platform Dependencies
1. **Default Dialer In-Call UI (`VozInCallService`):** Android only binds `InCallService` when the user grants `RoleManager.ROLE_DIALER` to VOZ and a real cellular/SIM or Telecom call occurs. When VOZ is not the default dialer, calls are placed via `TelecomManager.placeCall` or handed off to the system dialer via `Intent.ACTION_DIAL`.
2. **Automatic Blocklist Rejection:** Requires VOZ to hold `RoleManager.ROLE_DIALER` or `RoleManager.ROLE_CALL_SCREENING` on Android 10+ (API 29+).
3. **Dual-SIM Subscription Discovery:** Requires `READ_PHONE_STATE` permission and a device with multiple active SIM subscriptions.
4. **Release Signing:** Production release signing uses environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) in `app/build.gradle.kts` so no signing secrets are committed to source control.
