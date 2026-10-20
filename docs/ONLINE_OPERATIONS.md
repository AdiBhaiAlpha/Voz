# Deploying and operating VOZ

## Dependencies you must supply

- Linux VPS/public IPv4, controlled DNS names, Docker Engine + Compose v2.
- Public HTTPS name for the API; public TURN address and reachable relay ports.
- Firebase project + Android app (`com.voz.calling`), Android client configuration,
  and a Firebase Admin service account permitted to send FCM messages.
- JDK 17, Android SDK 34/build tools/platform tools and two physical Android phones
  with microphones/Google Play services on different networks for acceptance tests.

None of these external services/credentials/devices has been fabricated by this
workspace. Without Firebase, keep both apps open for foreground signaling testing.
Without reachable TURN, readiness is not proof of audio; do not announce activation.

## Production installation

1. Point `voice.yourdomain` and `turn.yourdomain` A records to the VPS. Do not publish
   an AAAA record unless IPv6 routing, relay and firewall have been tested.
2. In `server/`, copy `.env.example` to `.env`. Generate DIFFERENT secrets using
   `openssl rand -hex 32` for POSTGRES_PASSWORD, TURN_SECRET and optionally
   METRICS_TOKEN. Hex passwords are URL-safe; otherwise percent-encode DB URL parts.
   Set VOZ_HOST, TURN_HOST, TURN_PUBLIC_IP, TURN_URLS and STUN_URLS to real values.
   Protect `.env` with mode 600. Never paste secrets into chat or commit them.
3. Firewall (and cloud security group): TCP 80/443 for Caddy; UDP+TCP 3478 for TURN;
   UDP 49160–49200 for relay allocation. TCP 5349 additionally for TURN/TLS. Do not
   expose PostgreSQL 5432 or API 8080. TURN uses host networking on Linux.
4. On direct-public-IP hosts set TURN_PUBLIC_IP to that IP. Behind NAT, coturn needs
   correct public/private mapping (`--external-ip=PUBLIC/PRIVATE`) and equivalent
   port forwarding; inspect interface/address selection before production.
5. Start base services:

   ```sh
   docker compose config --quiet
   docker compose up -d --build
   docker compose ps
   curl --fail https://voice.yourdomain/live
   curl --fail https://voice.yourdomain/ready
   ```

   Caddy obtains/renews HTTPS certificates and proxies WSS upgrades. DB migration
   runs automatically under an advisory lock; schema version lives in
   `schema_migrations`. DB and TLS volumes survive container replacement.
6. Pin tested container image digests and configure CPU/memory/disk quotas and
   host security updates for your environment. One API instance only. The service
   is not yet a multi-replica/shared-presence deployment.

### TURN/TLS fallback

Base compose enables TURN/UDP and TURN/TCP; WebRTC media is DTLS-SRTP regardless.
For networks requiring TLS, provision an automatically renewable, trusted certificate
for **TURN_HOST** outside Git; copy/symlink its full chain and key into TURN_CERT_DIR.
Ensure the container runtime user can read the private key without world access.
Add to `.env`:

```dotenv
TURN_CERT_DIR=/absolute/protected/path/to/turn-certificate
TURN_URLS=turn:turn.yourdomain:3478?transport=udp,turn:turn.yourdomain:3478?transport=tcp,turns:turn.yourdomain:5349?transport=tcp
```

```sh
docker compose -f compose.yaml -f compose.turn-tls.yaml up -d
```

Mounts are read-only. `turn-entrypoint.sh` writes a mode-600 temporary secret config,
not a secret command-line argument. Restart the TURN container after cert renewal
so it loads the new chain; coordinate active calls. Do not try to put TURN/TLS on
the same IP:443 already used by Caddy without a suitable multiplexer/second IP.
Review denied IPv4/IPv6 private-peer ranges and reject loopback/internal allocations;
verify them on your host. Keep allocation/user quotas and bandwidth cost alerts.

## FCM setup

1. Create/select a Firebase project; register Android package `com.voz.calling`.
   Enable FCM HTTP v1. In Project settings → Cloud Messaging / service accounts,
   create a narrowly scoped service account with FCM send permission. Obtain its
   JSON **outside this repository**, e.g. `/srv/voz-secrets/firebase-admin.json`,
   protect owner/group permissions, and never commit it or put its content in .env.
2. Put only its absolute path in `.env` as `FCM_CREDENTIALS_PATH`. Start the override:

   ```sh
   docker compose -f compose.yaml -f compose.fcm.yaml up -d --build
   ```

   Combine `-f compose.turn-tls.yaml` too when using TURN TLS. Use the same file set
   for subsequent commands. ADC reads the mounted file; no service-account material
   is sent to Android. Test that the chosen container user can read the mounted file.
3. Read Firebase Android app configuration and place the following **client** values
   in untracked `~/.gradle/gradle.properties` on the build host:

   ```properties
   VOZ_SERVICE_URL=https://voice.yourdomain
   FCM_APPLICATION_ID=1:YOUR_PROJECT_NUMBER:android:YOUR_APP_HASH
   FCM_API_KEY=YOUR_ANDROID_FIREBASE_API_KEY
   FCM_PROJECT_ID=your-firebase-project-id
   FCM_SENDER_ID=YOUR_PROJECT_NUMBER
   ```

   These are Firebase client identifiers/API key, not the Admin private key. Apply
   appropriate Google API/package/SHA certificate restrictions and verify they do
   not prevent FCM registration. Manual FirebaseOptions initialization is used;
   the Google services Gradle plugin/committed google-services.json is not required.
4. Build/install with JDK 17/SDK 34:

   ```sh
   ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

5. Open Tools → VOZ online; create an account with username, strong password and
   mandatory display name. The authenticated identity endpoint allocates its ID.
   Sign in on both phones; device/token registration happens automatically. Permit
   notifications/microphone as requested. On Android 14+, enable full-screen call
   intent access if desired. Grant overlay permission separately for system island.
6. App backgrounded/idle has no permanent WS. FCM sends high-priority data; a short
   incoming-call service verifies the invitation. A user answer/consent starts mic
   FGS. OnNewToken refreshes registry. Token revocation/error clears invalid tokens.
   Force-stop prevents normal FCM delivery until the user launches again. Vendor
   background policies may require explicit supported settings, not bypasses.

## Monitoring and operational hygiene

- `/live`: process; `/ready`: DB + TURN configuration, not a live relay/FCM test.
- `/metrics` requires separate METRICS_TOKEN and returns aggregate JSON counts.
  Poll over TLS using a protected monitoring secret. Alert on DB errors, failed
  readiness, rising push failures/unanswered calls, WS reconnects, CPU/disk and TURN
  bandwidth. No user SDP/IP/caller IDs in application metric labels.
- API logs are structured route-pattern/status/latency logs. Do not enable raw body,
  bearer, Firebase token, password, SDP or ICE logging. Restrict coturn/host logs and
  set retention; network infrastructure can still see peer addresses.
- DB retention worker: ended media events purge after 1h, ended call records after
  30d, expired sessions removed. Configure legal/privacy policy and backup retention
  consistently. Rotate signing keys, TURN secret, Firebase credentials and admin
  access through a documented maintenance procedure. TURN rotation can affect new
  allocations during existing calls; coordinate rollout rather than blindly rotate.
- Default deployment uses a single private PostgreSQL server. Use TLS PostgreSQL
  connections and least-privilege runtime/migration roles when separating hosts.
  No password-recovery or moderation service is implemented; do not launch open
  public registration without an abuse/support and account-recovery plan.

## Backup and restore

Use encrypted off-host backups with restricted access and tested retention.
Example logical backup (from `server/`; path must be outside Git):

```sh
umask 077
mkdir -p /srv/voz-backups
docker compose exec -T db pg_dump -U voz -d voz -Fc > /srv/voz-backups/voz-YYYYMMDD.dump
```

Test restore **to a different database**, never overwrite live data during a test:

```sh
docker compose exec -T db createdb -U voz voz_restore_check
docker compose exec -T db pg_restore -U voz -d voz_restore_check --no-owner < /srv/voz-backups/voz-YYYYMMDD.dump
```

Check accounts, constraints and schema version, and run the test app against an
isolated restored deployment. Revoke restored sessions before an incident rollback;
old refresh tokens and old call invitations must not be resurrected as active.
For real recovery, stop API traffic, restore a known-good DB into a new volume/DB,
end nonterminal calls, revoke sessions, switch DATABASE_URL, check migrations and
health, then reopen traffic. Record downtime/RPO/RTO. Restored backups contain
sensitive account/push/session hashes and must honor deletion/retention obligations.

## Upgrade and rollback

1. Tag/pin tested code/images and take/validate a backup. Run backend tests, Android
   build and device acceptance first. Drain calls or announce maintenance.
2. Build new API image; start it. Migrations are forward-only and serialized. Schema
   001 is the first PostgreSQL schema; there is no automatic import of the old
   SQLite prototype. Do not point this API at that SQLite file.
3. Check health, auth/identity, two-device audio, push, TURN allocation and metrics.
4. Application rollback is safe only if the previous image supports the current
   schema. Otherwise restore the pre-upgrade DB into an isolated volume, revoke
   sessions/end calls, and switch both image and DB during maintenance. Never
   downgrade PostgreSQL binaries against an existing newer-version data directory.
5. Keep Android endpoint/protocol compatibility; use a staged rollout. The API is
   versioned `/v1`; don't deploy incompatible event changes without a migration plan.

## Troubleshooting

| Symptom | Checks |
|---|---|
| Service unavailable in app | HTTPS VOZ_SERVICE_URL Gradle property, DNS/cert chain, `/ready`; no cleartext fallback |
| Login/refresh rejected | Correct username (not VOZ ID), password; refresh replay/session revocation → sign in again |
| Unavailable recipient | Registered identity/device, live foreground socket or valid FCM token; Admin credentials/send failures |
| Ringing but no audio | Mic consent/permission, actual WebRTC callbacks, TURN public IP, UDP relay range, NAT mapping, certificate chain |
| Works on Wi-Fi, not mobile | Force-relay build, TURN/TCP and TURN/TLS reachability, carrier firewall; do not rely on LAN-only tests |
| Background call missing | Same Firebase project, app token, GMS, notification permission, high-priority delivery, expired TTL, force-stop/OEM restrictions |
| Old invitation | Backend state/expiry always wins; don't accept cached push data |
| Interrupted call on reconnect | Re-auth/refresh, seq replay, 90s signaling lease, selected device/generation; restart negotiation after process recovery |
| Repeated 429/503 | Per-IP rate limits/shared NAT; KDF concurrency, DB health; don't disable limits for public launch |
| Fullscreen/island absent | Overlay/full-screen/notification permissions and OS policy; notification is the supported fallback |
