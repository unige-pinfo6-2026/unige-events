# Load test results — UNIGE Events (production)

> Run 2026-05-29 with Grafana k6 v2.0.0 from a single load generator (`unige-debian`) against
> `https://pinfo6.p-info.net`. Auth via the ROPC token pool (50 seeded test users). Methodology and
> scripts: [`specs_load-testing-k6.md`](specs_load-testing-k6.md) + `scenarios/`.

## Runs

| Run | Profile | Peak VUs | Reqs | Throughput | p95 | p99 | max | unexpected_failures (SLO) | 429 | checks |
|---|---|---|---|---|---|---|---|---|---|---|
| `load` | nominal, full journey | 50 | 31 786 | 49.5/s | 532 ms | n/a¹ | **50.5 s** | **29.0 %** | 9 302 | 70.9 % |
| `stress-safe` | 50→200, un-throttled reads | 200 | 59 887 | 99.6/s (peak ~145/s) | 473 ms | 769 ms | **60.0 s** | **10.8 %** | 6 439 | 89.2 % |

¹ `p(99)` wasn't in k6's default summary for the first run; `summaryTrendStats` was added afterwards.

## Key findings

1. **Kong rate-limit is mis-scoped on `GET /api/events` (10/min per IP).** The `rate-limiting` plugin
   on the `/api/events` route is **not method-restricted**, so it throttles the **GET listing**
   exactly like `POST /events` create (`minute: 10`, `policy: local`). Proof (concurrent burst of 40):
   - `GET /api/events?status=PUBLISHED` → 10× `200` then `429` (`RateLimit-Limit: 10`, `Retry-After: 34`)
   - `GET /api/events/featured` → 40× `200`  ·  `GET /api/events/search` → 40× `200`  ·  `GET /api/events/{id}` → `404`
   **Production impact:** behind a shared NAT (e.g. the UNIGE campus network) *all* users share ~10
   event-list requests/min — the main browse page becomes unusable under shared load. Almost certainly
   unintended (only `POST /events` should be limited). Source: `helm/templates/kong/configmap-routes.yaml`
   + `docker/kong.yml` (`events-list` route). **A fix task has been spun off.**

2. **Edge-level per-IP shedding under sustained load.** Even on un-throttled endpoints, sustained
   ~145 req/s from a single IP produced ~10.8 % `429` plus a thin tail of 60 s timeouts. All responses
   carry `Server: cloudflare` + `cf-ray` → the ceiling is imposed at the **edge / per source IP**, not
   by the application.

3. **App + tunnel latency is healthy when not shed.** Successful requests at 200 VU held **p95 ≈ 470 ms,
   p99 ≈ 770 ms**. There is **no app/DB latency collapse** up to 200 VU on read endpoints — the limiting
   factor is rate-limiting / per-IP shedding, not CPU/DB throughput.

4. **Tail latency / saturation signal.** Both runs show a tail reaching the k6 60 s timeout, pointing at
   queuing in the **single-pod cloudflared tunnel** and/or **single Kong replica** under burst.

5. **Methodology limit (no silent cap).** Because the dominant constraints are *per-IP* rate limits,
   meaningful read-**throughput** / capacity numbers cannot be obtained from one source IP. They require
   either a dedicated env with the rate limits raised/removed, or load distributed across many IPs
   (e.g. k6 distributed / Grafana Cloud).

6. **`notification_lag` not measured.** Self-actions don't notify the actor, so the Kafka→notification
   lag needs cross-VU coordination (two test users acting on each other's content) — left as future work.

## Recommendations

- **Fix the Kong `rate-limiting` scoping** so it applies to `POST /events` only (method-scoped route or
  a dedicated create route), and review the `follows.follow` route the same way. → spun-off task.
- For real capacity figures: run against a **preview/staging** deployment with rate limits raised, and
  distribute the generator across multiple IPs.
- Investigate **scaling the cloudflared tunnel** (1 pod) and **Kong** (`replicas: 1`) for burst
  resilience — the 50–60 s tail suggests edge queuing under spikes.

## Safety / cleanup

Headline load was **read-only** (no pollution). The `writes` trickle created only `[LOADTEST]`-tagged
events under the test accounts (and mostly hit the `events.create` 10/min cap, so very few persisted);
`teardown()` cancelled+deleted them and undid test-to-test follows. The `stress-safe` run was anonymous
read-only (nothing to clean).
