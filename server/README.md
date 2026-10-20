# VOZ internet calling service

**Architecture:** TypeScript + Fastify, PostgreSQL, authenticated WebSockets,
Firebase Admin high-priority data messages, coturn, native Android WebRTC. The old
SQLite/browser/WebView service has been replaced; its API/database are not compatible.
There is no SIM fallback, local ID activation, server-side audio proxy or fake timer.

## Status and evidence

Backend TypeScript builds. Security/capability/CI-diagnostic unit tests and a real
PostgreSQL suite with twelve integration subtests pass. Production npm audit reports no known
vulnerabilities in the locked dependencies at verification time.

Native Android registration, encrypted session storage, device registration, audio
negotiation, reconnect/replay and FCM handling are implemented. Debug assembly and
JVM tests now pass on authorized hosted GitHub CI; real-device media/push remains
unverified. No public server, TURN service, Firebase project or two-device call
was fabricated. No owner setup is requested; see the
[autonomous capability audit](../docs/AUTONOMOUS_DELIVERY.md). See [verification](../docs/ONLINE_VERIFICATION.md).

## Automated CI and local developer verification

Branch pushes run backend build/tests/audit and Android compilation/tests.
`npm run autonomy:preflight` provides an offline, secret-redacted capability check
(exit 2 correctly means blocked, not a deployed server). The following commands
are developer reproduction references, not an owner deployment assignment.

### Backend

Node 22.13+:

```sh
cd server
npm ci --ignore-scripts
npm run build
npm test
# This package's reviewed script restores symlinks for the embedded PG test binary.
npm rebuild @embedded-postgres/linux-x64
npm run test:db
```

`test:db` starts a temporary **real PostgreSQL** server and deletes its cluster on
exit. No mock/in-memory SQL substitutes. Run as an ordinary user, not root. Other
supported platforms can rebuild their corresponding embedded-postgres package, or
set `TEST_DATABASE_URL` to a **disposable** PostgreSQL 18 database. Tests create
accounts/calls; never point them at production.

Docker local stack (not a phone/public-media deployment):

```sh
docker compose -f compose.local.yaml up -d --build
curl --fail http://127.0.0.1:8080/live
curl --fail http://127.0.0.1:8080/ready
# Stop without deleting data:
docker compose -f compose.local.yaml down
```

The local TURN address is a configuration placeholder: `/ready` checks configured
credentials and DB, not real relay reachability. For actual phone audio, deploy
reachable coturn + HTTPS using the guides below.

## Guides

- [Architecture, trust and limits](../docs/ONLINE_ARCHITECTURE.md)
- [API and signaling schema](../docs/ONLINE_API.md)
- [Deployment, FCM, firewall, backup, upgrade/rollback, troubleshooting](../docs/ONLINE_OPERATIONS.md)
- [End-to-end device test and release checklist](../docs/ONLINE_VERIFICATION.md)

Migrations run under a PostgreSQL advisory lock before API startup. API image is
non-root and read-only; PostgreSQL owns persistent state. Use one API instance:
fanout/presence is local; database state is transactional and survives restarts.
Do not scale replicas without adding a shared pub/sub/presence implementation.
