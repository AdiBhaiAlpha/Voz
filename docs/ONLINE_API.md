# HTTP API and signaling v1

All production traffic is HTTPS/WSS. JSON, strict input schemas; unknown fields
are rejected. Authentication: `Authorization: Bearer <accessToken>`. UUID fields
must be canonical valid UUIDs. Errors are `{ "error": "MACHINE_CODE" }`; no stack or
secret data. 400 invalid input, 401 auth, 403 wrong authority/device, 404 unknown or
inaccessible identity/call, 409 transition/busy/idempotency conflict, 429 rate limit,
503 auth capacity/TURN unavailable. Never interpret an error as a cellular fallback.

## Accounts, sessions, devices

| Method/path | JSON / response |
|---|---|
| POST `/v1/auth/signup` | `{username,password}` → `{accessToken,refreshToken,expiresIn:900}` |
| POST `/v1/auth/login` | Same; username `[a-z0-9_]{3,40}`, password 12–128 chars |
| POST `/v1/auth/refresh` | `{refreshToken}` → replacement token pair; serialize rotations |
| POST `/v1/identity` | Auth required, `{displayName}` → `{account_id,voz_id,display_name}`; existing ID is stable |
| GET `/v1/me` | `{accountId,deviceId,identity:{voz_id,display_name}|null}` |
| POST `/v1/devices` | `{installationId:UUID,name,pushToken?:string|null}` → server `{id}`; idempotent register/refresh |
| GET `/v1/devices` | Account-owned device IDs/names/last_seen/revoked_at, no tokens |
| DELETE `/v1/devices/:id` | Revoke own device/sessions/calls |
| POST `/v1/auth/logout` | Revoke current session; detach push and end device's call |
| DELETE `/v1/account` | Revoke/delete authenticated account and owned identity/state |
| GET `/v1/recipients/VOZ-302-8671` | Verified `{voz_id,display_name}`; limited lookup, not a credential |
| GET `/v1/ice` | `{expiresAt:unixSeconds,iceServers:[{urls:[...],username?,credential?}]}` |

Omit `pushToken` to retain an existing token; `null` explicitly clears it. One
installation ID per account maps to one device. Another account cannot take an
active token by simply submitting it. Logout the old account before reassigning
that installation/token. A new login must register its device before calling/WS.

TURN username is `<expiry>:<account UUID>`, password Base64 HMAC-SHA1 over username
with server-only TURN secret. TTL 600s. Android requests fresh ICE config on restart.
STUN entries carry no credential. Do not persist credentials or publish them in logs.

## Calls

POST `/v1/calls`:

```json
{"target":"VOZ-302-8671","requestId":"00000000-0000-4000-8000-000000000001"}
```

Response `{call,duplicate}`. `call` has server UUID `id`, from/to account/device IDs,
`state`, `revision`, timestamps/expiry and terminal `reason`. GET `/v1/calls/:id`
adds verified caller/recipient names and IDs, `server_time` and `unexpired`; only
participants may access it. GET `/v1/calls/active` returns `{call:null|object}`.

POST `/v1/calls/:id/events` returns `{call,duplicate}`:

```json
{"eventId":"00000000-0000-4000-8000-000000000002","action":"accept","payload":{}}
```

| Action | Authority / payload |
|---|---|
| `accept` | Recipient registered device, ringing only; winning device is pinned |
| `reject` | Recipient, ringing only |
| `cancel` | Original caller device, ringing only |
| `end` | Selected participant device once accepted; participant ringing rules also apply |
| `offer` | Caller device, accepted; `{generation:revision,sdp:string}` |
| `answer` | Winning recipient device; same generation and an existing offer required |
| `ice` | Selected devices; `{generation,candidate,sdpMid:string|null,sdpMLineIndex:int}` |
| `restart` | Caller device; empty payload, increments revision; then send new offer |
| `restart_request` | Selected recipient device; empty payload, asks caller to restart |

Each new action has a fresh event UUID; retries reuse the same UUID and payload.
Retry network-lost create requests with the same requestId/target. Never retry auth
refresh blindly: consumed-token replay deliberately revokes its session. `accepted`
means the recipient accepted, **not that encrypted audio is connected**.

## WSS `/v1/signal`

First frame within 5s (never put credentials in a query string):

```json
{"type":"auth","accessToken":"<opaque token>"}
```

Response `{"type":"ready"}`. One live socket per session; reconnect replaces the
old one. Browser Origin, when present, must match PUBLIC_ORIGIN; native has none.

Live events:

```json
{"type":"call.event","seq":"123","call":{"id":"...","state":"accepted","revision":2},"action":"offer","payload":{"generation":2,"sdp":"..."}}
```

`call` above is abbreviated; actual snapshot includes participant IDs/timestamps.
Media events go only to selected devices. `incoming` goes to recipient devices and
caller origin. State events also dismiss invitations on other account devices.
Server-generated actions: `incoming`, `timeout`, `delivery_failed`, revocation `end`.

Commands may also be sent over WS:

```json
{"type":"command","callId":"<UUID>","command":{"eventId":"<UUID>","action":"accept","payload":{}}}
```

Server replies `{"type":"ack","eventId":"...","call":{},"duplicate":false}` or
`{"type":"error","error":"..."}`. Native uses HTTPS command submission and WS event
wakeups; both transports call the **same transactional state machine**.

### Replay/reconnect

GET `/v1/calls/:id/events?after=123` → `{call,events:[{seq,type,payload,actor_device}]}`.
Sequence numbers are decimal **strings** (PostgreSQL bigint), monotonically ordered,
500 rows/page; repeat until fewer than 500. Snapshot is the latest authoritative
state; clients must not resurrect old ringing/accepted states from historical events.
Ignore own ICE using `actor_device`; only the selected devices can read media rows.
Reconnect and replay after gaps/restarts; duplicate seq does not reapply effects.
Do not create a peer after process death until the user reconsents. Then request an
ICE restart (or recipient restart_request) to negotiate a fresh generation.

Server sends pings every 25s and validates auth on pongs. For active calls, send
`{"type":"heartbeat","callId":"<UUID>"}` every 20s to renew the selected-device lease;
an idle socket does not renew a locally ended call. Access expiry causes close/re-authentication; refresh then reconnect/replay.
90s without a participant lease ends the accepted call. A live signaling socket is
not an indication that audio works.

## Push and monitoring

FCM high-priority Android data:

```json
{"kind":"voz.incoming","callId":"<server UUID>","expiresAt":"<UTC ISO timestamp>"}
```

Treat data as a hint only. Authenticate/fetch the call, check recipient/state/expiry,
then show verified controls; accept is always a backend transition. Collapse key is
call ID; duplicate delivery is safe. TTL is remaining invitation life. FCM accepted
send is not proof of phone receipt. Known exhausted delivery failure without a live
recipient socket ends unavailable; otherwise unanswered ends after 30s.

GET `/live`: process alive. GET `/ready`: DB reachable and TURN configured, **not**
a network allocation probe. GET `/metrics` with `Bearer <METRICS_TOKEN>`: JSON
aggregate websocket/call/push counters. Disabled without a configured operator token.
