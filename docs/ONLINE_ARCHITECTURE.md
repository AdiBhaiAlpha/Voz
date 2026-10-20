# Online calling architecture

## Decision

Use the requested TypeScript/Fastify/PostgreSQL stack. It integrates with the
existing Kotlin/Compose app through HTTPS/WSS without replacing Android Telecom's
cellular dialer. `io.github.webrtc-sdk:android:150.7871.01` supplies native `org.webrtc`
audio; `firebase-messaging` handles push. No WebView/audio JavaScript path remains.
The WebRTC dependency is pinned; its upstream Android SDK README was inspected.

```
Android A ── HTTPS/WSS ── Fastify ── PostgreSQL (identity/session/call/event/outbox)
                               └── Firebase Admin → FCM → Android B
Android A ═══ DTLS-SRTP audio (direct ICE or coturn relay) ═══ Android B
```

Application API never forwards audio. STUN discovers addresses; authenticated TURN
relays encrypted packets only when needed. Set `VOZ_FORCE_RELAY=true` in a diagnostic
Android build to test the relay independently of direct connectivity.

## Identity and authentication

1. Signup creates a username/password account and returns an authenticated session.
2. Authenticated `/identity` allocates a random seven-digit ID; clients cannot choose
   IDs. Display name is trimmed, 2–50 chars, visible letters/numbers, no control or
   format characters. Account lock and unique PostgreSQL constraints prevent races.
3. Access token: random 256-bit opaque bearer, hashed in DB, 15 minutes. Refresh token:
   random opaque bearer, rotating, 30-day session. Consumed refresh-token replay
   revokes the session. Never retry a consumed refresh token after a lost response;
   re-authenticate if rotation cannot be recovered.
4. Register each installation. Server device UUID is account-bound; access sessions
   are bound to one registered installation. Android persists tokens using AES-GCM
   and a non-exportable Android Keystore key, excluded from backup. Passwords are
   salted scrypt, with bounded KDF concurrency; no plaintext password persistence.
5. Logout revokes session and detaches that device's push token; device revocation
   revokes associated sessions; deleting an account revokes calls and cascades
   identity/session/device state. Username login is used, not the public VOZ ID.

## Authoritative calls

- PostgreSQL owns `ringing → accepted → ended`. Reject/cancel/timeout/unavailable are
  terminal reasons. Ring TTL is 30 seconds; max accepted-call duration is four hours.
- Account rows lock in stable order; `active_call_members.account_id` is a primary key.
  Each account has at most one nonterminal call across devices/concurrent requests.
- First eligible recipient device accepting the row lock wins. Only that device and
  the original caller device may send SDP/ICE or end an accepted call.
- Create `requestId` and event `eventId` are UUID idempotency keys. Reusing an event
  with different action/payload fails. Valid retries do not duplicate transitions.
- Caller alone offers/restarts; recipient alone answers. Media carries generation
  (call revision). Old generations, duplicate offers/answers and pre-accept SDP fail.
  Recipient can request a caller-driven restart, avoiding offer glare.
- Durable ordered events support `after=seq` replay. Native client replays on each
  reconnect and processes media sequentially, buffering ICE until remote SDP exists.
- Socket loss does not immediately end a call. Selected-device heartbeats renew
  signaling leases; a missing lease for 90 seconds ends the call and frees slots.
  Call expiry runs independently of potentially slow push delivery.

## Incoming delivery and Android lifecycle

- Call creation atomically records per-device push-outbox jobs. FCM high-priority
  data contains only call UUID, kind and expiry. No SDP, bearer or caller-supplied ID.
- Android starts a bounded incoming-call foreground service, immediately posts a
  provisional notification, then authenticates and fetches the invitation. No mic
  is acquired from FCM. Stale/forged/non-recipient invitations cannot be accepted.
- Answer opens the native Activity. Explicit user consent and runtime microphone
  permission precede media capture. Only then does the service add microphone FGS
  type. Valid accepted calls continue while the Activity backgrounds.
- Idle background WebSockets are closed; incoming calls rely on FCM. Ringing and
  accepted calls keep a foreground service and transient signaling transport.
- Notification/full-screen policies respect platform permissions. Locked/screen-off
  can use full screen; interactive unlocked use island/notification. Denied overlay
  falls back to Android notification. If FGS start is disallowed, a short-lived tap-
  to-verify alert is attempted; notification denial can still prevent visible alerts.
- FCM unavailable: online foreground WebSocket reception remains possible. No live
  socket and no configured push/token → explicit unavailable. Repeated known push
  failure → terminal unavailable; accepted-by-FCM but unanswered → 30-second expiry.
- Force-stopped apps cannot be reliably awakened by FCM; user must launch again.
  OS/vendor restrictions, missing Play services and notification permissions must
  be tested and disclosed. No promise of bypassing Doze or force-stop policies.

## Connection truth and security boundaries

Native UI shows Connecting until actual `PeerConnectionState.CONNECTED`; accepted
signaling is not connected media. Disconnected shows Reconnecting; a 30-second audio
connection deadline ends failed attempts. No microphone before consent, no SIM
fallback, no fake connected timer. WebRTC supplies packet-loss adaptation, Opus,
AEC/NS and encrypted media; network changes can trigger generation-checked ICE
restarts with refreshed TURN credentials (ten-minute TTL).

Rate limits cover signup, login, lookup, starts, HTTP payload size and WS frames.
Access is rechecked before commands, media delivery and leases. Logs use route
patterns/status/latency, not raw IDs, credentials, SDP, ICE addresses or FCM tokens.
Aggregate metrics require a separate operator token. SDP/ICE is participant-only,
retained during a call and one hour after end; call summaries retain 30 days.
Account deletion cascades owned data. PostgreSQL backups need independent retention
and deletion policy; deleting a live row does not erase old offline backups.

## Deliberate operational limits

One API instance; no shared fanout/presence yet. No password reset/verified-email
recovery, contact discovery, user block/report/moderation UI, large-scale abuse
scoring or multi-region HA. These are release-hardening work, not simulated features.
Default TURN sample needs public addressing/firewall/cert configuration. It is not
proof of relay connectivity. Images need digest pinning/scanning in a real release.
