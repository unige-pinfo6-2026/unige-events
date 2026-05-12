package ch.unige.events.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Application-scoped state for the per-user rate limiter.
 *
 * <p>Holds a Caffeine-backed {@code (annotationName + "|" + principalName) →
 * Bucket} cache and a swappable time source. Lives separately from
 * {@link RateLimitInterceptor} because CDI requires interceptors to be
 * {@code @Dependent}-scoped — but the bucket cache must be a singleton, or
 * each invocation would get its own empty buckets and the limit would never
 * trigger.
 *
 * <p>Caffeine evicts entries idle for &gt;5 minutes, so cold/sporadic users
 * don't pin memory. Recreating a bucket on next call is behaviourally
 * equivalent to a fresh window — exactly what we want for cold users.
 *
 * <p>Time is read through an {@link AtomicReference} of {@link LongSupplier}
 * (atomic so swap during a concurrent test is safe). Production uses
 * {@link Clock#systemUTC()}; tests inject a fake clock via
 * {@link #setNowMillisSupplier} to exercise window expiration without
 * {@code Thread.sleep}.
 */
@Singleton
public class RateLimitState {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(100_000)
            .build();

    private final AtomicReference<LongSupplier> nowMillisSupplier =
            new AtomicReference<>(() -> Clock.systemUTC().millis());

    /**
     * @return 0 if the call is permitted; else the number of seconds the
     *         caller must wait before retrying (always {@code >= 1}).
     */
    public long tryAcquire(String annotationName, String principalKey,
                           int max, int windowSeconds) {
        String bucketKey = annotationName + "|" + principalKey;
        long windowMillis = windowSeconds * 1000L;
        long now = nowMillisSupplier.get().getAsLong();
        Bucket bucket = buckets.get(bucketKey, k -> new Bucket());
        return bucket.tryAcquire(max, windowMillis, now);
    }

    /** Replaces the time source used to compute window boundaries. Tests only. */
    public void setNowMillisSupplier(LongSupplier supplier) {
        this.nowMillisSupplier.set(supplier);
    }

    /** Drops every cached bucket. Tests only. */
    public void clearBuckets() {
        buckets.invalidateAll();
    }

    /**
     * Fixed-window counter. Reset semantics: when {@code now - windowStart >=
     * windowMillis}, the bucket starts a fresh window with count = 1.
     * Otherwise the count is incremented; if it exceeds {@code max}, the call
     * is rejected and {@code retryAfter = windowEnd - now} (rounded up to
     * whole seconds).
     */
    public static final class Bucket {
        private final AtomicLong windowStart = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong count = new AtomicLong(0);

        /** @return 0 if the call is permitted; else the seconds until the window resets. */
        public synchronized long tryAcquire(int max, long windowMillis, long now) {
            long start = windowStart.get();
            if (start == Long.MIN_VALUE || now - start >= windowMillis) {
                windowStart.set(now);
                count.set(1);
                return 0;
            }
            long current = count.incrementAndGet();
            if (current <= max) {
                return 0;
            }
            long remainingMillis = windowMillis - (now - start);
            // Round up so a sub-second remainder is never reported as 0 — clients
            // would otherwise hammer immediately and re-trigger the same 429.
            long retryAfterSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            // Decrement so callers that retry within the window aren't penalised
            // by accumulated overflow — the next allowed call will pass cleanly
            // when the window resets.
            count.decrementAndGet();
            return retryAfterSeconds;
        }
    }
}
