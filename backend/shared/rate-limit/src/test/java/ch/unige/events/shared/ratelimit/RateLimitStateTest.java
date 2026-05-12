package ch.unige.events.shared.ratelimit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitStateTest {

    // =========================================================================
    // Bucket primitive — fixed-window counter
    // =========================================================================

    @Test
    void bucket_allowsCallsUpToMax() {
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, bucket.tryAcquire(10, 60_000L, 1_000L + i),
                    "call " + (i + 1) + " of 10 must be permitted");
        }
    }

    @Test
    void bucket_rejectsCallsBeyondMaxWithRetryAfter() {
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        long start = 5_000L;
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, bucket.tryAcquire(10, 60_000L, start));
        }
        long retry = bucket.tryAcquire(10, 60_000L, start + 1_000L);
        assertTrue(retry > 0L && retry <= 60L, "retry should be 1..60s, got " + retry);
    }

    @Test
    void bucket_resetsAfterWindowElapses() {
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, bucket.tryAcquire(10, 60_000L, 1_000L));
        }
        assertTrue(bucket.tryAcquire(10, 60_000L, 1_500L) > 0L);
        // After windowMillis elapses, the bucket starts fresh.
        assertEquals(0L, bucket.tryAcquire(10, 60_000L, 1_000L + 60_000L));
    }

    @Test
    void bucket_subSecondRemainderIsRoundedUpToOneSecond() {
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        for (int i = 0; i < 5; i++) {
            assertEquals(0L, bucket.tryAcquire(5, 1_000L, 0L));
        }
        // Window almost over (200ms remaining) — must still report >= 1s so a
        // client retrying immediately doesn't loop on rate_limited.
        long retry = bucket.tryAcquire(5, 1_000L, 800L);
        assertEquals(1L, retry);
    }

    @Test
    void bucket_isolationBetweenIndependentBuckets() {
        RateLimitState.Bucket alice = new RateLimitState.Bucket();
        RateLimitState.Bucket bob = new RateLimitState.Bucket();
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, alice.tryAcquire(10, 60_000L, 0L));
        }
        assertTrue(alice.tryAcquire(10, 60_000L, 0L) > 0L,
                "alice's 11th call must be rate limited");
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, bob.tryAcquire(10, 60_000L, 0L),
                    "bob's bucket must not be affected by alice's overflow");
        }
    }

    @RepeatedTest(value = 3, name = "concurrent-bucket-{currentRepetition}")
    void bucket_concurrentCallersConvergeOnExactly_max() throws InterruptedException {
        // The bucket is the only synchronisation point shielding the endpoint
        // from a concurrent burst by the same user; assert no over-admission
        // when many threads race for the budget.
        final int max = 50;
        final int contenders = 200;
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            for (int i = 0; i < contenders; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (bucket.tryAcquire(max, 60_000L, 0L) == 0L) {
                        admitted.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers ready");
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "pool drained");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(max, admitted.get(), "admitted must equal max under contention");
        assertEquals(contenders - max, rejected.get());
    }

    // =========================================================================
    // RateLimitState — Caffeine-backed bucket lookup
    // =========================================================================

    @Test
    void state_sameAnnotationAndPrincipal_sharesBucket() {
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("events.create", "alice", 10, 60));
        }
        // 11th call from same principal on same annotation hits the same bucket → 429.
        assertTrue(state.tryAcquire("events.create", "alice", 10, 60) > 0L);
    }

    @Test
    void state_sameAnnotation_differentPrincipals_independentBuckets() {
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("events.create", "alice", 10, 60));
        }
        // Bob is a different principal — fresh bucket.
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("events.create", "bob", 10, 60));
        }
    }

    @Test
    void state_samePrincipal_differentAnnotations_independentBuckets() {
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("events.create", "alice", 10, 60));
        }
        // Same alice, different annotation — fresh bucket.
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("comments.post", "alice", 10, 60));
        }
    }

    @Test
    void state_clearBuckets_resetsAllState() {
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        for (int i = 0; i < 10; i++) {
            state.tryAcquire("events.create", "alice", 10, 60);
        }
        assertTrue(state.tryAcquire("events.create", "alice", 10, 60) > 0L);
        state.clearBuckets();
        // After clear, the bucket is recreated empty — call passes again.
        assertEquals(0L, state.tryAcquire("events.create", "alice", 10, 60));
    }

    @Test
    void state_fakeClockDrivesWindowReset() {
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("events.create", "alice", 10, 60));
        }
        assertTrue(state.tryAcquire("events.create", "alice", 10, 60) > 0L);
        // Advance the fake clock past the 60s window — the bucket resets.
        fakeNow.set(60_000L);
        assertEquals(0L, state.tryAcquire("events.create", "alice", 10, 60));
    }

    @Test
    void state_defaultClockReturnsRealTime() {
        // Sanity: without a fake clock the supplier returns wall-clock millis.
        // Exercise it by making a single call (must not throw, must return 0).
        RateLimitState state = new RateLimitState();
        assertEquals(0L, state.tryAcquire("any", "alice", 10, 60));
    }

    @Test
    void state_pipeSeparator_avoidsKeyCollisionAcrossBuckets() {
        // Implementation detail check: bucketKey = name + "|" + principal.
        // Two pairs that would collide if joined by "" must be distinct here.
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        // ("foo", "bar") vs ("foob", "ar") — both yield "foo|bar" / "foob|ar"
        // → separate buckets. Without the pipe both would key on "foobar".
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("foo", "bar", 10, 60));
        }
        // The other pair must not have been counted against — gets its own
        // 10-call budget despite the lexically adjacent join.
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, state.tryAcquire("foob", "ar", 10, 60));
        }
    }

    @Test
    void state_distinctBucketsHaveDistinctIdentity() {
        // Indirect check that the cache returns distinct Bucket instances
        // for distinct keys: hammer two principals through their own budget
        // independently and assert both saturate exactly at max.
        RateLimitState state = new RateLimitState();
        AtomicLong fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
        long aliceFirstReject = -1L;
        for (int i = 0; i < 11; i++) {
            long r = state.tryAcquire("events.create", "alice", 10, 60);
            if (r > 0L && aliceFirstReject < 0L) aliceFirstReject = i;
        }
        assertEquals(10L, aliceFirstReject);
        long bobFirstReject = -1L;
        for (int i = 0; i < 11; i++) {
            long r = state.tryAcquire("events.create", "bob", 10, 60);
            if (r > 0L && bobFirstReject < 0L) bobFirstReject = i;
        }
        assertEquals(10L, bobFirstReject,
                "bob's bucket must reject on its own 11th call, independent of alice's");
        assertNotEquals(-1L, aliceFirstReject);
    }
}
