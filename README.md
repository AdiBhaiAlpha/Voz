# VOZ — Android dialer and internet voice calling

Kotlin/Jetpack Compose Android app with cellular Telecom integration and a new
native VOZ-to-VOZ WebRTC path. VOZ addresses such as `VOZ-302-8671` are kept separate
from carrier numbers; no cellular fallback or fake online activation is used.

## Internet calling architecture

- **TypeScript/Fastify API + PostgreSQL migrations**: authenticated accounts,
  server-allocated identities, rotating sessions, devices and authoritative calls.
- **Authenticated WebSockets + durable event replay**: SDP/ICE signaling, concurrency
  exclusion, invite expiry, generations, reconnect and device authorization.
- **Native Android WebRTC audio**: microphone consent/permission, actual peer states,
  direct P2P or short-lived authenticated coturn relay, encrypted DTLS-SRTP media.
- **FCM high-priority data + Android call service**: verified incoming invitations,
  background delivery path, supported notifications/full-screen rules and island.
- **Self-hosting**: Docker Compose, PostgreSQL, Caddy HTTPS/WSS, coturn and optional
  Firebase/TURN-TLS overrides. No production credentials are included.

See the [autonomous delivery/capability audit](docs/AUTONOMOUS_DELIVERY.md) and
[actual test results + release gates](docs/ONLINE_VERIFICATION.md). Technical
references: [server](server/README.md), [API](docs/ONLINE_API.md),
[operations](docs/ONLINE_OPERATIONS.md). These references are not owner setup tasks.

**Status:** Backend compilation, unit tests and real PostgreSQL integration tests
pass locally and on authorized GitHub CI. Native Android debug assembly, lint and 24 JVM
tests pass on a real hosted runner. Registration UI and actual native WebRTC audio
RTP between two peers on one emulator also pass (2 unskipped tests). Public deployment, real TURN allocation, FCM receipt and
two-device audio remain blocked/unverified. This is **not production ready**.
No owner account creation, secret submission or manual deployment is requested.

## Automated Android build and developer reference

Branch pushes automatically run Android and backend CI. The hosted runner supplies
JDK 17 and Android SDK tooling; the owner does not need to install them.
For reproducibility on an independently provisioned developer machine:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
# Device/emulator UI tests:
./gradlew connectedDebugAndroidTest
```

The native build reads existing authorized `VOZ_SERVICE_URL` and Firebase client
configuration from Gradle properties or environment variables. Absent a real
service configuration, online activation remains disabled rather than using a
preview/sample backend. The registration UI is under Tools → VOZ online.
Real identity registration requires a validated display name and authenticated
server session. Existing locally generated/demo IDs are not callable identities.

Release signing must be supplied securely; it intentionally does not use the debug
key. The legacy APK files in the repository are unchanged. Newly compiled debug APKs
are separate GitHub Actions artifacts, not signed/configured production releases.

## Native cellular UX improvements

Favorites filtering, safer deletion/confirmation, SIM confirmation preservation,
atomic UI state updates, safer identity normalization, private backup rules and
incoming screening hardening. The dynamic island uses bounded width, status-bar-safe
positioning, one expansion state/animation and accessible action targets. Native
call screens return to overlay on Home/app switching with user permission.

Unlocked interactive calls use island/notification rather than stealing focus;
locked/screen-off calls may use full screen under OS rules. An unlocked launcher
cannot be universally distinguished without invasive usage-access privileges.
Optional proximity auto-speaker is for native cellular calls, debounced and headset-
protecting. Recording is consent-based **microphone capture**, not guaranteed
prohibited two-sided cellular capture; failures are reported and invalid files removed.

Internet infrastructure is not cost-free: hosting/TURN bandwidth and mobile data
may cost money even without per-minute app charges. Emergency/PSTN calling remains
separate from online VOZ calling. Read the release checklist before distribution.
