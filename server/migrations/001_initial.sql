CREATE TABLE accounts (
 id uuid PRIMARY KEY, username text NOT NULL UNIQUE CHECK(username ~ '^[a-z0-9_]{3,40}$'),
 password_hash text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE identities (
 account_id uuid PRIMARY KEY REFERENCES accounts ON DELETE CASCADE,
 voz_id text NOT NULL UNIQUE CHECK(voz_id ~ '^VOZ-[0-9]{3}-[0-9]{4}$'),
 display_name text NOT NULL CHECK(length(display_name) BETWEEN 2 AND 50)
);
CREATE TABLE devices (
 id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES accounts ON DELETE CASCADE,
 installation_id uuid NOT NULL, name text NOT NULL, push_token text,
 last_seen timestamptz NOT NULL DEFAULT now(), revoked_at timestamptz,
 UNIQUE(account_id, installation_id), UNIQUE(id, account_id)
);
CREATE UNIQUE INDEX unique_push_token ON devices(push_token) WHERE push_token IS NOT NULL AND revoked_at IS NULL;
CREATE TABLE sessions (
 id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES accounts ON DELETE CASCADE,
 device_id uuid, access_hash text NOT NULL UNIQUE, refresh_hash text NOT NULL UNIQUE,
 access_expires timestamptz NOT NULL, expires timestamptz NOT NULL, revoked_at timestamptz,
 FOREIGN KEY(device_id, account_id) REFERENCES devices(id, account_id) ON DELETE CASCADE
);
CREATE TABLE consumed_refresh (
 token_hash text PRIMARY KEY, session_id uuid NOT NULL REFERENCES sessions ON DELETE CASCADE,
 consumed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE calls (
 id uuid PRIMARY KEY, from_account uuid NOT NULL REFERENCES accounts ON DELETE CASCADE,
 from_device uuid NOT NULL REFERENCES devices ON DELETE CASCADE,
 to_account uuid NOT NULL REFERENCES accounts ON DELETE CASCADE,
 to_device uuid REFERENCES devices ON DELETE SET NULL,
 state text NOT NULL CHECK(state IN ('ringing','accepted','ended')),
 reason text, created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL, accepted_at timestamptz, ended_at timestamptz,
 caller_seen timestamptz NOT NULL DEFAULT now(), callee_seen timestamptz NOT NULL DEFAULT now(),
 revision integer NOT NULL DEFAULT 1, CHECK(from_account <> to_account)
);
CREATE INDEX call_expiry ON calls(expires_at) WHERE state <> 'ended';
-- A user may belong to at most ONE nonterminal call, across all devices and API instances.
CREATE TABLE active_call_members (
 account_id uuid PRIMARY KEY REFERENCES accounts ON DELETE CASCADE,
 call_id uuid NOT NULL REFERENCES calls ON DELETE CASCADE
);
CREATE TABLE call_events (
 seq bigserial PRIMARY KEY, call_id uuid NOT NULL REFERENCES calls ON DELETE CASCADE,
 actor uuid REFERENCES accounts ON DELETE CASCADE, actor_device uuid REFERENCES devices ON DELETE CASCADE,
 event_id uuid NOT NULL, type text NOT NULL, payload jsonb NOT NULL DEFAULT '{}',
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(call_id, actor_device, event_id)
);
CREATE INDEX call_events_replay ON call_events(call_id, seq);
CREATE TABLE call_requests (
 account_id uuid NOT NULL REFERENCES accounts ON DELETE CASCADE,
 request_id uuid NOT NULL, call_id uuid NOT NULL REFERENCES calls ON DELETE CASCADE,
 target text NOT NULL, PRIMARY KEY(account_id, request_id)
);
CREATE TABLE push_outbox (
 id bigserial PRIMARY KEY, call_id uuid NOT NULL REFERENCES calls ON DELETE CASCADE,
 device_id uuid NOT NULL REFERENCES devices ON DELETE CASCADE,
 attempts integer NOT NULL DEFAULT 0, next_attempt timestamptz NOT NULL DEFAULT now(),
 delivered_at timestamptz, failed_at timestamptz, UNIQUE(call_id, device_id)
);
