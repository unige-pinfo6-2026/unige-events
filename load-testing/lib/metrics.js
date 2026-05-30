// Custom metrics (see specs §9/§10). Defined once at module scope.
import { Trend, Counter, Rate } from 'k6/metrics';

export const endpointLatency = new Trend('endpoint_latency', true); // ms, tagged {endpoint,service,method}
export const rateLimited = new Counter('rate_limited');             // Kong 429s, tagged {bucket}
export const unexpectedFailures = new Rate('unexpected_failures');  // SLO source (excludes expected 429/404)
export const notificationLag = new Trend('notification_lag', true); // ms, Kafka -> notification visibility, tagged {trigger}

// v2 — Cloudflare edge classification (we run THROUGH an uncontrolled edge; see specs §7).
export const edgeBlocks = new Counter('edge_blocks');   // tagged {kind: challenge|edge_429|edge_5xx}
export const edgeCache = new Counter('edge_cache');     // tagged {status: HIT|MISS|DYNAMIC|EXPIRED|...}
export const originErrors = new Rate('origin_errors');  // origin 5xx/timeout only (abort signal; excl. edge 520-527 & 429/404)
