# Autonomous delivery audit — 20 October 2026

## Outcome

**Public calling is not activated.** No public API/TLS endpoint, durable hosted
identity database, TURN allocation, Firebase project authentication, device push
receipt or two-device voice call has been established. No owner setup is being
requested. No sandbox URL, tunnel, sample identity, demo relay login or CI runner
is being substituted for production infrastructure.

The source implementation is preserved on `arena/d7ba82e8-voz`. Authorized GitHub
access has now been used to push this branch and execute real standard hosted CI.
The branch has **not** been merged into `main`. Production billing has not been
started and no third-party account has been created under someone else's identity.

## Capabilities actually inspected

- GitHub authentication works for `AdiBhaiAlpha`; repository `AdiBhaiAlpha/Voz`
  is public. Branch push and Actions run/job/check-annotation reads work.
- The integration returns HTTP 403 for Actions settings, repository secrets,
  repository variables and webhook listings. This does **not** establish that
  those resources are empty. Secret values were never requested or displayed.
- Repository deployment-environment listing returned no environments.
- Environment-name/presence inventory found GitHub credentials, but no recognized
  cloud-provider tokens, Firebase configuration, authorized SSH target/key,
  service origin or public TURN configuration. A GitHub token is not a general
  cloud OAuth credential and was not submitted to any provider signup endpoint.
- No local Java/JDK, Docker runtime or adb is available. GitHub's standard hosted
  Ubuntu runner supplies a legitimate Android compilation route. No physical
  Android devices are exposed here; a successful build would not prove a call.

`server/scripts/autonomy.mjs` produces a secret-redacted, offline JSON inventory
and exits 2 when capabilities are missing. It makes no network requests or
prompts. It rejects local/example/private/CGNAT addresses, common tunnel/sandbox
hosts and static Pages URLs as production configuration. Even complete input
configuration returns `configured_unverified`, never `productionReady: true`.
These are conservative syntax/capability checks, not a claim of DNS ownership,
credential validity, deployment, device availability or verified media.

## Free infrastructure research

These are official documentation findings checked during this session, not
promises that a free tier is production-safe or available without authorization.
Provider rules may change. No payment method, signup verification or account was
fabricated to get around a barrier.

| Route | Finding | Why it cannot autonomously complete this deployment here |
|---|---|---|
| Standard GitHub Actions | Standard hosted runners are free for public repositories. [1](https://docs.github.com/en/actions/concepts/billing-and-usage) Actions use is bounded and must remain within applicable development/testing terms. [2](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features) | Used for genuine builds/tests, not permanent signaling/TURN hosting. |
| GitHub Pages | Static site hosting with documented usage restrictions. [3](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits) | Cannot execute this authenticated Fastify/PostgreSQL/WebSocket/coturn stack or Android wake delivery. |
| Render free | Idle service sleeps after 15 minutes; free PostgreSQL expires after 30 days; free services are explicitly not intended for production. [1](https://render.com/docs/free) | Not a durable free identity database or reliable incoming-call service; no Render authorization present. |
| Oracle Always Free | Signup normally requires identity/contact/mobile/payment verification; prepaid/virtual/single-use cards are not accepted. [1](https://www.oracle.com/cloud/free/faq/) Free compute may be capacity-limited and idle resources reclaimed. [2](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) | No existing OCI authorization/account/key in this environment; cannot fabricate verification. Old claims of a universally available 4-OCPU/24-GB free VM must not be assumed. |
| Cloudflare Workers + Durable Objects/D1 | Workers have free request/CPU limits; SQLite Durable Objects and D1 offer bounded free storage. [1](https://developers.cloudflare.com/workers/platform/limits/) [2](https://developers.cloudflare.com/durable-objects/platform/limits/) [3](https://developers.cloudflare.com/d1/platform/limits/) | Requires an account/token and a backend port, not a drop-in PostgreSQL/coturn host. No Cloudflare authorization present. |
| Cloudflare TURN | Credentials are generated using a server-side TURN key ID/API token; key creation requires an account-scoped API token with Calls Write. [1](https://developers.cloudflare.com/realtime/turn/generate-credentials/) [3](https://developers.cloudflare.com/api/resources/calls/subresources/turn/methods/create/) Realtime has a shared free allowance before usage charges. [1](https://developers.cloudflare.com/realtime/sfu/pricing/) | No authorized account/token/key. Free public STUN does not supply authenticated TURN allocations or incoming wakeup. No paid-overage exposure was enabled. |
| Railway free plan | A one-time $5/30-day trial becomes $1/month free credit; full trial networking depends on account verification. [1](https://docs.railway.com/pricing/free-trial) | No Railway account/token or authorized OAuth session. The credit is not a guarantee of this entire always-on stack at zero cost. |
| Railway accountless VM | Officially offers a 60-minute build window, 24-hour claim window and deletion of unclaimed boxes/files. [2](https://docs.railway.com/pricing/free-trial) | A genuine accountless option, but explicitly temporary. Not provisioned or mislabeled as a durable deployment. |
| Koyeb | Official free instance docs say hobby/testing rather than production, no volumes, and mandatory sleep after an hour. [6](https://www.koyeb.com/docs/reference/instances) Account creation involves verified signup. [16](https://www.koyeb.com/docs/reference/accounts) | No account/token. Current pricing/onboarding reports conflict, so old card-free signup claims are not treated as guaranteed; sleep/persistence restrictions independently prevent selection. |
| Hugging Face Spaces | Current docs distinguish free static Spaces from compute Spaces requiring a paid plan; basic disks are nonpersistent and free hardware sleeps. [1](https://huggingface.co/docs/hub/spaces-overview) [3](https://huggingface.co/docs/huggingface_hub/en/guides/manage-spaces) | No account/token; not selected as durable identity/TURN hosting. Older tutorials promising unconditional free Docker hosting are insufficient. |
| Metered Open Relay / signaling | Official material advertises free TURN/signaling allowances and account/key signup; current credential API requires an API key. [1](https://www.metered.ca/tools/openrelay/) [3](https://www.metered.ca/tools/openrelay/webrtc-signaling-server/) [16](https://www.metered.ca/docs/turn-rest-api/get-credential/) Public static-auth examples also exist. [1](https://www.metered.ca/tools/openrelay/) | No authorized app key/account; public examples were not silently embedded or claimed tested. Even a working public relay would not provide VOZ's authenticated durable identity registry, authoritative call state or Android wake service. Relay reachability/allocation could not be tested from this restricted network. |
| Firebase Cloud Messaging | FCM itself is no-cost, but sending uses Firebase project OAuth/service-account/ADC authorization and device registration tokens. [1](https://firebase.google.com/products/cloud-messaging) [2](https://firebase.google.com/docs/cloud-messaging/send/v1-api) | No Firebase project/client config, readable authorized service-account file or real target device token. GitHub credentials cannot substitute. |

This is a bounded survey of plausible routes, not a claim to have searched every
provider in existence. The checkout's outbound network is restricted to GitHub
and package registries; documentation was researched with the search tool. No
provider signup/deployment API success or TURN allocation is being inferred from
search snippets. Existing authorized durable infrastructure was not discovered.

## What is automated instead of handed to the owner

- Branch push triggers backend build, unit tests, real PostgreSQL integration
  tests and production-dependency audit on a standard public-repository runner.
- Android branch CI compiles a debug APK, runs JVM tests and lint, and preserves
  available reports/APK artifacts; permissions are read-only. Compiler failures
  are surfaced as API-readable check annotations so they can be investigated
  despite this workspace's inability to follow Actions log-storage redirects.
- Android CI uses a pinned Ubuntu 24.04 runner with its installed SDK rather than
  depending on the moving `ubuntu-latest` label or a redundant SDK bootstrap.
- Existing backend startup applies transactional schema migrations, and Compose
  wires the database, HTTPS proxy, signaling process and short-lived coturn
  authentication. This is deployment automation source, **not an executed public
  deployment**. No production secrets were generated without an authorized target.
- Capability checks and their negative/security cases run in the test suite;
  secret values, paths and origin values are not echoed into the report.

## Unavoidable external gates, not owner assignments

Durable, authorized public compute/database/relay access and authenticated Android
wake credentials are absent. There is also no physical two-device testing access.
These gates remain blocked; this report does not ask the owner to sign up, supply
keys, buy hosting, configure DNS, or manually deploy. It would be dishonest to
mark them complete by publishing an ephemeral preview or substituting local-only
registration/calls. A green CI result does not change these gates.

Live build evidence and latest limitations are recorded in
[ONLINE_VERIFICATION.md](ONLINE_VERIFICATION.md).
