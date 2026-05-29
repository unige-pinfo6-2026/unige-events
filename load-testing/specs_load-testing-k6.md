# Load Testing Spec — UNIGE Events (Grafana k6, remote target)

> **Status:** spec / implementation guide. When an agent is later pointed at this file, it must be
> able to write **and** run the k6 scripts from this document alone.
> **Branch:** `perf/k6-load-testing`. **Do not** modify `openapi/openapi.yaml`.
> **Language of all deliverables:** English.
>
> **✅ Target confirmed (2026-05-29): production `https://pinfo6.p-info.net` (API `…/api`).**
> A preview/staging env stays the safer choice for write-heavy/stress runs (§3); since prod is the
> graded **live** site, the §4 safety rules (coordinated window, read-heavy bias, `[LOADTEST]`
> tagging, `teardown()` cleanup) are **mandatory**, and stress/spike runs should be kept conservative.

---

## 1. Objective

Measure the performance and resilience of the UNIGE Events platform under a realistic,
**multi-service integration** workload of **50–200 concurrent virtual users (VUs)**, generated with
**Grafana k6**, run **against the remote deployment** from a single load-generator machine
(`unige-debian`). The test must:

- Exercise a realistic end-user journey that crosses **all five microservices** (event, user,
  engagement, moderation, notification) — not a single-endpoint micro-benchmark.
- Produce actionable SLO verdicts (latency percentiles, error rate excluding expected throttling,
  check pass-rate) and per-endpoint / per-service breakdowns.
- Surface the real bottlenecks of the deployment topology (single-pod Cloudflare tunnel, single-replica
  Kong + services), and the behaviour of the two-stage rate limiting.

Out of scope: fixing what the test finds; testing internal service-to-service endpoints (not routed by
Kong — see §8); UI/browser testing.

---

## 2. Machine roles & network chain

```
unige-debian (LOAD GENERATOR, k6)          ← the ONLY thing we run
        │  public HTTPS
        ▼
Cloudflare edge  (anti-abuse / bot / possible caching)        ← can throttle BEFORE the app
        ▼
cloudflared tunnel  (1 single pod — prod mode "named", preview mode "quick")   ← throughput ceiling
        ▼
Ingress Nginx
        ▼
Kong API Gateway  (replicas: 1 — see helm/values.yaml; DB-less, declarative routes)
        ▼
Quarkus service  (event / user / engagement / moderation / notification — each replicas: 1)
        ▼
per-service PostgreSQL 16   +   Kafka (events → notifications)   +   MinIO (S3, image uploads)
```

**Consequences to keep in the analysis:**
- **Nothing runs locally.** Do **not** start any service with `quarkus:dev`. k6 hits the public URL.
- **WAN + Cloudflare + tunnel latency is added to every response.** Calibrate SLOs against a 1-VU
  smoke baseline, not against localhost numbers.
- The **single-pod cloudflared tunnel** and the **single Kong replica** are likely the first
  saturation points. Part of the test's job is to tell whether we're measuring the app or the edge.
- `event-service` and `moderation-service` are `replicas: 1` **strict** (scheduled-job leader
  constraints) — a stress run can knock them over; watch their health.

---

## 3. Environments

| | Production | Preview / staging |
|---|---|---|
| Base URL | `https://pinfo6.p-info.net` (API `…/api`) | `https://<name>.pinfo6.p-info.net` (cloudflared `quick` tunnel) |
| Helm values | `helm/values.yaml` (`cloudflared.mode: named`, `kong.replicas: 1`, `ingress.host: pinfo6.p-info.net`) | `helm/values-preview.yaml` (`cloudflared.mode: quick`) |
| Risk | **Graded, live site.** Writes pollute real data. | Disposable — preferred for write-heavy / stress runs. |

`BASE_URL` is parameterised in `load-testing/.env` (already set to the prod API). **Confirm prod vs
preview with the requester before running.** For anything beyond a smoke/read test, prefer preview.

---

## 4. Preconditions, authorization & safety (mandatory)

This is the team's own infrastructure, so testing is authorized — **but** the run procedure MUST:

1. **Coordinate a test window** with the team, outside any demo / grading slot.
2. **Warn the Cloudflare account admin** — sustained high traffic can trip Cloudflare anti-abuse and
   flag/throttle the account or the tunnel.
3. **Watch the single-replica services** (`event`, `moderation`) during the run; abort if they crash.
4. **Data pollution is real.** Every write (`POST /events`, `/comments`, `/attend`, `/follow`,
   `/report`, image uploads) creates **real rows** in the remote DBs and triggers **Kafka →
   notification** fan-out. The 50 seeded users are **real accounts** too. Therefore:
   - Prefer a **disposable preview** environment.
   - If prod: **minimize writes**, **tag** all created content (titles/descriptions prefixed
     `[LOADTEST]`), and run the **`teardown()`** cleanup (cancel+delete created events, delete
     comments, unfollow, unfavorite). Cleanup is best-effort; document any residue (e.g. Auth0 users
     and provisioned profiles persist unless deleted via the Management API).
5. **Secrets:** all credentials come from the git-ignored `load-testing/.env` via `__ENV` — never
   hard-code or commit tokens/passwords; never print full tokens in logs.

---

## 5. Technical constraints

- **Grafana k6 only.** k6 is **not installed** on `unige-debian`. Two supported ways to run (see §12):
  - native binary (k6 apt repo), or
  - `docker run --rm -i --env-file load-testing/.env -v "$PWD/load-testing":/work -w /work grafana/k6 run …`.
  - Target is **public HTTPS** → no Docker-network tricks; hit the URL directly.
  - A single machine realistically drives a few hundred VUs; **200 VU is within reach**. For more,
    note k6 distributed / Grafana Cloud as out-of-scope options.
- **Load: 50–200 concurrent VUs**, expressed as k6 `scenarios` with explicit `executor`s (§7).
  Prefer **arrival-rate executors** (`ramping-arrival-rate` / `constant-arrival-rate`) wherever we
  need to **control throughput** — essential against the rate limits and the single-pod tunnel.

---

## 6. Authentication strategy (ALREADY BOOTSTRAPPED — reuse, do not redesign)

The auth bootstrap exists in `load-testing/` and is **verified working** (ROPC → `GET /users/me`
returns 200 against prod):

| File | Role |
|---|---|
| `.env` / `.env.example` | config + secrets (git-ignored `.env`) |
| `seed-users.sh` | creates the pool of 50 Auth0 test users via the Management API |
| `get-token.sh` | ROPC end-to-end smoke test (mints a token for user #1, calls `/users/me`) |
| `README.md` | operator setup steps |

**Mechanics (mirror `get-token.sh` inside k6 `setup()`):**
- Tenant `dev-p8ufbvhr6g61j78w.us.auth0.com`, audience **`https://unige-events/api`**, connection
  `Username-Password-Authentication`.
- Grant: **`password-realm`** (`grant_type=http://auth0.com/oauth/grant-type/password-realm`,
  `realm=${AUTH0_CONNECTION}`, `audience=${AUTH0_AUDIENCE}`, `scope=openid profile email`,
  `client_id=${AUTH0_CLIENT_ID}`, `client_secret=${AUTH0_CLIENT_SECRET}`).
  The token-minting client is a confidential **Regular Web App** (`unige-events-loadtest-ropc`)
  authorized on the API.
- **Mint the whole pool ONCE in `setup()`**, with light throttling (Auth0 `/oauth/token` is itself
  rate-limited — add ~150–300 ms between mints, or mint serially), then **cache** the array of tokens
  and reuse for the entire run. **Never re-mint per iteration.**
- **VU ↔ identity mapping:** assign each VU a fixed identity:
  `const me = tokens[(exec.vu.idInTest - 1) % tokens.length]`. `POOL_SIZE` default **50**; raise to
  **200** (and re-run `seed-users.sh`) for strict one-identity-per-VU at the stress peak.
- **Token TTL vs soak:** Auth0 access tokens are typically valid ~24 h; the soak profile must stay
  within TTL or request `offline_access` and refresh.

**Env vars consumed (from `load-testing/.env`):** `BASE_URL`, `AUTH0_DOMAIN`, `AUTH0_AUDIENCE`,
`AUTH0_CONNECTION`, `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `POOL_SIZE`, `AUTH0_TEST_EMAIL_PREFIX`,
`AUTH0_TEST_EMAIL_DOMAIN`, `AUTH0_TEST_PASSWORD`.

Reference `setup()` token mint (k6):

```javascript
// lib/auth.js
import http from 'k6/http';

export function mintTokenPool() {
  const n = Number(__ENV.POOL_SIZE || 50);
  const prefix = __ENV.AUTH0_TEST_EMAIL_PREFIX || 'loadtest';
  const domain = __ENV.AUTH0_TEST_EMAIL_DOMAIN || 'example.com';
  const tokens = [];
  for (let i = 1; i <= n; i++) {
    const body = {
      grant_type: 'http://auth0.com/oauth/grant-type/password-realm',
      realm: __ENV.AUTH0_CONNECTION,
      audience: __ENV.AUTH0_AUDIENCE,
      scope: 'openid profile email',
      client_id: __ENV.AUTH0_CLIENT_ID,
      client_secret: __ENV.AUTH0_CLIENT_SECRET,
      username: `${prefix}-${i}@${domain}`,
      password: __ENV.AUTH0_TEST_PASSWORD,
    };
    const res = http.post(`https://${__ENV.AUTH0_DOMAIN}/oauth/token`, body); // form-encoded
    if (res.status !== 200) throw new Error(`mint ${i} failed: ${res.status} ${res.body}`);
    tokens.push(res.json('access_token'));
    // gentle pacing to avoid Auth0 /oauth/token throttling
  }
  return tokens; // returned from setup(); k6 passes it to default() and teardown()
}
```

---

## 7. Load model (50–200 VU profiles)

Profiles are selected via `__ENV.PROFILE` (one file per profile under `scenarios/`, or a single
`main.js` with multiple `scenarios{}` keys gated by env). All numbers are **starting points** — tune
against the smoke baseline.

| Profile | Executor | Shape | Purpose |
|---|---|---|---|
| `smoke` | `constant-vus` | 1 VU, 1 min | Sanity: auth, correlation, every request type works |
| `load` (nominal) | `ramping-vus` | 2m→50, 6m@50, 2m→0 | Steady-state SLO measurement |
| `stress` | `ramping-vus` | 2m→50, 3m→100, 3m→150, 3m→200, 3m@200, 2m→0 | Find the knee / breaking point (50→200) |
| `spike` | `ramping-arrival-rate` | 30s→ high rate, 1m hold, 30s→low | Burst resilience of tunnel + Kong |
| `soak` (optional) | `constant-vus` | 50 VU, 1–2 h | Leaks, GC, connection exhaustion (mind token TTL) |

**Read vs write split — run them as separate concurrent scenarios** so writes never drown in 429
(see §9). The read scenario carries the 50–200 VU headline load; the write scenario is a deliberately
**low constant arrival rate** kept under the Kong ceiling.

```javascript
// main.js (illustrative — gate by __ENV.PROFILE)
import exec from 'k6/execution';
export const options = {
  scenarios: {
    // headline read load (anonymous + authenticated GETs) — carries the 50–200 VUs
    browse: {
      executor: 'ramping-vus',
      exec: 'browse',
      stages: [ {duration:'2m',target:50},{duration:'3m',target:100},
                {duration:'3m',target:150},{duration:'3m',target:200},
                {duration:'3m',target:200},{duration:'2m',target:0} ],
      tags: { scenario: 'browse' },
    },
    // write trickle kept UNDER the Kong per-route ceiling (see §9)
    writes: {
      executor: 'constant-arrival-rate',
      exec: 'writes',
      rate: 8, timeUnit: '1m',          // ≤ events.create=10/min Kong cap
      duration: '16m', preAllocatedVUs: 10, maxVUs: 20,
      tags: { scenario: 'writes' },
    },
  },
  thresholds: { /* see §10 */ },
};
export function setup() { /* mint token pool + fetch working-set of PUBLISHED event ids */ }
export function browse(data) { /* 70% anon + 20% authed reads */ }
export function writes(data) { /* organizer + engagement writes, correlated */ }
export function teardown(data) { /* cleanup created entities */ }
```

---

## 8. Integration scenario (weighted, read-heavy)

Two `exec` functions. Endpoints, methods, auth and payloads below are taken from
`backend/docs/api-contract.md` + `data-model.md` — **all public (Kong-routed)**; internal endpoints
(`*/_internal-*`, `attendance-summary`, `?check-co-org-of=`, …) are **excluded** (no Kong route → 404
externally; see `backend/docs/internal-endpoints.md`).

### 8.1 `browse()` — read load (no writes)

Per iteration, pick a path by weight; `sleep()` 1–5 s between steps (think-time).

- **~70 % anonymous** (no `Authorization` header, **unmetered**):
  - `GET /api/events?status=PUBLISHED&page=&size=` (list + filters: `category`, `faculty`,
    `endDateFrom`)
  - `GET /api/events/search?q=&category=&tags=&page=&size=`
  - `GET /api/events/{id}` (use ids from the working set; **PUBLISHED only** — DRAFT/CANCELLED →
    404 anti-oracle)
  - `GET /api/events/featured`
  - `GET /api/events/{id}/comments?page=&size=`
  - `GET /api/events/{id}/occurrences`
  - `GET /api/s/{shortCode}` (expect **302**; set `redirects: 0` to measure the redirect, not the
    target)
  - `GET /api/users/{uuid}` (public profiles), `GET /api/users/by-username/{username}`
- **~20 % authenticated** (Bearer token from the pool):
  - `GET /api/users/me` (idempotent provisioning)
  - `POST /api/events/{id}/favorite` (idempotent 200) / `DELETE …/favorite`
  - `POST /api/events/{id}/attend` (200; 400 if not PUBLISHED, 409 if past `registrationDeadline`) /
    `DELETE …/attend` (capacity + WAITLISTED auto-promotion)
  - `GET /api/users/me/{favorites,attendances,participations,events,notifications}`
  - `POST /api/users/{otherId}/follow` / `DELETE …/follow` *(rate-limited — keep light here; main
    follow load belongs to the write scenario calibration)*
  - `POST /api/comments/{id}/like` / `DELETE …/like`

### 8.2 `writes()` — organizer + engagement (low arrival rate)

Each iteration runs a correlated mini-journey by the VU's own identity:

1. `POST /api/events` → **201** `EventDTO` (capture `id`). Body `CreateEventRequest`:
   ```json
   {
     "title": "[LOADTEST] perf event <vu>-<iter>",
     "description": "[LOADTEST] generated",
     "location": "Online",
     "startDate": "<ISO-8601, strictly in the future>",
     "endDate":   "<ISO-8601, after startDate>",
     "category": "CONFERENCE",
     "faculty": "SCIENCES",
     "capacity": 100
   }
   ```
   `category` ∈ `ACADEMIC|SPORTS|CULTURAL|SOCIAL|CONFERENCE|OTHER`;
   `faculty` ∈ `SCIENCES|MEDICINE|LETTERS|SOCIAL_SCIENCES|GSEM|LAW|THEOLOGY|PSYCHOLOGY|FTI`.
   `startDate` must be `@Future` (use `now + N days`). Rate-limited (see §9).
2. `PATCH /api/events/{id}/publish` → 200 (DRAFT → PUBLISHED).
3. *(optional, costly)* `POST /api/events/{id}/image` (multipart, ≤ 5 MiB) — keep a small fraction.
4. `GET /api/events/{id}/stats` (creator view).
5. `POST /api/events/{id}/comments` → 201. Body `CreateCommentRequest`
   `{ "content": "[LOADTEST] ...", "parentCommentId": null }` (content 1–500 chars). Rate-limited.
6. occasional `POST /api/events/{id}/report` (on a **foreign** event; own event → 422). Body
   `{ "reason": "OTHER", "description": "[LOADTEST]" }` (`reason` ∈ `SPAM|INAPPROPRIATE|FAKE|OTHER`).

### 8.3 Cross-service / Kafka → notification lag

After a write that triggers a notification (e.g. another pool user **attends** an event created by VU
*k*, or **follows** VU *k*), the **owner** VU polls `GET /api/users/me/notifications` (header
`X-Unread-Count`) until the expected notification appears or a **bounded timeout** (e.g. 30 s, poll
every 2–3 s). Record the delay in a `notification_lag` Trend (eventual consistency is accepted —
at-least-once). Do not fail the iteration on timeout; record a miss.

### 8.4 Correlation & anti-oracle handling

- In `setup()`, fetch 1–2 pages of `GET /events?status=PUBLISHED` to build a **working set** of real
  `{id, shareCode?}` for the anonymous/auth read paths; pass it via the `setup()` return value.
- Capture ids from `201` bodies for the write journey (`eventId`, `commentId`).
- Treat `404` on DRAFT/CANCELLED as **expected** (anti-oracle), not a failure.

---

## 9. Rate-limit handling (real on the remote target)

Two stages apply simultaneously (`backend/docs/api-contract.md`):

**(a) Kong `rate-limiting` plugin, `policy: local`** on 3 routes (prod config in
`helm/templates/kong/configmap-routes.yaml`):

| Route | Limit |
|---|---|
| `POST /events` | 10 / min |
| `POST /events/{id}/comments` | 10 / min |
| `POST /users/{id}/follow` | 30 / min |

> **Reality check (important).** Prod Kong runs **`replicas: 1`** (`helm/values.yaml`), so `policy:
> local` is a **single counter** (no ×2 across replicas — the architecture doc's "2 replicas" is
> stale). Kong here does **not** authenticate a consumer, so the plugin's `limit_by` falls back to
> **IP**. From the **single load-generator IP** (and possibly collapsed further to one upstream IP
> behind the Cloudflare tunnel), these caps behave as a **shared ceiling for the whole test**, not
> per-VU. **Net effect: you cannot exceed ~10 event-creates/min, ~10 comments/min, ~30 follows/min in
> total via Kong, regardless of VU count.** Confirm the observed behaviour during smoke (watch
> `RateLimit-*` response headers — `hide_client_headers: false`).

**(b) `@PerUserRateLimit`** (in-app, keyed by user `sub`, 60 s window): events.create=10,
comments.post=10, follows.follow=30, comments.like=30, users.search=60, notifications.read=60,
notifications.readAll=10, reports.commentCreate=5, events.duplicate=10, events.uploadAttachment=10,
users.updateUsername=5. With 50 distinct users this is far higher in aggregate than the Kong IP cap —
so **Kong is the binding write constraint from one IP.**

**Decision:**
1. Keep the **write scenario arrival rate under the Kong caps** (e.g. creates ≤ 8/min, comments ≤
   8/min, follows ≤ 25/min) so writes mostly succeed and we measure latency, not 429 storms.
2. **Measure 429s** as a first-class signal: a `Counter('rate_limited')` tagged by `bucket`
   (`events.create`, `comments.post`, `follows.follow`, …).
3. **Exclude expected 429s from the error SLO:** drive the SLO from a custom
   `Rate('unexpected_failures')` (incremented when status ∉ the endpoint's expected set, where the 3
   rate-limited write endpoints include 429 as expected). Optionally set
   `http.setResponseCallback(http.expectedStatuses(...))` to keep the built-in `http_req_failed` sane.
4. The **bulk of load rides the unmetered GET endpoints** — that's where the 50–200 VU headline lives.

If genuine **write-throughput** capacity is a goal, it requires either a dedicated load-test env with
the Kong rate-limit plugin raised/removed, or multiple source IPs (out of scope from one machine) —
**log this limitation explicitly in the report** (no silent cap).

---

## 10. Metrics & thresholds (SLOs)

Account for WAN + tunnel latency; calibrate against the smoke baseline. Starting thresholds:

```javascript
thresholds: {
  // Latency — built-in, tag-scoped where useful
  'http_req_duration{scenario:browse}': ['p(95)<800', 'p(99)<1500'],
  'http_req_duration{type:anon_read}':  ['p(95)<700'],
  'http_req_duration{type:auth_read}':  ['p(95)<1200'],
  // Error budget — EXCLUDES expected 429 (custom metric drives the verdict)
  unexpected_failures: ['rate<0.01'],
  // Correctness
  checks: ['rate>0.99'],
  // Eventual consistency (Kafka → notifications)
  notification_lag: ['p(95)<30000'],   // ms
}
```

**Custom metrics (`lib/metrics.js`):**
- `Trend('endpoint_latency')` tagged `{endpoint, service, method}` — per-endpoint / per-service
  breakdown (also achievable via `http_req_duration{endpoint:…}` using request tags).
- `Counter('rate_limited')` tagged `{bucket}` — 429s per rate-limit bucket.
- `Rate('unexpected_failures')` — non-expected statuses (the error SLO source).
- `Trend('notification_lag')` — ms from triggering write to notification visibility.
- Tag **every** request with `{endpoint, service}` so Grafana/`handleSummary` can break results down by
  the 5 services.

**`http_req_failed`** is kept for reference but is **not** the SLO (it would count expected 429s).

---

## 11. k6 file tree to produce (under `load-testing/`)

```
load-testing/
├── specs_load-testing-k6.md        # this spec
├── .env / .env.example             # config + secrets (.env git-ignored)        [exists]
├── seed-users.sh / get-token.sh    # auth bootstrap                              [exists]
├── README.md                       # operator steps                             [exists]
├── lib/
│   ├── config.js                   # read __ENV, BASE_URL, POOL_SIZE, thresholds, expected-status sets
│   ├── auth.js                     # mintTokenPool() (password-realm), per-VU token selection
│   ├── metrics.js                  # Trend/Counter/Rate definitions
│   ├── http.js                     # tagged request wrappers; 429 counter; unexpected_failures
│   ├── data.js                     # payload builders (event/comment/report) + working-set helpers
│   └── journeys.js                 # browse() / writes() step functions + think-time
├── scenarios/
│   ├── smoke.js                    # 1 VU sanity (also validates auth + correlation)
│   ├── load.js                     # nominal ~50 VU
│   ├── stress.js                   # 50 → 200
│   ├── spike.js                    # burst
│   └── soak.js                     # optional, 1–2 h
├── main.js                         # combined scenarios (browse + writes), gated by __ENV.PROFILE
└── results/                        # JSON output (git-ignored; create with `mkdir -p results`)
```

`results/` and `.env` are git-ignored (see root `.gitignore`).

---

## 12. Install & run procedure (from `unige-debian`)

**Install k6 (choose one):**
```bash
# A) native binary (Debian)
sudo gpg -k && sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list && sudo apt-get update && sudo apt-get install k6

# B) Docker (no install)  — mounts the folder, loads .env
#   run from repo root:
docker run --rm -i --env-file load-testing/.env \
  -v "$PWD/load-testing":/work -w /work grafana/k6 run scenarios/smoke.js
```

**Run (native; load .env into the environment first):**
```bash
cd load-testing && set -a && . ./.env && set +a
mkdir -p results                                 # handleSummary writes here; dir is git-ignored
k6 run scenarios/smoke.js                       # 1) always smoke first
k6 run scenarios/load.js                         # 2) nominal
k6 run -e PROFILE=stress main.js                 # 3) stress 50→200
# Raw JSON stream + summary:
k6 run --out json=results/stress-raw.json -e PROFILE=stress main.js   # handleSummary writes results/summary.json
# Optional live metrics to Prometheus/Grafana:
K6_PROMETHEUS_RW_SERVER_URL=http://<prom>:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),min,max' \
k6 run -o experimental-prometheus-rw -e PROFILE=load main.js
```

**Reporting:** `handleSummary(data)` emits `results/summary.json` plus a compact console
summary (see `lib/summary.js`) — self-contained, no remote imports. Per-`{service, endpoint}`
latency and the `rate_limited` counter are captured in `summary.json` and the raw `--out json`
stream. (An HTML report via `k6-reporter` was dropped to avoid a remote dependency.)

**Sequence discipline:** smoke → load → (analyse) → stress → (analyse) → spike → (optional) soak.
Re-mint tokens between long runs if TTL is exceeded.

---

## 13. Pass / fail criteria

A run **passes** when:
- `unexpected_failures` rate < 1 % (429s on the 3 rate-limited buckets are expected, not failures).
- `checks` pass-rate > 99 %.
- Latency thresholds (§10) hold for the **`load` (nominal)** profile.
- `notification_lag` p95 < 30 s (eventual consistency).
- No single-replica service crashes during `load`.

The **`stress`** profile is exploratory: its goal is to **locate the knee** (VU level / arrival rate
where p95 latency or error rate degrades sharply) and identify which layer saturates (tunnel vs Kong
vs a service vs DB). Document the knee; it is not pass/fail.

---

## 14. Risks & limits (state these in the report — no silent caps)

- **Single-pod cloudflared tunnel** + **single Kong replica** + **single-replica services**: the test
  may measure the **edge/tunnel ceiling**, not app capacity. Compare prod vs preview if possible.
- **Kong rate limit is an IP-keyed shared ceiling** from one load-generator IP (§9) → write throughput
  cannot be meaningfully stressed from a single IP without raising/removing the plugin.
- **Data pollution** on a real deployment; `teardown()` is best-effort. Auth0 users + provisioned
  profiles persist unless explicitly deleted.
- **Auth0 `/oauth/token` throttling** → mint once in `setup()`; never per-iteration.
- **Single load generator** CPU/NIC/ephemeral-port limits near 200 VU — watch the generator's own
  resource use; it must not be the bottleneck.
- **Token TTL** vs soak duration.
- **Production parity caveat for any future local comparison:** prod is DB-per-service; the local
  docker-compose is single-Postgres (not used here, but relevant if results are ever compared).

---

## 15. Definition of done (for the later test-execution phase)

- [x] Target confirmed: **production** `https://pinfo6.p-info.net`; `BASE_URL` set in `.env`.
- [ ] `bash get-token.sh` → HTTP 200 (auth chain verified) before any k6 run.
- [ ] `lib/` + `scenarios/` + `main.js` implemented per §6–§11; secrets only from `.env`.
- [ ] `setup()` mints the token pool once and builds the PUBLISHED working set.
- [ ] `browse()` (70/20 anon/auth reads) + `writes()` (calibrated under Kong caps) implemented;
      requests tagged `{service, endpoint}`.
- [ ] Custom metrics wired: `rate_limited` (per bucket), `unexpected_failures`, `notification_lag`.
- [ ] Thresholds (§10) set; 429 excluded from the error SLO.
- [ ] smoke → load → stress executed; `results/summary.json` produced (after `mkdir -p results`).
- [ ] `teardown()` cleanup run; residual `[LOADTEST]` data reported.
- [ ] Report includes per-service breakdown, the stress knee, the 429/rate-limit finding, and the
      tunnel/edge-vs-app caveat.

---

## Appendix A — Rate-limit recap

| Endpoint | Kong (policy:local, 1 replica, IP-keyed) | `@PerUserRateLimit` (per user, 60 s) |
|---|---|---|
| `POST /events` | 10/min | 10 |
| `POST /events/{id}/comments` | 10/min | 10 |
| `POST /users/{id}/follow` | 30/min | 30 |
| `POST /comments/{id}/like` | — | 30 |
| `GET /users/search` | — | 60 |
| `POST /events/{id}/duplicate` | — | 10 |
| `POST /comments/{id}/report` | — | 5 |
| `PATCH /users/me/notifications/{id}/read` | — | 60 |
| `PATCH /users/me/notifications/read-all` | — | 10 |
| `POST /events/{id}/attachments` | — | 10 |
| `PATCH /users/me/username` | — | 5 |

## Appendix B — Env var reference (`load-testing/.env`)

| Var | Meaning |
|---|---|
| `BASE_URL` | Remote API base, e.g. `https://pinfo6.p-info.net/api` |
| `AUTH0_DOMAIN` | `dev-p8ufbvhr6g61j78w.us.auth0.com` |
| `AUTH0_AUDIENCE` | `https://unige-events/api` |
| `AUTH0_CONNECTION` | `Username-Password-Authentication` |
| `AUTH0_CLIENT_ID` / `AUTH0_CLIENT_SECRET` | ROPC client (`unige-events-loadtest-ropc`, Password grant) |
| `POOL_SIZE` | Number of seeded test users / tokens (default 50; 200 for 1-per-VU at peak) |
| `AUTH0_TEST_EMAIL_PREFIX` / `AUTH0_TEST_EMAIL_DOMAIN` | Email pattern `prefix-<i>@domain` |
| `AUTH0_TEST_PASSWORD` | Shared password for the pool |
| `AUTH0_MGMT_CLIENT_ID` / `AUTH0_MGMT_CLIENT_SECRET` / `AUTH0_MGMT_TOKEN` | Management API creds (seeding only) |

## Appendix C — Endpoint ↔ service map (public, Kong-routed)

| Service | Public endpoints used by the test |
|---|---|
| **event-service** | `GET/POST /events`, `GET /events/{id}`, `/events/search`, `/events/featured`, `/events/{id}/occurrences`, `PATCH /events/{id}/publish`, `POST /events/{id}/image`, `POST/DELETE /events/{id}/favorite`, `GET /users/me/{favorites,events}`, `GET /events/{id}/stats`, `GET /events/{id}/share`, `GET /s/{shortCode}`, `POST /events/{id}/view` |
| **user-service** | `GET /users/me`, `GET /users/{id}`, `/users/by-username/{username}`, `POST/DELETE /users/{id}/follow`, `GET /users/{id}/{followers,following}`, `GET /users/me/follow-requests` |
| **engagement-service** | `POST/DELETE /events/{id}/attend`, `GET /events/{id}/attendees`, `GET/POST /events/{id}/comments`, `DELETE /comments/{id}`, `POST/DELETE /comments/{id}/like`, `GET /users/me/{attendances,participations}` |
| **moderation-service** | `POST /events/{id}/report` |
| **notification-service** | `GET /users/me/notifications`, `PATCH /users/me/notifications/{id}/read`, `…/read-all` |

> Internal endpoints (`*/_internal-*`, `/events/{id}/attendance-summary`, `?check-co-org-of=`, …) are
> **not** Kong-routed and **must not** be targeted (404 externally — see `internal-endpoints.md`).
