# UNIGE Events — Load Testing Dossier (A→Z)

> **Purpose of this file.** A single, self-contained record of the entire load-testing effort:
> what we did, how, what we found, the limitations, and the conclusions. It is written so that a
> **fresh session (or a teammate) can reload the full context from this file alone**, and so it can
> directly feed the **2 presentation slides** on load testing (see §11 "Slide material").
>
> Companion files (same folder): test #1 spec [`specs_load-testing-k6.md`](specs_load-testing-k6.md) +
> results [`RESULTS.md`](RESULTS.md); test #2 spec
> [`specs_load-testing-k6-v2-prod-capacity.md`](specs_load-testing-k6-v2-prod-capacity.md) + results
> [`RESULTS-v2.md`](RESULTS-v2.md); scripts in `lib/` + `scenarios/`; bootstrap `seed-users.sh`,
> `get-token.sh`, `.env.example`.
>
> Dates are absolute (campaign run **2026-05-29**).

---

## 1. TL;DR (the one thing to remember)

We load-tested the **production** UNIGE Events deployment (`https://pinfo6.p-info.net`) with Grafana
k6, in two campaigns. **Test #1** (single campus IP) found and we fixed a **Kong rate-limit bug** that
throttled the public event listing. **Test #2** (Grafana Cloud, intended multi-IP) then showed the
real ceiling: **the production single-pod, low-resource topology starts timing out at ~80 req/s from
one IP and under even moderate sustained load** — *before* any edge/rate-limit wall. 

**Conclusion:** the app's resistance to a large number of users is **bounded by infrastructure
sizing (single replicas, tiny CPU, one cloudflared tunnel, one Kong), not by application code,
Cloudflare, or the rate limits.** The path to "handles many users" is **horizontal/vertical scaling
of the deployment**, not code changes.

---

## 2. The system under test

```
client(s)
   │  HTTPS
   ▼
Cloudflare edge        — bot mitigation, per-IP rate limiting, caching (we do NOT control it)
   ▼
cloudflared tunnel     — 1 pod (named tunnel)              ← single ingress
   ▼
Ingress Nginx → Kong API Gateway (replicas: 1, DB-less, declarative routes, rate-limiting policy:local)
   ▼
Quarkus microservices  — event / user / engagement / moderation / notification, EACH replicas: 1
   ▼
per-service PostgreSQL 16  +  Kafka (events→notifications)  +  MinIO (S3 images)
```

Structural facts that bound everything (verified in `helm/values.yaml` + `helm/templates/**`):
- **Everything is `replicas: 1`.** `event-service` is **hard-pinned to 1** by a helm `fail` guard
  (ADR-001: `EventExpirationJob` has no leader election) → it **cannot scale horizontally** as-is.
- **Resource requests are tiny**: cpu 50–100m, mem 64–512Mi, **no limits**.
- **Single cloudflared tunnel pod + single Kong replica** = the edge/ingress chokepoints.
- Deploy = **merge to `main` → GitHub Actions (`ci-cd.yml`→`deploy.yml`) → `helm upgrade --install`**
  to namespace `unige-events`.
- All 5 services expose Prometheus metrics at `/q/metrics`; Kong has the `prometheus` plugin — **but
  no Grafana/Prometheus stack is deployed** to scrape them (hence client-side-only observability).

Kong per-route rate limits (per-IP, `policy: local`, Kong `replicas: 1` → single counter per IP):
`POST /api/events` = 10/min, `POST /api/events/{id}/comments` = 10/min,
`~/api/users/{id}/follow` = 30/min (all methods, incl. unfollow). `GET /api/events` listing = **no
limit after the test #1 fix**.

---

## 3. The story, A→Z

### Phase 0 — Motivation
Know how the platform behaves under 50–200 concurrent users **in real conditions** (hit the live
deployment, not localhost), find the bottlenecks, and produce defensible SLO/capacity statements.

### Phase 1 — Test #1 (k6, single IP from `unige-debian`)
- Built a reusable k6 harness in `load-testing/`: a realistic **multi-service user journey** (browse
  + writes across all 5 services), authenticated via **Auth0 ROPC** with a **pool of 50 seeded test
  users**, with `[LOADTEST]` tagging + `teardown()` cleanup for prod safety.
- Ran smoke + `load`@50VU + `stress-safe`@200VU against prod.
- **Key finding (a real bug):** the Kong `rate-limiting` plugin on `/api/events` was **not
  method-scoped**, so it throttled the **public GET listing** to 10/min/IP exactly like `POST`. Behind
  a shared NAT (campus), the whole site shared ~10 list requests/min → the homepage became unusable
  under shared load.
- Also observed: app/tunnel latency stayed healthy (p95 ~470 ms) up to 200 VU; the ceiling was
  **per-IP edge shedding** (~11% 429 at ~145 req/s, all `Server: cloudflare`). From one IP you cannot
  measure true throughput. → logged as the open limitation that motivated test #2.

### Interlude — the Kong fix (PR #219) and a deploy incident
- **Fix:** split the `/api/events` route by method — `events-create` (POST, keeps `rate-limiting
  minute:10`) vs `events-list` (GET/HEAD/OPTIONS, no limit). Applied in both `docker/kong.yml` and
  `helm/templates/kong/configmap-routes.yaml`. No application/contract change.
- Also addressed Copilot review on the tooling (POOL_SIZE guard, `jq`-safe user seeding, doc fixes).
- **PR #219 merged** → auto-deployed → verified live (`GET /api/events` → no 429).
- **Incident:** PR #218 merged ~20 s before #219 but its CI run **finished last**, and the deploy job
  checks out the *triggering commit* (not `main` HEAD), so #218's older tree **re-deployed the old
  Kong config and reverted the fix**. We detected it (GET 429s returned), **re-ran #219's deploy** to
  restore, and spun off a task to fix `deploy.yml` (deploy `main` HEAD, not the triggering SHA).

### Phase 2 — Test #2 (Grafana Cloud k6, prod-only)
- **Intent:** generate from **many IPs** (multiple Grafana Cloud load zones) to beat test #1's per-IP
  wall and find the true knee. Decisions locked with the team: **prod-only**, **as-is topology** (no
  infra changes), **client-side observability only** (no cluster access), **Cloudflare uncontrolled**
  (accepted, pedagogical — instructor aware).
- Adapted the harness (reused `lib/`+`scenarios/`): added Cloudflare **edge-response classification**
  (`edge_blocks`, `edge_cache`, `origin_errors`), `options.cloud` load-zone fan-out, a controlled
  **capacity** profile, and a **cross-VU `notification_lag`** probe.
- **Constraints discovered at runtime** (Grafana Cloud **Free** plan): a test may use **only 1 load
  zone** (multi-zone is paid) → **multi-IP unavailable**; plus **100 VUs/test, 1 h/test, 500
  VUh/month** caps. We proceeded **single-zone (Frankfurt)** and documented it.
- Ran the suite (smoke → capacity → load → spike → notify → soak). Results in §5 / `RESULTS-v2.md`.
- **Cleanup lesson:** the soak's in-test `teardown()` DELETEs timed out under prod degradation,
  leaving ~131 `[LOADTEST]` events; a targeted owner-by-owner script cleaned prod back to `[]`.

---

## 4. Methodology (how we did it)

- **Tool:** Grafana k6 (open-source) run via **Grafana Cloud k6** (`k6 cloud run`) for test #2; native
  k6 from one machine for test #1.
- **Auth:** Auth0 **ROPC** (`password-realm` grant). A confidential client
  (`unige-events-loadtest-ropc`) mints one token per test user; tokens minted **once** in `setup()`
  and reused; each VU maps to a fixed identity. Pool = 50 users `loadtest-1..50@example.com`, seeded
  via an Auth0 **M2M** app (`seed-users.sh`).
- **Workload:** a weighted **multi-service journey** — ~78% anonymous reads + ~22% authenticated reads
  for the headline load (`browse`), plus a low-rate **writes** trickle (create→publish→favorite→
  attend→comment→follow) kept under the Kong caps. Requests tagged `{service, endpoint}`.
- **Profiles** (`lib/options.js`, selected per `scenarios/*.js`): `smoke`, `load`, `stress`,
  `stress_nolist` (`stress-safe`), `spike`, `soak`, `cloud_capacity` (controlled arrival-rate),
  `cloud_notify` (cross-VU notification lag).
- **Custom metrics** (`lib/metrics.js`): `endpoint_latency`, `rate_limited` (Kong 429s by bucket),
  `unexpected_failures` (SLO source, **excludes** expected 429/404), `notification_lag`,
  `edge_blocks` (Cloudflare challenge/edge_429/edge_5xx), `edge_cache`, `origin_errors` (origin
  5xx/timeout — feeds the protective abort).
- **Edge vs app classification** (`lib/http.js`): every response is tagged using `Cf-Ray`,
  `Cf-Cache-Status`, `Cf-Mitigated`, `Server`, presence of Kong `RateLimit-*` headers, and status
  ranges (429, 520–527) — so Cloudflare behavior is never mistaken for app behavior.
- **Safety:** read-only headline; `[LOADTEST]`-tagged low-rate writes only; `teardown()` cleanup +
  `search?q=LOADTEST`→`[]` check; **automatic abort** (`origin_errors>10%`, `abortOnFail`) that fires
  on *origin* trouble (timeouts/5xx) but **not** on expected edge/Kong 429s.
- **Budget discipline:** VUh tracked against the 500 VUh/month cap (used ≈55 across all v2 runs).

---

## 5. Results (test #2)

7 runs, single zone Frankfurt, ~55 VUh total. Full table + per-run detail in
[`RESULTS-v2.md`](RESULTS-v2.md). Headline numbers:

| Scenario (run id) | Result | What it showed |
|---|---|---|
| smoke (7642401) | ✅ pass | chain works end-to-end |
| capacity NO_THINK (7642485) | ⛔ abort @~2.5 min | origin timeouts at ~30–50 aggressive VU → safety abort |
| capacity controlled (7642559) | ⚠️ failed | **knee ≈ 80 req/s** (timeouts begin) |
| load (7642605) | ⚠️ failed | many timeouts even at ~10–25 req/s sustained |
| spike (7642690) | ⚠️ failed | only 1 timeout — short bursts absorbed |
| notify (7642713) | ✅ pass | `notification_lag` measured, within p95<30 s |
| soak 20 VU/30 m (7642814) | ⚠️ failed | 9 timeouts/30 min — mostly stable, no leak/crash |

Prod recovered to `200 @ ~0.11 s` after every run. Detailed percentiles per run are on the Grafana
Cloud UI: `https://eliebussod.grafana.net/a/k6-app/runs/<id>`.

---

## 6. Conclusions & interpretation

1. **Does the app resist a large number of users?** Honest answer: **its current production *topology*
   does not** — it times out under moderate single-IP load (~80 req/s; ~50 concurrent mixed users).
   But the **bottleneck is infrastructure sizing, not the application code or the edge**: single
   replicas, tiny CPU/no limits, one tunnel, one gateway. The same code on a scaled deployment would
   very likely hold far more.
2. **The edge/rate-limits are not the real wall (anymore).** Test #1's headline (per-IP edge 429) was
   real but secondary; once we pushed harder (test #2), the **origin/tunnel saturates first**. The
   Kong listing bug we fixed was a genuine correctness issue, but it is not the capacity ceiling.
3. **Multi-IP was the wrong lever for this topology.** Spreading client IPs raises the *edge* per-IP
   budget, but here the origin falls before that budget matters — so multi-IP (even if the plan
   allowed it) would not raise the ceiling. The lever is **scaling the origin**.
4. **Bursts vs sustained:** the deployment absorbs short spikes (queue drains) but degrades under
   sustained concurrency — consistent with limited worker/connection capacity at single pods.
5. **The methodology held up:** realistic multi-service journey, prod-safe writes + cleanup, an
   abort that protected the live site, and edge-vs-app classification that kept the interpretation
   honest. The main process lesson: **`teardown()` must be robust to prod degradation** (it wasn't).

**Actionable follow-ups (infra, already aligned with PR #219 notes):** scale cloudflared (>1 pod) and
Kong (>1 replica + `rate-limiting policy: local → redis`); raise service CPU/replicas (event-service
needs leader election first, ADR-001); add server-side observability (cluster Prometheus →
remote_write to Grafana Cloud) to *prove* which pod is the first to fall.

---

## 7. Limitations (what we could NOT conclude, and why)

- **Single load zone (Free plan)** → multi-IP not exercised; can't separate per-IP vs global limits
  experimentally. We only know the *origin* knee from one IP.
- **Client-side only** → bottleneck attribution is **inferential** (timeouts + recovery + topology),
  not proven with CPU/heap/GC/DB-pool/Kafka-lag. We cannot name the exact first-failing pod.
- **Plan caps** (100 VU/test, 1 h/test, 500 VUh/mo) → never sustained >100 VU; the true knee may be
  higher than what we reached; soak limited to 30 min.
- **Cloudflare uncontrolled** → numbers are user-facing, edge-included; bot mitigation may shape a
  fraction of requests.
- **Sequential runs** → prod recovered between runs but pools/GC may not have fully reset, a possible
  confounder for the gentle `load` timeouts.
- **Detailed k6 Cloud metrics** (percentiles, per-bucket counts) not extractable via the REST API
  (undocumented OData); they live in the Grafana Cloud UI only.

---

## 8. Reproduce / continue (for a future session)

- **Branch:** `perf/k6-load-testing-v2` (test #2). Test #1 lives on the merged `perf/k6-load-testing`.
- **Run from the devcontainer** (`unige-events-devcontainer-1`, repo at `/workspace`, drive via
  `docker exec -u vscode`). k6 v2.0.0 is installed and authenticated to Grafana Cloud
  (`k6 cloud login` done; stack `eliebussod.grafana.net` id `1672446`, project `7680240`).
- **Secrets** in git-ignored `load-testing/.env` (Auth0 ROPC + `K6_CLOUD_TOKEN` + `K6_CLOUD_PROJECT_ID`).
- **Always** before a run: re-seed if needed (`bash seed-users.sh`, needs `jq`), and verify the Kong
  fix is live: `for i in $(seq 1 15); do curl -s -o /dev/null -w '%{http_code} ' https://pinfo6.p-info.net/api/events; done` → no 429.
- **Launch** (single zone, the only option on Free):
  ```bash
  cd load-testing && set -a && . ./.env && set +a && mkdir -p results
  k6 cloud run scenarios/smoke.js                                   # always first
  k6 cloud run -e SKIP_AUTH=1 -e NO_THINK=1 scenarios/cloud-capacity.js   # controlled req/s ramp
  k6 cloud run scenarios/load.js                                    # full journey
  k6 cloud run scenarios/spike.js
  k6 cloud run scenarios/notify.js                                  # notification_lag (cross-VU)
  k6 cloud run -e SOAK_VUS=20 -e SOAK_DURATION=30m scenarios/soak.js     # gentle soak
  ```
  Pass the Auth0 `-e` vars (BASE_URL, AUTH0_*) for any scenario that mints tokens (all except the
  `-e SKIP_AUTH=1` capacity probe).
- **To enable real multi-IP later:** upgrade to a paid k6 plan, then `-e MULTI_ZONE=1` (re-enables the
  4-zone fan-out already coded in `lib/options.js`).
- **Cleanup if a teardown fails** (residue): mint each owner's token (retry — Auth0 throttles rapid
  mints), `GET /users/me/events?size=50`, then per `[LOADTEST]` event `PATCH .../cancel` → wait ~0.6 s
  → `DELETE`. Verify `search?q=LOADTEST` → `[]`.

---

## 9. Key facts reference

- **Target:** `https://pinfo6.p-info.net` (API `…/api`), prod only.
- **Auth0:** tenant `dev-p8ufbvhr6g61j78w.us.auth0.com`, audience `https://unige-events/api`,
  connection `Username-Password-Authentication`, confidential ROPC client
  `unige-events-loadtest-ropc`, M2M app for seeding. Pool: `loadtest-1..50@example.com`.
- **Grafana Cloud k6:** stack `eliebussod.grafana.net` (id 1672446), project `7680240`. **Free plan
  caps:** 1 load zone/test, 100 VUs/test, 1 h/test, 500 VUh/month.
- **Run IDs (2026-05-29):** smoke 7642401 · capacity(NO_THINK) 7642485 · capacity(controlled) 7642559
  · load 7642605 · spike 7642690 · notify 7642713 · soak 7642814.
- **File map:** `lib/{config,auth,http,metrics,options,entry,summary}.js`;
  `scenarios/{smoke,load,stress,stress-safe,spike,soak,cloud-capacity,notify}.js`;
  `seed-users.sh`, `get-token.sh`, `.env(.example)`; specs + RESULTS (v1 & v2) + this dossier.
- **Commits:** v1 Kong fix `d24a4d0e`, review fixes `bdab8392`, merged as `b23814f`; v2 spec+scripts
  `d384746a`, plan-cap adaptation `56560f30` (on `perf/k6-load-testing-v2`).

---

## 10. Glossary (quick)

- **VU** = virtual user (one concurrent simulated client). **VUh** = VUs × hours (Grafana Cloud's
  billing/budget unit). **Knee** = the load level where latency/errors start degrading sharply.
- **ROPC** = Resource Owner Password Credentials (Auth0 password grant) — lets k6 mint tokens headless.
- **Edge 429 vs origin timeout** = "Cloudflare shedding excess per-IP traffic to protect the app"
  vs "the app/tunnel itself overwhelmed and not responding in time" — very different meanings.
- **`origin_errors`** = our custom metric: origin 5xx/timeouts only (excludes edge 520–527 & expected
  429/404); drives the protective abort.

---

## 11. Slide material (ready to lift for the 2 presentation slides)

### Slide 1 — *What we did & how* (load testing of UNIGE Events)
- **Goal:** measure how the **production** platform holds under many concurrent users, and find the
  bottleneck — in real conditions (hit the live deployment).
- **How:** Grafana **k6**, a realistic **multi-service user journey** (browse + create/publish/
  favorite/attend/comment/follow across all 5 microservices), **50 Auth0 test users** (ROPC), run from
  **Grafana Cloud** to use multiple IPs.
- **Two campaigns:** #1 from a single campus IP → found & fixed a **Kong rate-limit bug** (public
  event listing wrongly throttled to 10/min). #2 from Grafana Cloud to push for the real capacity.
- **Built for prod safety:** read-only headline load, `[LOADTEST]`-tagged writes only, auto-cleanup,
  and an **automatic abort** that stops the test if the live site starts failing.
- **One-line diagram:** `k6 (cloud) → Cloudflare → cloudflared tunnel (×1) → Kong (×1) → services
  (×1 each) → Postgres/Kafka`.

### Slide 2 — *Results & interpretation*
- **Kong bug fixed & verified** in prod: public event listing no longer throttled.
- **The real ceiling is the infrastructure, not the code:** from one IP the production deployment
  **times out at ~80 req/s** and under even moderate sustained load (~50 concurrent users) — because
  **everything runs as a single small replica** (cloudflared ×1, Kong ×1, services ×1, tiny CPU).
- **Edge/rate-limits are not the wall:** the origin saturates *before* Cloudflare's per-IP limit — so
  "more IPs" would not help; **scaling the deployment would.**
- **Bursts are absorbed; sustained load is not.** Short spikes ≈ 0 errors; sustained load ⇒ timeouts.
- **Verdict:** the application is functionally sound (notifications, journeys work under test), but to
  **support many real users it needs horizontal/vertical scaling** (more cloudflared/Kong/service
  replicas, higher CPU; `policy: local → redis`; leader election to unpin `event-service`).
- **Honest limitations:** single load zone (free plan) + client-side metrics only ⇒ the exact
  first-failing component is inferred, not measured; numbers are user-facing (through Cloudflare).
