# Release readiness

Current authoritative documents:

- [Online architecture](ONLINE_ARCHITECTURE.md)
- [API and signaling](ONLINE_API.md)
- [Deployment and operations](ONLINE_OPERATIONS.md)
- [Actual verification results and release gates](ONLINE_VERIFICATION.md)

Earlier SQLite/WebView/foreground-only prototype notes have been superseded by the
TypeScript/PostgreSQL/native-WebRTC/FCM implementation. That does **not** mean the
new Android/media path is fully verified. Native debug assembly/lint, 24 JVM tests,
2 actual emulator registration/native-RTP cases and 6 isolated real coturn subtests
now pass on authorized GitHub CI; physical-device media/FCM/public-relay tests and public
deployment remain blocked. A real CI debug APK artifact exists; it is not a
configured production release. No deployed server or successful call is fabricated. Review all unchecked gates before a production claim.

Additional existing-app gates: actual cellular default-dialer/dual-SIM/emergency
routing (only approved lab procedures for emergencies), Bluetooth/headset/hold/DTMF,
OEM lock-screen notifications, permission revocation, TalkBack/font scaling/Bangla,
Room migrations and string-localization completeness. Reminder entries are still
local task-list records, not reliable scheduled notification alarms. Cellular
recording cannot bypass Android/OEM security restrictions.
