# Verification, end-to-end procedure and release gate

## Actual results — 20 October 2026

The [autonomous delivery audit](AUTONOMOUS_DELIVERY.md) records provider research,
authorized capabilities and unavoidable external gates. No owner setup work is
requested. Operator/lab procedures below are reference acceptance gates, not
assignments to the owner or evidence of a deployment.

| Check | Actual result |
|---|---|
| Backend TypeScript strict compilation | Passed locally and on authorized GitHub CI |
| Unit/security/capability/CI-diagnostic tests | 14 passed locally and on hosted CI |
| Real PostgreSQL integration | 12 subtests passed (13 TAP entries including parent); temporary PG18.4 locally and PG18 container in CI |
| Production npm audit | 0 reported vulnerabilities in locked production dependencies at verification time |
| Native Android debug assembly | Passed on hosted CI; genuine debug APK artifact produced |
| Android JVM tests | **24 actual cases, 0 failures, 0 skipped**; independently checked from JUnit XML |
| Android lint | **Passed** after fixing direct permission checks and declaring actual English/Bangla support/completing Bangla resource keys; no disabled checks or baseline |
| Registration form UI | Passed on API30 x86_64 emulator; required testcase confirmed from JUnit XML |
| Two native PeerConnections, real audio RTP counters on one emulator | **Passed** actual ICE/DTLS negotiation and nonzero audio RTP sent/received on both peers. Required testcase confirmed, not skipped. Not a two-device/public call or human speech test |
| Real coturn 4.6.3 fixture | **6 subtests passed**: UDP/TCP relay packets, TLS relay with trusted fixture cert, bad-HMAC rejection, real expiry/new-allocation rejection + fresh recovery, denied private-peer rejection |
| Native Kotlin syntax / Android XML / workflow YAML / shell / whitespace checks | Passed separately; syntax parsing is not Android type checking |
| Local Gradle command | Still blocked before Gradle: no local Java/JAVA_HOME. The actual build route is authorized hosted CI, not a fabricated local build |
| Production Compose/Caddy public deployment | Not run: no authorized durable public host/DNS/deployment credentials |
| Public TURN allocation / real phone relay | Not run; the passing coturn test is isolated loopback infrastructure, not public hosting |
| FCM delivery (background/locked/Doze) | Not run: no accessible Firebase project/service-account/client configuration or real device |
| Two physical Android devices on separate networks | Not run: no physical devices/public deployment |
| Human-audible bidirectional voice, public packet-loss/reconnect/OEM behavior | Not run; emulator/native packets and protocol fixtures are not a substitute |

### Hosted evidence and scope

- [Backend and real coturn CI, source 66269c3](https://github.com/AdiBhaiAlpha/Voz/actions/runs/39369496312): success.
- [Android compile/JVM/lint/native-SDK/UI run, source 66269c3](https://github.com/AdiBhaiAlpha/Voz/actions/runs/39369496378): **success**. Guard step independently confirms 24 JVM cases plus 2 instrumented cases, all unskipped and passing.
- [New debug APK artifact](https://github.com/AdiBhaiAlpha/Voz/actions/runs/39369496378/artifacts/12240293966): 42,094,900 bytes (Actions ZIP), not a production-configured release.
- [Machine-readable evidence snapshot](VERIFICATION_EVIDENCE.json) records source SHA/run IDs/counts; later documentation-only changes do not change the tested native/server source.
- [Earlier successful debug assembly/JVM test steps and genuine debug APK artifact](https://github.com/AdiBhaiAlpha/Voz/actions/runs/39366672102): the overall run failed lint; it is not claimed green.
- [Earlier passing registration UI job](https://github.com/AdiBhaiAlpha/Voz/actions/runs/39367265611): overall run also had a lint failure.

Legacy APKs tracked at the repository root have not been replaced. New debug APKs
are Actions artifacts, **not configured/signed production releases**. No deployed
server, real push receipt, callable identity or Internet voice call is fabricated.

The first hosted attempt failed at redundant Android SDK setup; a stable Ubuntu
24.04 runner with its installed SDK worked. The workspace cannot follow Actions
log/artifact storage redirects, so failures are surfaced as API-readable check
annotations. Gradle's actual exit status is preserved. A proposed workflow-context
credential inventory returned `action_required` without jobs; it was removed,
not bypassed. Repository secret/variable listings remain unavailable to this
integration; absence of visible credentials is not proof that hidden secrets do
not exist.

PostgreSQL tests cover transactional ID uniqueness, authentication/lookup, concurrent
busy exclusion, accept/reject/cancel/timeout, spoofing/invalid transitions, selected-
device media restrictions, duplicate requests/events, restart generations, refresh
rotation/replay, device revocation, push-outbox behavior, delivery failure, signaling
leases, account deletion and authenticated WS delivery/replay. FCM is a **test sender**
in those tests; SDP/ICE payloads there are protocol fixtures, not real audio.

The coturn fixture starts the exact production image version on an ephemeral hosted
runner, binds only loopback, generates temporary secrets/certificates privately,
and checks real client send/receive packet counters. Loopback peers are enabled
**only in that fixture**. Production private/loopback denial is unchanged. TLS is
validated against an isolated self-signed fixture trust anchor, not a public CA.
The expiry test backdates the test input timestamp so a previously accepted
credential expires after roughly 10 seconds, waits for actual wall-clock expiry,
and attempts a **new allocation**. Production credential TTL remains 600 seconds.
No CI job is used as a permanent signaling/database/relay server.

## Automatic and reproducible verification

Branch pushes run the actual CI jobs. Android runs only for Android/build/workflow
changes, avoiding needless rebuilds on research-only changes. No owner JDK,
emulator, account creation or manual workflow dispatch is needed.

Developer/lab references (not owner tasks):

```sh
cd server
npm run build
npm test
npm run test:db     # real disposable PostgreSQL; see server README
npm run test:turn   # requires actual Docker + openssl; no mock/skip substitute
npm audit --omit=dev
npm run autonomy:preflight # exit 2 correctly reports absent capabilities
```

The Android CI runs assembly/JVM tests/lint and `connectedDebugAndroidTest` for
registration validation and native-SDK loopback on an API30 emulator. None of
these establishes FCM delivery, public reachability, physical microphone quality,
Android14+ foreground-service behavior, or a two-device Internet call.

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

- [x] Hosted debug compile/lint, 24 JVM cases and 2 emulator registration/native-RTP
      cases pass. Required test XML is checked to reject missing/skipped tests.
- [ ] Production-configured release build, protected signing, current store target-SDK
      compliance and real Android14+/OEM behavior verified. Debug CI is not a release.
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
