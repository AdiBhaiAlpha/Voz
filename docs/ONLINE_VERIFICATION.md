# Verification, end-to-end procedure and release gate

## Actual results in this workspace

| Check | Actual result |
|---|---|
| `cd server && npm run build` | Passed TypeScript strict compilation |
| `npm test` | Passed 3 security unit tests |
| `npm run test:db` | Passed real temporary PostgreSQL suite: 12 subtests (13 TAP entries including parent suite) |
| `npm audit --omit=dev` | 0 reported vulnerabilities in locked production dependencies at verification time |
| Kotlin syntax parser / Android XML parse / patch whitespace | Passed; **not** Kotlin/Android type checking |
| `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` | Attempted; blocked before Gradle: no Java/JAVA_HOME |
| Android Compose instrumentation/UI tests | Added registration-validation test; not run: no SDK, emulator/adb or device |
| Docker Compose build/start, Caddy HTTPS, coturn allocation | Not run: Docker/public host/DNS/certificates unavailable |
| Real FCM delivery (background/locked/Doze) | Not run: Firebase credentials/project/device unavailable |
| Two Android devices on separate networks | Not run: no real devices/public deployment |
| Actual ICE media, packet loss, TLS relay credential rejection after expiry | Not run; protocol/HMAC tests are **not** proof of media/relay behavior |

PostgreSQL tests cover transactional ID uniqueness, authentication/lookup, concurrent
busy exclusion, accept/reject/cancel/timeout, spoofing/invalid transitions, selected-
device media restrictions, duplicate requests/events, restart generations, refresh
rotation/replay, device revocation, push-outbox behavior, delivery failure, signaling
leases, account deletion and authenticated WS delivery/replay. FCM is a test sender
in those tests; SDP/ICE payloads are protocol fixtures, not real audio.

**No updated APK or live working two-device call has been claimed.** Android code
must be compiled and integration failures fixed on a configured build host before
any production release. This document supersedes earlier SQLite/WebView MVP notes.

## Reproduce backend tests

Follow `server/README.md`. Use a disposable PostgreSQL 18 database or embedded PG
binary. CI runs unit + DB tests and audit. Android CI runs tests/lint/assembly when
triggered on configured GitHub infrastructure; remote CI was not triggered here.
Run added Compose test with an emulator/device:

```sh
./gradlew connectedDebugAndroidTest
```

Do not confuse the registration form UI test with microphone/FCM/native audio tests.

## Two-device acceptance procedure (mandatory, currently unexecuted)

### Prepare

1. Deploy API/PostgreSQL/coturn/HTTPS per ONLINE_OPERATIONS. Configure real FCM on
   server and Android. Build with JDK 17/SDK 34 and install the **newly built** APK
   on devices A and B; checked-in legacy APKs do not contain these changes.
2. A uses Wi-Fi, B uses mobile data (not tethered to A). Note OS/OEM/network versions
   and app commit/build properties. Verify public DNS/certificate chain and TURN
   firewall. Never store passwords, bearer/refresh/FCM tokens or SDP/IP addresses in
   publicly shared test logs.
3. Register distinct accounts/display names. Confirm server IDs are unique, correctly
   formatted and stable after sign-in; no client-supplied arbitrary ID works. Verify
   that local draft/profile data does not activate an identity.
4. Grant microphone only after explicit call/answer consent. Permit notifications,
   optional overlay and Android 14 full-screen call access. Confirm a denied mic or
   notification permission has a clear supported failure/fallback, not fake success.

### Foreground media

- [ ] Dial B's VOZ ID from A's VOZ keypad mode. It opens the native client, not a
      WebView, carrier intent or SIM selector. Incomplete IDs cannot be called.
- [ ] B sees verified incoming identity and can decline. A sees terminal state; both
      may place another call immediately. Rapid duplicate taps do not allocate two calls.
- [ ] Accept and verify **bidirectional spoken audio** for several minutes. Connected
      appears only on real WebRTC connection callback. Mute/unmute both directions;
      speaker/earpiece/headset behavior and audio focus interruption work correctly.
- [ ] End from each device and from notification/island; mic indicator stops promptly,
      media/FGS/overlay resources release, remote ends, server concurrency slots free.
- [ ] Repeat enough calls to detect factory/audio-device/native-memory leaks.

### Direct and relay paths

- [ ] Observe selected ICE candidate pair using temporary redacted WebRTC diagnostics
      on a private test build. Prove direct connectivity where possible; never assert
      it solely because a call succeeded.
- [ ] Build with `-PVOZ_FORCE_RELAY=true`; repeat bidirectional audio on separate
      networks and prove a relay candidate/actual coturn allocation. Restore false
      for normal release. Test TURN/UDP, TURN/TCP and TURN/TLS separately.
- [ ] Fetch ICE credentials, validate relay allocation; after their 600s TTL, verify
      a **new** allocation with expired credentials is rejected by real coturn. Existing
      allocations have separate lifetimes; do not conflate them with new auth checks.
- [ ] Obtain fresh credentials and prove restart succeeds. Verify private/loopback
      relay targets are denied. Wrong secret/cert/firewall must fail honestly.

### Background, lock and process delivery

- [ ] B presses Home with no active call; no permanent idle WS remains. Call from A.
      FCM should start verified incoming notification/island without mic acquisition.
- [ ] Repeat while B is locked/screen off. Full screen only where platform permission
      allows; otherwise heads-up/tap notification. Answer requests consent/permission
      appropriately and establishes audio; switching app/Home preserves active audio.
- [ ] Repeat in Doze (`adb shell dumpsys deviceidle force-idle` in a controlled test;
      restore with `adb shell dumpsys deviceidle unforce`). Test Google/OEM devices.
- [ ] Test process termination without force-stop. Test force-stop separately and
      record platform non-delivery until relaunch as a supported limitation, not a pass.
- [ ] Delay a push beyond 30s, duplicate it, reject on another registered device and
      revoke the session/device. No expired/unauthorized acceptance or mic starts.
- [ ] Disable/expire push credentials/token. Foreground socket still receives; offline
      missing reachability or exhausted known delivery failure yields unavailable,
      otherwise no answer times out. No silent infinite ringing or cellular fallback.

### Reconnect and concurrency

- [ ] Switch Wi-Fi ↔ mobile while connected, temporarily interrupt network, restore.
      Reconnecting is truthful, ICE restarts use a fresh generation/config, no offer
      glare or stale ICE acceptance, and peer recovers or times out cleanly.
- [ ] Restart API while a call is active. PostgreSQL state persists; WS reconnects,
      refreshes auth and replays seq without duplicate side effects. Reconnect within
      lease grace or cleanly end after timeout; idle sockets never extend ended calls.
- [ ] Kill/reopen the app during an accepted call. No mic capture before renewed user
      consent; resume requests fresh negotiation. It either recovers or ends honestly.
- [ ] Two callers simultaneously call one recipient; one wins, other gets busy. Accept
      on two recipient devices; only one is pinned. Other device cannot read SDP/ICE,
      forge caller, accept/end an active call, replay a transition or use revoked tokens.
- [ ] Exercise packet loss/jitter in a controlled router/network emulator. Assess
      actual intelligibility, jitter/packet stats, reconnect deadline and resource use.

## Release checklist

- [ ] Android compile, lint, JVM tests and Compose tests pass; fix API/SDK integration
      errors rather than bypassing failing tests. Sign release with protected key.
- [ ] All two-device/media/FCM/lock/Doze/relay tests above completed with evidence.
- [ ] API/FCM/TURN deployment healthy; TLS/cert renewals and expiring TURN auth tested.
- [ ] Backup AND isolated restore exercised; migration/rollback rehearsed. Do not
      restore old sessions/calls as active. Pin/scanned images and dependencies.
- [ ] Rate/load/registration-abuse tests, moderation/block/report and account recovery
      plan approved before public signup. Default rate limits alone are not SaaS AAA certification.
- [ ] Privacy policy, retention/deletion including backups, permission/FGS/full-screen
      Play declarations, target-SDK/store requirements reviewed; no credentials in Git.
- [ ] Notification/overlay denial, accessibility/large fonts/rotation, battery/OEM
      behavior and recording disclosures tested. Never promise prohibited cellular recording.
- [ ] Staged rollout, operator alerts, TURN bandwidth budgets and incident response
      owner assigned. Explicit single-API-instance limitation documented.
