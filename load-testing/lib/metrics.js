// Custom metrics (see spec §10). Defined once at module scope.
import { Trend, Counter, Rate } from 'k6/metrics';

export const endpointLatency = new Trend('endpoint_latency', true); // ms, tagged {endpoint,service,method}
export const rateLimited = new Counter('rate_limited');             // 429s, tagged {bucket}
export const unexpectedFailures = new Rate('unexpected_failures');  // SLO source (excludes expected 429/404)
export const notificationLag = new Trend('notification_lag', true); // ms, Kafka -> notification visibility
