# Load Testing Spec v2 — UNIGE Events (Grafana Cloud k6, production capacity)

> **Status:** spec / implementation guide for the **second** load test. When an agent is later
> pointed at this file, it must be able to run the cloud test from this document alone.
> **Branch:** `perf/k6-load-testing-v2`. **Do not** modify `openapi/openapi.yaml`.
> **Language of all deliverables:** English.
>
> **This builds on test #1** ([`specs_load-testing-k6.md`](specs_load-testing-k6.md) +
> [`RESULTS.md`](RESULTS.md)). Read those first. v2 does **not** re-derive the auth bootstrap, the
> integration journey, or the safety model — it **reuses** `lib/` + `scenarios/` and only adds what
> test #1 could not measure.
>
> **✅ Target: production `https://pinfo6.p-info.net` (API `…/api`) — ONLY. Never preview/staging.**

---

## 0. Why a second test (what #1 left on the table)

Test #1 ran from a **single IP** and hit two walls *before* the application itself saturated:

1. A **Kong mis-scope** throttled the public `GET /api/events` listing to 10/min per IP — **fixed**
   in PR #219 (method-split route), deployed and verified live (`GET /api/events` → no 429).
2. The remaining ceiling was **per-IP**: Cloudflare edge shed ~11 % of requests at ~145 req/s from
   one IP, and Kong's per-route write caps behaved as a single shared counter. **From one IP you
   cannot measure real throughput** — RESULTS.md §5 logged this as the open limitation.

**v2's job:** generate load from **many IPs** (Grafana Cloud k6, multiple load zones) to get *past*
the per-IP edge wall and find the **real knee** of the production topology, while honestly
classifying what is the **edge** vs what is the **application**. It also closes #1's gaps:
per-service breakdown under true load, a **spike** and a **soak**, and a **cross-VU
`notification_lag`** measurement.

> **Pedagogical framing.** This exercise is graded partly on *method*. The deliverable must show a
> rigorous production load-testing approach **and** state its limitations plainly (the instructor is
> aware of them). A defensible "we measured X, we could not isolate Y, here is why" beats a
> confident-but-wrong capacity number.

---

## 1. Objectives

- Drive a realistic, **multi-service** workload from **multiple source IPs** against prod and find the
  **knee** (latency degradation / error onset) per `{service, endpoint}`.
- **Separate edge from app:** classify every response as application-origin vs Cloudflare-edge
  (challenge / rate-limit / 5xx / cache) so conclusions are not contaminated by edge behavior.
- **Empirically answer:** does multi-IP raise the ceiling? Specifically — is the limit keyed on the
  **real client IP** (Cloudflare edge; multi-IP helps) or on the **single cloudflared/ingress IP**
  Kong sees upstream (multi-IP does *not* help writes)? See §6.
- Measure **spike** recovery (single cloudflared pod + single Kong replica) and **soak** stability
  (memory/GC, DB connection pool, Kafka consumer lag, ephemeral storage) over 1 h.
- Measure **`notification_lag`** (Kafka → notification visibility) with **cross-VU** coordination
  (user A acts on user B's content), which #1 could not (self-actions don't notify the actor).

**Out of scope:** fixing what we find; server-side instrumentation (no cluster access — see §3);
preview/staging; UI/browser testing; raising rate limits or scaling infra.

---

## 2. System under test & network chain

```
Grafana Cloud k6 load generators (multiple AWS load zones, many egress IPs)   ← the generator
        │  public HTTPS, per-zone client IPs
        ▼
Cloudflare edge   (bot mitigation, per-IP rate limiting, caching)   ← PART OF THE SUT; not bypassable
        ▼
cloudflared tunnel   (1 pod, mode "named")                          ← single ingress; throughput ceiling
        ▼
Ingress Nginx  →  Kong API Gateway (replicas: 1, DB-less, rate-limiting policy: local)
        ▼
Quarkus services (event / user / engagement / moderation / notification — each replicas: 1)
        ▼
per-service PostgreSQL 16   +   Kafka (events → notifications)   +   MinIO (S3)
```

Facts that bound every conclusion (verified in `helm/`):

- **Everything is `replicas: 1`.** `event-service` is **hard-pinned to 1** (`helm` `fail` guard,
  ADR-001: `EventExpirationJob` has no leader election) → it **cannot scale**, by design.
- **Single cloudflared pod + single Kong replica** = the likely first saturation points (RESULTS.md
  #4: a 50–60 s tail = edge queuing under burst).
- **Tiny resource requests** (cpu 50–100m, mem 64–512Mi, no limits) → the app/DB knee may be low.
- **Cloudflare sits in front and we do not control it** (§3). Treat it as part of the SUT.

---

## 3. Locked decisions & constraints (respect strictly)

| Decision | Value | Consequence for the test |
|---|---|---|
| **Target** | prod `https://pinfo6.p-info.net` only | Never preview. All safety rules (§5) are mandatory. |
| **Generator** | **Grafana Cloud k6** (`k6 cloud run`), multi-zone | Many egress IPs → beats the per-IP **edge** wall of #1. |
| **Topology** | **as-is**, no infra changes | We measure the real ceiling of prod *as it runs today*. |
| **Observability** | **client-side only** (no kubectl, no server Prometheus) | Bottleneck attribution is **inferential** (§9, §12). |
| **Cloudflare** | **not controlled** (no allowlist, no bot-fight off, no cache bypass) | Test runs *through* the edge; we **measure & classify** edge responses, we do **not** try to defeat them (§7). |

> **The Cloudflare limitation is the headline caveat.** Grafana Cloud k6 generates from known AWS IP
> ranges; with no allowlist, Cloudflare **bot mitigation may challenge/break** a fraction of requests,
> and per-IP rate limiting still applies per egress IP. We do **not** attempt to evade this. Instead we
> (a) **classify** edge responses as a first-class signal, (b) keep load within "looks like real
> traffic" shapes, and (c) state up front that **application capacity cannot be fully isolated from
> edge shedding/bot mitigation.** Every number is reported as *user-facing, edge-included*.

---

## 4. Prerequisites & authorization

- [x] **Grafana Cloud k6** ready: k6 **v2.0.0** installed in the devcontainer and **authenticated**
      (`k6 cloud login` done) — stack `https://eliebussod.grafana.net` (id `1672446`), default project
      **`7680240`**. `K6_CLOUD_TOKEN` + `K6_CLOUD_PROJECT_ID` are in git-ignored `load-testing/.env`.
- [x] **ROPC pool** of 50 test users (`loadtest-1..50@example.com`) seeded; `setup()` mints one token
      per user. (`seed-users.sh` now requires `jq`.)
- [ ] **Off-hours window** agreed (prod is the graded live site). Someone watches it.
- [ ] **VUh budget** respected — **500 VUh total on the trial** (≈14 days left). This is a **hard
      cap**; see the budget table in §10. Reserve ~30–40 % for re-runs after tuning.
- [ ] **Verify the trial's max-concurrent-VUs-per-test limit** before sizing (trials are often capped,
      e.g. ~50–100 VUs). If capped, the §8 peaks must be lowered and that **itself bounds** achievable
      throughput — log it (no silent cap).
- [ ] **Kong fix is live** — re-check immediately before every run:
      `for i in $(seq 1 15); do curl -s -o /dev/null -w '%{http_code} ' https://pinfo6.p-info.net/api/events; done`
      → **no 429**. (A CI race in `deploy.yml` can revert it if a stale branch deploys afterwards.)

**No Cloudflare allowlist** is available and that is accepted; there is **no action to obtain one**.
The test copes by measuring edge behavior, not defeating it (§7).

---

## 5. Production safety (mandatory)

Identical model to test #1 (§4 there), restated because the target is the live site:

1. **Read-only headline.** The bulk of load is anonymous/auth **GETs** — non-polluting.
2. **Writes only on `[LOADTEST]` content**, at low arrival rate, by the test accounts; never touch
   real users' data. Cross-VU actions stay between pool users.
3. **`teardown()` cleanup** (cancel+delete `[LOADTEST]` events, undo test-to-test follows) + **verify
   residue**: `curl 'https://pinfo6.p-info.net/api/events/search?q=LOADTEST'` → `[]`.
4. **Automatic abort to protect real users.** Thresholds with `abortOnFail` stop the test if the
   **origin** degrades — and crucially they distinguish:
   - **Expected, NOT a failure:** edge `429` / bot challenge / Kong `429` on write caps → *do not*
     abort (these are the ceiling we are mapping).
   - **Protective abort:** **origin** `5xx`/timeouts climb (a real backend in trouble) → abort.
   Concretely (tunable): abort when `origin_errors` rate `> 0.10` sustained `30 s`, or app
   `http_req_duration` p95 `> 8 s` sustained `60 s`. See §9 for the `origin_errors` definition that
   excludes edge 5xx (520–527) and expected 429/404.
5. **Start small, escalate.** Always `k6 cloud run` the **smoke** first (1 VU); only then the ramp,
   then load, then spike, then soak. Re-mint tokens if a run approaches the Auth0 token TTL.

---

## 6. Rate-limit calibration & the multi-IP question (the core experiment)

**Per-IP caps in play** (Kong `rate-limiting`, `policy: local`, `limit_by` falls back to **IP**;
prod Kong `replicas: 1` → a single counter per keyed IP):

| Route | Kong cap | Methods affected |
|---|---|---|
| `POST /api/events` | 10 / min | POST only (after the #219 fix) |
| `POST /api/events/{id}/comments` | 10 / min | POST only |
| `~/api/users/{id}/follow` | 30 / min | **ALL methods** — also throttles `DELETE`/unfollow |
| `GET /api/events` (listing) | **none** (fixed) | — |

Plus in-app `@PerUserRateLimit` keyed by user `sub` (events.create=10, comments.post=10,
follows.follow=30, … per 60 s) — far higher in aggregate across 50 users than the Kong IP cap.

**The decisive unknown — where is the IP cap keyed?**

- **Cloudflare edge** rate-limiting / bot mitigation keys on the **real client IP** → spreading across
  Grafana Cloud load zones **raises** the aggregate edge budget. *This is the read-path win.*
- **Kong** keys on the IP it *sees*. If `X-Forwarded-For` from Cloudflare → cloudflared → ingress is
  **not** trusted/propagated to Kong, Kong sees the **single cloudflared/ingress upstream IP** → the
  write caps (events/comments/follow) are a **global ceiling regardless of how many client IPs we
  use**. If it *is* propagated, multi-IP raises write throughput too.

> **Make this an explicit experiment, not an assumption.** During the capacity ramp and the
> write-path probe, compare the **aggregate** successful write rate across zones against the 10/min
> (events, comments) and 30/min (follow) caps:
> - If aggregate writes plateau at ~10/min (events) **regardless of zone count** → Kong keys on the
>   upstream IP; multi-IP does **not** help writes. Report this.
> - If aggregate writes scale ~linearly with zone count → Kong sees the client IP; report the real
>   write ceiling.
> Read the `RateLimit-*` response headers (`hide_client_headers: false`) and the `bucket`-tagged
> `rate_limited` counter to attribute 429s.

**Decision for the run:** keep write arrival rates modest and **let 429s happen as a measured
signal** (not an error). The headline load rides the **now-unthrottled GET** endpoints, where
multi-IP is expected to actually buy throughput.

---

## 7. Cloudflare edge: expected responses & classification

Because we run *through* an uncontrolled edge, the harness must label edge responses so they don't
masquerade as application results. Added to `lib/http.js` + `lib/metrics.js` (see §11):

| Signal (client-observable) | Meaning | Counted as |
|---|---|---|
| `Cf-Ray` present, `Server: cloudflare` | response touched the edge (≈ all responses) | context only |
| `Cf-Cache-Status: HIT/MISS/DYNAMIC/EXPIRED` | edge cache outcome | `edge_cache{status}` — a **HIT never reached origin** (don't credit it as app latency) |
| HTTP `429` **with** `RateLimit-Limit`/`X-RateLimit-*` | **Kong** per-route cap (app layer) | `rate_limited{bucket}`, expected |
| HTTP `429` **without** Kong RateLimit headers, with `Cf-Ray` | **Cloudflare** per-IP rate limit (≈ code 1015) | `edge_blocks{kind:edge_429}`, expected |
| HTTP `403`/`503` + `Cf-Mitigated`/challenge body | bot challenge / managed challenge (1010/1020) | `edge_blocks{kind:challenge}` |
| HTTP `520`–`527` | Cloudflare↔origin edge errors (origin down/timeout at edge) | `edge_blocks{kind:edge_5xx}` |
| HTTP `5xx` **without** edge codes, `Cf-Ray` present | **origin** 5xx surfaced through edge | `origin_errors`, **protective-abort signal** |

Classification is **best-effort and documented as such** — perfectly separating a Cloudflare 1015
from a Kong 429 client-side is not always possible. The `RateLimit-*` header presence is the primary
discriminator. A high `edge_blocks{kind:challenge}` rate means **bot mitigation is shaping results** →
flag it loudly; it caps how far the read ramp can go and is the #1 reason a clean app-capacity number
may be unobtainable.

---

## 8. Scenarios

All reuse `lib/entry.js` + `lib/options.js` (profiles added in §11). Each carries the **cloud** block
(project + load-zone distribution) so `k6 cloud run` fans out across IPs; a plain `k6 run` ignores it.

| # | Scenario | Profile / file | Executor | Shape (tunable, within VU cap) | Purpose |
|---|---|---|---|---|---|
| a | **Post-fix validation** | `scenarios/smoke.js` (+ the §4 curl) | 1 VU | 6 browse + 3 write iters | Auth + correlation + GET-no-429 + POST-429>10/min. Cheap. |
| b | **Capacity ramp (reads)** | `scenarios/cloud-capacity.js` | `ramping-arrival-rate` | 20→500 req/s over ~12 m, multi-zone, `-e SKIP_AUTH=1` | Find the **read knee** across IPs; un-throttled endpoints only. |
| c | **Full-journey load** | `scenarios/load.js` | `ramping-vus` + write trickle | ramp to 50 VU, 6 m hold | Realistic mixed read/write across **all services**; per-`{service,endpoint}` SLOs. |
| d | **Spike** | `scenarios/spike.js` | `ramping-vus` | 20s→200, 40s hold, 20s→0 | Burst resilience of single cloudflared pod + Kong; recovery time. |
| e | **Soak (1 h)** | `scenarios/soak.js` | `constant-vus` | 30 VU, 60 m + write trickle 6/min | Leaks, GC, agroal DB pool, Kafka consumer-lag drift, ephemeral-storage. Sized to VUh budget. |
| f | **`notification_lag` (cross-VU)** | `scenarios/notify.js` | `constant-arrival-rate` | 10/min, ~5 m | User A follows user B → poll B's notifications → produce→notify latency. |

Notes:
- **(b)** ramps **request rate** (not VUs) — the right model for capacity behind a rate-limited edge.
  Default peak (500 req/s) is aspirational; the **trial VU cap** (§4) and Cloudflare shedding will
  likely cap it far lower — that ceiling *is* a finding. Lower `maxVUs`/peak if the trial caps VUs.
- **(c/d/e)** are the existing profiles, now fanned out across load zones via the shared cloud block.
- **(f)** triggers a follow (A→B), polls B's `X-Unread-Count`, records `notification_lag{trigger}`,
  then unfollows (inline + `teardown()` backstop). Honest limits in §12.

---

## 9. Metrics & SLOs

Custom metrics (in `lib/metrics.js`):

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `endpoint_latency` | Trend (ms) | `{endpoint,service,method}` | per-endpoint app latency (successful, non-cached) |
| `rate_limited` | Counter | `{bucket}` | Kong `429`s per write bucket (expected) |
| `unexpected_failures` | Rate | `{endpoint}` | SLO source — status ∉ expected set (**429/404 excluded**) |
| `notification_lag` | Trend (ms) | `{trigger}` | Kafka → notification visibility (cross-VU) |
| `edge_blocks` | Counter | `{kind}` | Cloudflare challenge / edge_429 / edge_5xx (**new**, §7) |
| `edge_cache` | Counter | `{status}` | `Cf-Cache-Status` distribution (**new**) |
| `origin_errors` | Rate | — | origin 5xx/timeout, **excluding** edge 520–527 & expected 429/404 (**new**, abort signal) |

**Thresholds / SLOs** (tunable, calibrated against the smoke baseline):

```
http_req_duration{scenario:browse} : p(95)<1500, p(99)<3000      // WAN + edge + tunnel budget
http_req_duration{scenario:reads}  : p(95)<1500, p(99)<5000      // capacity probe
unexpected_failures                : rate<0.05                   // edge/Kong 429 & 404 excluded
checks                             : rate>0.95
notification_lag                   : p(95)<30000                 // eventual consistency
origin_errors                      : rate<0.10  (abortOnFail, delayAbortEval 30s)   // PROTECTIVE
```

The error SLO is **driven by `unexpected_failures`**, never by raw `http_req_failed` (which counts
expected 429/404). Report `rate_limited`, `edge_blocks`, `edge_cache` separately as the **ceiling
map**, not as failures.

---

## 10. Grafana Cloud setup, run procedure & VUh budget

**Launch (from the devcontainer, already authenticated):**
```bash
cd load-testing && set -a && . ./.env && set +a
mkdir -p results
k6 cloud run scenarios/smoke.js                       # 1) ALWAYS smoke first (cheap, validates chain)
k6 cloud run -e SKIP_AUTH=1 scenarios/cloud-capacity.js   # 2) read capacity ramp (multi-IP)
k6 cloud run scenarios/load.js                         # 3) full-journey load
k6 cloud run scenarios/spike.js                        # 4) spike
k6 cloud run scenarios/notify.js                       # 5) notification_lag (cross-VU)
k6 cloud run scenarios/soak.js                         # 6) soak 1h (budget permitting)
```
Each `k6 cloud run` prints a Grafana Cloud URL; results (per-zone breakdown, percentiles, the custom
metrics above) are read from that **Grafana Cloud k6** test-run page. The load-zone distribution lives
in `lib/options.js` (`options.cloud.distribution`); default fan-out is Paris / Frankfurt / Dublin /
London (EU-realistic + IP diversity). Verify zone IDs against the current k6 load-zone list.

**VUh budget (hard cap = 500; keep total well under, reserve for re-runs):**

| Scenario | Peak VUs (approx) | Duration | ~VUh |
|---|---|---|---|
| a — smoke | 1 | 3 m | ~0.1 |
| b — capacity ramp | ≤ trial cap (e.g. 100–300) | ~12 m | ~25–50 |
| c — full-journey load | ~65 | ~15 m | ~16 |
| d — spike | ~210 | ~3 m | ~10 |
| e — soak | ~40 | 60 m | ~40 |
| f — notification_lag | ~30 | 5 m | ~3 |
| **Subtotal (one clean pass)** | | | **~95–120** |
| **Reserve for tuning / re-runs (~3×)** | | | **~300** |
| **Total** | | | **≤ 500** |

> VUh is billed roughly by **allocated max VUs × duration**. The capacity ramp dominates — cap its
> `maxVUs` to what the **trial VU limit** allows, and don't re-run it casually. Soak is the other big
> line; if budget is tight, run it once at 30 VU / 1 h, not 2 h.

---

## 11. Deliverables (what to produce)

1. **This spec** (`specs_load-testing-k6-v2-prod-capacity.md`).
2. **Script adaptations** (build on existing files — done as part of writing this spec):
   - `lib/metrics.js` — add `edge_blocks`, `edge_cache`, `origin_errors`.
   - `lib/http.js` — Cloudflare edge classification (§7) into those metrics.
   - `lib/options.js` — `options.cloud` block (project `7680240` + load-zone distribution) on every
     profile; new profiles `cloud_capacity` (ramping-arrival-rate reads) and `cloud_notify`; the
     `origin_errors` abort threshold.
   - `lib/entry.js` — `notifyProbe(data)` exec: cross-VU follow → poll B's unread → record
     `notification_lag` → inline unfollow.
   - `scenarios/cloud-capacity.js`, `scenarios/notify.js` — new entry files for the new profiles.
3. **`RESULTS-v2.md`** (after the run) — suggested structure:
   - Run table (per scenario: peak VUs/rate, reqs, throughput, p95/p99, `unexpected_failures`,
     `rate_limited`, `edge_blocks` by kind, `edge_cache` HIT ratio).
   - **Per-`{service,endpoint}`** latency breakdown and the **knee** (where p95 degrades / errors rise).
   - **The multi-IP verdict** (§6): did write throughput scale with zones, or is Kong keyed on the
     tunnel IP? With evidence (aggregate write rate vs cap).
   - **Edge vs app**: how much of the ceiling was Cloudflare (challenges/edge_429) vs origin.
   - Spike recovery time; soak drift (latency creep, error onset, any restarts inferable from 5xx).
   - `notification_lag` p50/p95 and miss rate.
   - **Limitations** (§12) restated against the actual numbers. Cleanup residue confirmation.

---

## 12. Explicit limitations (state these in RESULTS-v2)

1. **No server-side metrics** (client-side only): bottleneck attribution is **inferential** — we infer
   "event-service vs Postgres vs Kong vs cloudflared" from status codes, latency shape, per-endpoint
   divergence and edge headers, **not** from CPU/heap/GC/DB-pool/Kafka-lag. A finding like "writes
   plateau" cannot be pinned to a specific pod without cluster access.
2. **The edge cannot be bypassed** (no Cloudflare allowlist): **application capacity is not isolable**
   from edge shedding/bot mitigation. All numbers are *user-facing, edge-included*. If
   `edge_blocks{challenge}` is high, the read ramp measured Cloudflare, not the app.
3. **Multi-IP may not help writes** (§6): if Kong keys on the single cloudflared/ingress IP, the
   write caps are global regardless of zone count. We *measure* which case holds; we don't assume.
4. **As-is single-pod ceiling**: cloudflared (1) and Kong (1) are themselves the likely first knee;
   `event-service` is **pinned to 1** and cannot scale — so the "capacity" found is this topology's,
   not the application's theoretical max.
5. **Trial VUh cap (500) + per-test VU cap** bound test depth — peaks and the soak are sized to fit,
   so the true knee may sit *above* what the budget/VU-cap let us reach. Logged, not hidden.
6. **`notification_lag` cross-VU caveats**: it assumes a follow reliably notifies the followed user
   and that `X-Unread-Count` reflects it within the poll window; under heavy concurrent load the
   measured lag mixes Kafka lag with notification-service read latency. Reported as an order-of-
   magnitude, not a precise SLA.

---

## 13. Definition of Done

- [ ] Trial **max-VUs-per-test** confirmed; §8 peaks adjusted to fit; VUh budget table updated.
- [ ] Kong fix re-verified live (`GET /api/events` → no 429) immediately before the run.
- [ ] Off-hours window agreed; abort thresholds (`origin_errors`) in place and **tested** (they fire
      on origin 5xx, not on expected edge/Kong 429).
- [ ] smoke → capacity ramp → load → spike → notification_lag → soak executed via `k6 cloud run`;
      each run's Grafana Cloud URL captured.
- [ ] `teardown()` ran; residue `search?q=LOADTEST` → `[]` confirmed.
- [ ] `RESULTS-v2.md` produced with: per-service breakdown, the knee, the **multi-IP verdict**, the
      **edge-vs-app** split, spike recovery, soak drift, `notification_lag`, and the §12 limitations.
