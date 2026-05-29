# Load test results v2 — UNIGE Events (production, Grafana Cloud k6)

> Run **2026-05-29** with Grafana Cloud k6 (k6 v2.0.0), **single load zone `amazon:de:frankfurt`**
> (Free plan caps tests at 1 zone — see §Limitations), against production
> `https://pinfo6.p-info.net`. Auth via the ROPC token pool (50 seeded users). Spec + scripts:
> [`specs_load-testing-k6-v2-prod-capacity.md`](specs_load-testing-k6-v2-prod-capacity.md) +
> `lib/` + `scenarios/`. This is the **second** campaign; read [`RESULTS.md`](RESULTS.md) (test #1) first.
>
> **What v2 set out to do:** generate load from *many IPs* (Grafana Cloud) to get past the per-IP edge
> wall that capped test #1, and find the real knee of the production topology. **What actually
> happened:** multi-IP turned out to be both unavailable (plan-capped to 1 zone) **and moot** — the
> single-pod origin saturates *before* the edge per-IP limit. That is the headline result.

## Runs

All 7 runs went through the protective abort / SLO thresholds. "failed" = an SLO threshold was crossed
(mostly request timeouts counted as `origin_errors`/`unexpected_failures`); it does **not** mean the
harness errored. Detailed per-`{service,endpoint}` percentiles live on each Grafana Cloud run page
(`https://eliebussod.grafana.net/a/k6-app/runs/<id>`).

| # | Scenario | Run id | Model | Result | VUh | Outcome |
|---|---|---|---|---|---|---|
| 1 | smoke | 7642401 | 1 VU, browse+writes | ✅ pass | 1.0 | Chain validated end-to-end (ROPC mint in cloud, reads, writes, edge tagging, `teardown`). |
| 2 | capacity (aggressive) | 7642485 | ramping-VUs→100, **NO_THINK** | ⛔ **aborted @ ~2.5 min** | 14.6 | Origin **timed out at ~30–50 VU**; `origin_errors`>10% → protective abort fired. NO_THINK was too brutal (slams, doesn't map). |
| 3 | capacity (controlled) | 7642559 | ramping-arrival-rate **10→100 req/s** | ⚠️ failed (ran full) | 7.1 | **Knee ≈ 80 req/s**: first timeouts at 3m11s, i.e. as offered load crossed ~80 req/s. Below that, reads served fine. |
| 4 | load (full journey) | 7642605 | ramp→50 VU + writes, think-time | ⚠️ failed | 10.7 | **Many timeouts** even at ~10–25 req/s sustained mixed read/write — incl. on `/events` listing, `/featured`, `/{id}`, `/comments`. |
| 5 | spike | 7642690 | burst→80 VU, 40 s, think-time | ⚠️ failed | 1.9 | **Only 1 timeout** — a short burst is largely absorbed (queue drains). |
| 6 | notification_lag | 7642713 | cross-VU follow, 10/min, 5 min | ✅ pass | 2.5 | `notification_lag` measured (A follows B → poll B's unread). Within the p95<30 s SLO. |
| 7 | soak | 7642814 | 20 VU, 30 min, think-time | ⚠️ failed | 17.5 | **9 timeouts over 30 min** — mostly stable at low sustained load; sporadic blips, no crash/leak signal. |

**Total ≈ 55 VUh of the 500 VUh/month trial budget.** Prod returned to `200 @ ~0.11 s` after **every**
run (it recovered each time). Final cleanup verified: `search?q=LOADTEST` → `[]`.

## Key findings

1. **The binding bottleneck is the single-pod, low-resource topology — not Cloudflare, not the rate
   limits.** From one IP, the origin/tunnel **starts timing out at ~80 req/s** (controlled ramp) and
   even moderate sustained mixed load (~50 VU, ~10–25 req/s) produces timeouts. This is *below* test
   #1's edge per-IP shedding wall (~145 req/s). So the origin saturates **first**; the Cloudflare
   per-IP 429 shedding we set out to bypass is **not** the limiting factor under real load.
   Root cause (structural, from `helm/`): everything `replicas: 1` (cloudflared 1, Kong 1, each
   Quarkus service 1), tiny requests (cpu 50–100m, no limits), single cloudflared tunnel pod.

2. **Multi-IP was both unavailable and moot.** The Grafana Cloud **Free plan caps a test to 1 load
   zone** (multi-zone needs a paid plan); and even if we had it, the origin knee (~80 req/s) is hit
   before the edge per-IP limit, so spreading client IPs would not have raised the ceiling for *this*
   topology. The single Frankfurt datacenter IP showed **no better** ceiling than test #1's campus NAT.

3. **Failure mode flipped vs test #1.** Test #1 (campus NAT, think-time, up to 200 VU) saw a healthy
   app (p95 ~470 ms) with the **edge** shedding excess per-IP (429). Test #2's aggressive NO_THINK
   pushed offered req/s high enough to saturate the **origin into timeouts** (status 0) instead. The
   distinction matters: 429 = "edge protecting the app"; timeout = "the app/tunnel itself is underwater".

4. **Bursts are absorbed; sustained load is not.** The spike (80 VU for 40 s) produced a single
   timeout, but sustained load (load, soak) produced steady timeouts — pointing at limited concurrency
   / queue depth at the single cloudflared + single Kong + single-pod services, not a hard rps wall.

5. **`notification_lag` is measurable cross-VU** (user A follows user B, poll B's `X-Unread-Count`).
   The scenario passed its p95<30 s SLO. (Exact value: read it on run 7642713 in the UI.)

6. **The protective abort works.** On the aggressive ramp the `origin_errors>10%` threshold
   auto-stopped the test within ~50 s of timeouts starting, and prod recovered seconds later — the
   safety design did its job on the live site.

## Comparison to test #1 (one-IP, campus NAT, with think-time)

| | Test #1 | Test #2 |
|---|---|---|
| Generator | `unige-debian` (campus NAT IP) | Grafana Cloud k6, 1 zone (Frankfurt AWS IP) |
| Headline failure | edge **429** shedding ~145 req/s; app healthy (p95 ~470 ms) | **origin timeouts** from ~80 req/s; sustained load times out |
| Limiting factor | per-IP edge rate-limit | **single-pod origin/tunnel capacity** |
| `GET /api/events` | throttled 10/min (the bug) | **fixed** — no 429 |

> Caveat: v2's 7 runs were **sequential**; prod recovered to 200 between them but may not have fully
> reset connection pools / GC, so the gentle `load` timeouts may carry some cumulative-stress effect.

## Limitations (be explicit — these bound every conclusion)

- **Single load zone** (Free plan): the multi-IP premise of v2 could not be exercised. So we cannot
  distinguish "per-IP edge limit" from "global limit" experimentally; we only know the origin knee.
- **Client-side observability only** (no kubectl / no server Prometheus): bottleneck attribution is
  **inferential** — "single-pod origin/tunnel" is deduced from timeouts + recovery + topology, **not**
  proven with CPU/heap/GC/DB-pool/Kafka-lag. We cannot say *which* pod (Kong vs a service vs Postgres
  vs cloudflared) is the first to fall.
- **Plan caps:** 100 VUs/test, 1 h/test, 500 VUh/month — so peaks/soak were sized down; the true knee
  may sit above what we could reach, and we never sustained >100 concurrent VUs.
- **Cloudflare uncontrolled:** runs go through the edge; we classify edge responses but cannot bypass
  bot mitigation. Numbers are user-facing, edge-included.
- **Detailed percentiles** (p95/p99 per endpoint, `rate_limited`/`edge_blocks` counts) were **not
  extractable via the k6 Cloud REST API** (undocumented OData `get_aggregate` returns null without an
  unknown param). They are available on the Grafana Cloud UI run pages listed above.

## Safety / cleanup

- Headline load read-only; writes only on `[LOADTEST]` content; protective `origin_errors` abort.
- **Teardown was incomplete after the soak** (lesson): under prod degradation, the in-test
  `teardown()` DELETEs timed out, leaving ~131 `[LOADTEST]` events. Manual cleanup was required and
  surfaced two issues to fix in the harness: (a) `GET /users/me/events?size=200` → **HTTP 400** (page
  size cap; use ≤50), and (b) `DELETE /events/{id}` right after `PATCH .../cancel` can **409** (needs a
  short delay; cancel→delete is not atomic). A targeted owner-by-owner pass (reliable token mint +
  cancel→sleep→delete) cleaned prod to `search?q=LOADTEST` → `[]`.

## Recommendations

- **For capacity head-room, the fix is infra, not the test:** scale cloudflared (>1 pod) and Kong
  (>1 replica, switching `rate-limiting policy: local → redis`), and raise service CPU/replicas
  (note `event-service` is pinned to 1 by ADR-001 → needs leader election first). These are the
  follow-ups already flagged in PR #219 / `RESULTS.md`.
- **Harden `teardown()`**: page size ≤50; add a delay/retry between cancel and delete; consider an
  out-of-band cleanup script (we now have one pattern) that runs even if a test aborts.
- For a genuine multi-IP capacity number, a **paid k6 plan** (multi-zone) **and** server-side metrics
  (cluster Prometheus → remote_write to Grafana Cloud) would be required — both out of scope here.
