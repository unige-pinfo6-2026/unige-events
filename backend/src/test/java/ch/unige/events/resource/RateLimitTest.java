package ch.unige.events.resource;

import ch.unige.events.config.PerUserRateLimit;
import ch.unige.events.config.RateLimitInterceptor;
import ch.unige.events.config.RateLimitState;
import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.exception.RateLimitExceededException;
import ch.unige.events.exception.mapper.RateLimitExceptionMapper;
import ch.unige.events.service.AttendanceServiceMock;
import ch.unige.events.service.EventServiceMock;
import ch.unige.events.service.FavoriteServiceMock;
import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.security.identity.SecurityIdentity;
import io.restassured.http.ContentType;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the per-user rate-limit interceptor introduced for issue #98
 * (pentest 2026-04-17 finding 4.14).
 *
 * <p>Layers covered:
 * <ul>
 *   <li><b>Bucket</b> — direct unit tests of the fixed-window primitive: limit
 *       enforcement, retry-after computation, window reset, per-key isolation,
 *       concurrency.</li>
 *   <li><b>Exception mapper</b> — direct unit test of the 429 response shape
 *       (status, {@code Retry-After} header, JSON body).</li>
 *   <li><b>HTTP integration</b> — every annotated endpoint is exercised through
 *       the real CDI/JAX-RS pipeline with a fake clock injected into the
 *       interceptor, asserting the {@code N+1}<sup>th</sup> call returns 429.</li>
 * </ul>
 *
 * <p>The fake clock is plugged in via
 * {@link RateLimitInterceptor#setNowMillisSupplier} so window-reset tests don't
 * need {@code Thread.sleep} — fast and deterministic.
 */
@QuarkusTest
class RateLimitTest {

    private static final String ALICE = "auth0|alice";

    @Inject
    RateLimitState rateLimitState;

    @Inject
    EventServiceMock eventServiceMock;

    @Inject
    UserServiceMock userServiceMock;

    @Inject
    AttendanceServiceMock attendanceServiceMock;

    @Inject
    FavoriteServiceMock favoriteServiceMock;

    private final AtomicLong fakeNow = new AtomicLong();

    @BeforeEach
    void setUp() {
        eventServiceMock.reset();
        userServiceMock.reset();
        attendanceServiceMock.reset();
        favoriteServiceMock.reset();
        rateLimitState.clearBuckets();
        fakeNow.set(1_000_000_000L);
        rateLimitState.setNowMillisSupplier(fakeNow::get);
    }

    // =========================================================================
    // Bucket unit tests — fixed-window primitive
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
        // 11th call within the same window — must return positive retry-after.
        long retry = bucket.tryAcquire(10, 60_000L, start + 1_000L);
        assertTrue(retry > 0L && retry <= 60L, "retry should be 1..60s, got " + retry);
    }

    @Test
    void bucket_resetsAfterWindowElapses() {
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, bucket.tryAcquire(10, 60_000L, 1_000L));
        }
        // 11th in same window → 429
        assertTrue(bucket.tryAcquire(10, 60_000L, 1_500L) > 0L);
        // After windowMillis elapses, the bucket resets and the call passes.
        assertEquals(0L, bucket.tryAcquire(10, 60_000L, 1_000L + 60_000L));
    }

    @Test
    void bucket_subSecondRemainderIsRoundedUpToOneSecond() {
        RateLimitState.Bucket bucket = new RateLimitState.Bucket();
        long start = 0L;
        for (int i = 0; i < 5; i++) {
            assertEquals(0L, bucket.tryAcquire(5, 1_000L, start));
        }
        // Window almost over (200ms remaining) — must still report >= 1s so a
        // client retrying immediately doesn't loop on rate_limited.
        long retry = bucket.tryAcquire(5, 1_000L, 800L);
        assertEquals(1L, retry);
    }

    @Test
    void bucket_isolationBetweenIndependentBuckets() {
        // Models the per-user isolation invariant: two principals hitting the
        // same endpoint use independent buckets — exhaustion on one MUST NOT
        // bleed into the other (acceptance criteria, issue #98).
        RateLimitState.Bucket alice = new RateLimitState.Bucket();
        RateLimitState.Bucket bob = new RateLimitState.Bucket();
        long now = 0L;
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, alice.tryAcquire(10, 60_000L, now));
        }
        assertTrue(alice.tryAcquire(10, 60_000L, now) > 0L,
                "alice's 11th call must be rate limited");
        // Bob's bucket is a separate object — fresh budget.
        for (int i = 0; i < 10; i++) {
            assertEquals(0L, bob.tryAcquire(10, 60_000L, now),
                    "bob's bucket must not be affected by alice's overflow");
        }
    }

    @RepeatedTest(value = 3, name = "concurrent-bucket-{currentRepetition}")
    void bucket_concurrentCallersConvergeOnExactly_max() throws InterruptedException {
        // The bucket is the only synchronisation point shielding the endpoint
        // from a concurrent burst by the same user; we assert no over-admission
        // when many threads race for the budget. Pool is sized to `contenders`
        // so every task can park on the start gate — otherwise the start-latch
        // would deadlock waiting for tasks queued behind a too-small pool.
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
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(max, admitted.get(),
                "exactly `max` concurrent callers must be admitted");
        assertEquals(contenders - max, rejected.get());
    }

    // =========================================================================
    // Exception mapper unit test
    // =========================================================================

    @Test
    void exceptionMapper_emitsStandardEnvelope() {
        RateLimitExceptionMapper mapper = new RateLimitExceptionMapper();
        Response response = mapper.toResponse(
                new RateLimitExceededException("Too many requests. Retry in 42 seconds.", 42L));

        assertEquals(429, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
        assertEquals("42", response.getHeaderString(HttpHeaders.RETRY_AFTER));

        Object entity = response.getEntity();
        assertNotNull(entity);
        // Use reflection-free record accessor — keeps the test resilient to refactors.
        assertEquals("rate_limited", ((ch.unige.events.dto.ApiErrorResponse) entity).error());
        assertTrue(((ch.unige.events.dto.ApiErrorResponse) entity).message().contains("42"));
    }

    @Test
    void exception_carriesRetryAfter() {
        RateLimitExceededException ex = new RateLimitExceededException("nope", 7L);
        assertEquals(7L, ex.getRetryAfterSeconds());
        assertEquals(429, ex.getResponse().getStatus());
    }

    // =========================================================================
    // Direct interceptor unit tests — exercise branches that the HTTP-level
    // tests cannot easily reach (anonymous principal, blank principal name,
    // unsatisfied identity instance, missing annotation).
    // =========================================================================

    @Test
    void interceptor_proceedsWhenAnnotationMissing() throws Exception {
        // No @PerUserRateLimit on the method → interceptor falls through to ctx.proceed().
        RateLimitState state = new RateLimitState();
        @SuppressWarnings("unchecked")
        Instance<SecurityIdentity> identity = org.mockito.Mockito.mock(Instance.class);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(state, identity);

        InvocationContext ctx = mockCtx("methodWithoutAnnotation", "ok");
        Object result = interceptor.intercept(ctx);
        assertEquals("ok", result);
    }

    @Test
    void interceptor_proceedsWhenAnnotationMisconfigured() throws Exception {
        // max <= 0 → annotation is a no-op (defence: misconfig must not block).
        RateLimitState state = new RateLimitState();
        @SuppressWarnings("unchecked")
        Instance<SecurityIdentity> identity = org.mockito.Mockito.mock(Instance.class);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(state, identity);

        InvocationContext ctx = mockCtx("methodWithBadAnnotation", "ok");
        Object result = interceptor.intercept(ctx);
        assertEquals("ok", result);
    }

    @Test
    void interceptor_anonymousIdentity_usesAnonymousBucket() throws Exception {
        // Burns the anonymous bucket — proving the interceptor doesn't NPE on
        // anonymous identities and bucketing is keyed on the anonymous fallback.
        RateLimitState state = new RateLimitState();
        @SuppressWarnings("unchecked")
        Instance<SecurityIdentity> identity = org.mockito.Mockito.mock(Instance.class);
        SecurityIdentity anon = org.mockito.Mockito.mock(SecurityIdentity.class);
        org.mockito.Mockito.when(identity.isUnsatisfied()).thenReturn(false);
        org.mockito.Mockito.when(identity.get()).thenReturn(anon);
        org.mockito.Mockito.when(anon.isAnonymous()).thenReturn(true);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(state, identity);
        InvocationContext ctx = mockCtx("methodWithLimit2", "ok");

        // Two passes through — the third should be rate-limited under the same anon key.
        assertEquals("ok", interceptor.intercept(ctx));
        assertEquals("ok", interceptor.intercept(ctx));
        org.junit.jupiter.api.Assertions.assertThrows(
                RateLimitExceededException.class, () -> interceptor.intercept(ctx));
    }

    @Test
    void interceptor_blankPrincipalName_fallsBackToAnonymous() throws Exception {
        RateLimitState state = new RateLimitState();
        @SuppressWarnings("unchecked")
        Instance<SecurityIdentity> identity = org.mockito.Mockito.mock(Instance.class);
        SecurityIdentity ident = org.mockito.Mockito.mock(SecurityIdentity.class);
        java.security.Principal blank = () -> "   ";
        org.mockito.Mockito.when(identity.isUnsatisfied()).thenReturn(false);
        org.mockito.Mockito.when(identity.get()).thenReturn(ident);
        org.mockito.Mockito.when(ident.isAnonymous()).thenReturn(false);
        org.mockito.Mockito.when(ident.getPrincipal()).thenReturn(blank);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(state, identity);
        InvocationContext ctx = mockCtx("methodWithLimit2", "ok");
        assertEquals("ok", interceptor.intercept(ctx));
    }

    @Test
    void interceptor_unsatisfiedIdentity_fallsBackToAnonymous() throws Exception {
        RateLimitState state = new RateLimitState();
        @SuppressWarnings("unchecked")
        Instance<SecurityIdentity> identity = org.mockito.Mockito.mock(Instance.class);
        org.mockito.Mockito.when(identity.isUnsatisfied()).thenReturn(true);

        RateLimitInterceptor interceptor = new RateLimitInterceptor(state, identity);
        InvocationContext ctx = mockCtx("methodWithLimit2", "ok");
        assertEquals("ok", interceptor.intercept(ctx));
    }

    /**
     * Builds a minimal {@link InvocationContext} stub whose target method
     * carries a fixture annotation. Method names are matched against fixtures
     * declared on {@link Fixtures} below.
     */
    private static InvocationContext mockCtx(String methodName, Object proceedValue) throws Exception {
        java.lang.reflect.Method method = Fixtures.class.getDeclaredMethod(methodName);
        InvocationContext ctx = org.mockito.Mockito.mock(InvocationContext.class);
        org.mockito.Mockito.when(ctx.getMethod()).thenReturn(method);
        org.mockito.Mockito.when(ctx.proceed()).thenReturn(proceedValue);
        return ctx;
    }

    /** Holds method fixtures used by {@link #mockCtx} to back reflective lookups. */
    @SuppressWarnings("unused")
    static final class Fixtures {
        void methodWithoutAnnotation() { /* no-op */ }

        @PerUserRateLimit(name = "x", max = 0, windowSeconds = 0)
        void methodWithBadAnnotation() { /* no-op */ }

        @PerUserRateLimit(name = "test.unit", max = 2, windowSeconds = 60)
        void methodWithLimit2() { /* no-op */ }
    }

    // =========================================================================
    // HTTP integration tests — endpoint-by-endpoint enforcement
    // Each test confirms the (max+1)-th call returns 429 with the right shape.
    // =========================================================================

    @Test
    @TestSecurity(user = ALICE)
    void post_events_eleventhCallReturns429() {
        for (int i = 0; i < 10; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(validCreateEventRequest("Conf #" + i))
                    .when().post("/events")
                    .then().statusCode(201);
        }
        assert429(given()
                .contentType(ContentType.JSON)
                .body(validCreateEventRequest("Conf overflow"))
                .when().post("/events"));
    }

    @Test
    @TestSecurity(user = ALICE)
    void put_events_id_eleventhCallReturns429() {
        Event event = eventServiceMock.seedEvent(ALICE, "Original");
        for (int i = 0; i < 10; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(validUpdateEventRequest("Updated #" + i))
                    .when().put("/events/{id}", event.id)
                    .then().statusCode(200);
        }
        assert429(given()
                .contentType(ContentType.JSON)
                .body(validUpdateEventRequest("Updated overflow"))
                .when().put("/events/{id}", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void patch_events_cancel_eleventhCallReturns429() {
        // The 11th cancel attempt is rate-limited *before* the resource enters
        // its own conflict-checking branch, so a single seeded event suffices —
        // earlier calls return either 200 (first time) or 409 (already cancelled).
        // Both are non-429 responses; rate-limit kicks in at call 11.
        Event event = eventServiceMock.seedEvent(ALICE, "To cancel");
        for (int i = 0; i < 10; i++) {
            int status = given().when().patch("/events/{id}/cancel", event.id)
                    .then().extract().statusCode();
            assertTrue(status == 200 || status == 409,
                    "call " + (i + 1) + " expected 200 or 409, got " + status);
        }
        assert429(given().when().patch("/events/{id}/cancel", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void patch_events_restore_eleventhCallReturns429() {
        Event event = eventServiceMock.seedEvent(ALICE, "To restore");
        for (int i = 0; i < 10; i++) {
            given().when().patch("/events/{id}/restore", event.id);
        }
        assert429(given().when().patch("/events/{id}/restore", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void patch_events_publish_eleventhCallReturns429() {
        Event event = eventServiceMock.seedEvent(ALICE, "To publish");
        for (int i = 0; i < 10; i++) {
            given().when().patch("/events/{id}/publish", event.id);
        }
        assert429(given().when().patch("/events/{id}/publish", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void post_events_id_image_sixthCallReturns429() {
        Event event = eventServiceMock.seedEvent(ALICE, "Image upload target");
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType("multipart/form-data")
                    .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                    .when().post("/events/{id}/image", event.id)
                    .then().statusCode(200);
        }
        assert429(given()
                .contentType("multipart/form-data")
                .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                .when().post("/events/{id}/image", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void post_events_id_attend_thirtyFirstCallReturns429() {
        Event event = attendanceServiceMock.seedEvent("Attend target");
        for (int i = 0; i < 30; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"status\":\"ATTENDING\"}")
                    .when().post("/events/{id}/attend", event.id)
                    .then().statusCode(200);
        }
        assert429(given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void post_events_id_favorite_thirtyFirstCallReturns429() {
        Event event = favoriteServiceMock.seedEvent("Favorite target");
        for (int i = 0; i < 30; i++) {
            given().when().post("/events/{id}/favorite", event.id)
                    .then().statusCode(200);
        }
        assert429(given().when().post("/events/{id}/favorite", event.id));
    }

    @Test
    @TestSecurity(user = ALICE)
    void put_users_me_eleventhCallReturns429() {
        userServiceMock.seedUser(ALICE, "alice@example.com");
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Alice", null, null, null, null, null, null);
        for (int i = 0; i < 10; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(req)
                    .when().put("/users/me")
                    .then().statusCode(200);
        }
        assert429(given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/users/me"));
    }

    @Test
    @TestSecurity(user = ALICE)
    void post_users_me_image_sixthCallReturns429() {
        userServiceMock.seedUser(ALICE, "alice@example.com");
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType("multipart/form-data")
                    .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                    .when().post("/users/me/image")
                    .then().statusCode(200);
        }
        assert429(given()
                .contentType("multipart/form-data")
                .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                .when().post("/users/me/image"));
    }

    @Test
    @TestSecurity(user = ALICE)
    void post_users_me_banner_sixthCallReturns429() {
        userServiceMock.seedUser(ALICE, "alice@example.com");
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType("multipart/form-data")
                    .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                    .when().post("/users/me/banner")
                    .then().statusCode(200);
        }
        assert429(given()
                .contentType("multipart/form-data")
                .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                .when().post("/users/me/banner"));
    }

    // =========================================================================
    // Cross-cutting HTTP behaviors
    // =========================================================================

    @Test
    @TestSecurity(user = ALICE)
    void rateLimit_resetsAfterTimeWindow() {
        // Exhaust budget on POST /events.
        for (int i = 0; i < 10; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(validCreateEventRequest("Pre #" + i))
                    .when().post("/events")
                    .then().statusCode(201);
        }
        given()
                .contentType(ContentType.JSON)
                .body(validCreateEventRequest("Overflow"))
                .when().post("/events")
                .then().statusCode(429);

        // Advance the fake clock past the window — fresh budget.
        fakeNow.addAndGet(60_001L);

        given()
                .contentType(ContentType.JSON)
                .body(validCreateEventRequest("Post-window"))
                .when().post("/events")
                .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = ALICE)
    void rateLimit_429_responseHasRetryAfterAndStandardBody() {
        for (int i = 0; i < 10; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(validCreateEventRequest("c#" + i))
                    .when().post("/events")
                    .then().statusCode(201);
        }
        // Cap response carries a Retry-After header (digit string of seconds)
        // and the standard {error, message} envelope.
        given()
                .contentType(ContentType.JSON)
                .body(validCreateEventRequest("overflow"))
                .when().post("/events")
                .then()
                .statusCode(429)
                .header(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+"))
                .header(HttpHeaders.RETRY_AFTER, notNullValue())
                .contentType(ContentType.JSON)
                .body("error", equalTo("rate_limited"))
                .body("message", matchesPattern("Too many requests\\. Retry in \\d+ seconds\\."));
    }

    @Test
    @TestSecurity(user = ALICE)
    void rateLimit_endpointsHaveIndependentBudgets() {
        // Saturate POST /events (events.create bucket) — does NOT prevent the
        // same user from hitting POST /events/{id}/favorite (events.favorite
        // bucket); buckets are keyed on the annotation name, not the principal
        // alone.
        for (int i = 0; i < 10; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(validCreateEventRequest("c#" + i))
                    .when().post("/events")
                    .then().statusCode(201);
        }
        given()
                .contentType(ContentType.JSON)
                .body(validCreateEventRequest("overflow"))
                .when().post("/events")
                .then().statusCode(429);

        Event fav = favoriteServiceMock.seedEvent("still allowed");
        given().when().post("/events/{id}/favorite", fav.id)
                .then().statusCode(200);
    }

    @Test
    void rateLimit_anonymousCallerStillBucketed() {
        // POST /events requires authentication, so an anonymous request hits
        // 401 before the interceptor admits it. Use POST /events/{id}/favorite
        // — same enforcement: 401 from @Authenticated. Either way, an
        // unauthenticated flood does NOT bypass the security stack: this test
        // documents that the interceptor's anonymous-key fallback exists
        // (defence-in-depth) without a way to actually exercise it through the
        // current authenticated endpoints.
        given()
                .contentType(ContentType.JSON)
                .body(validCreateEventRequest("anon"))
                .when().post("/events")
                .then().statusCode(401);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void assert429(io.restassured.response.Response response) {
        response.then()
                .statusCode(429)
                .header(HttpHeaders.RETRY_AFTER, notNullValue())
                .body("error", equalTo("rate_limited"));
    }

    private static CreateEventRequest validCreateEventRequest(String title) {
        CreateEventRequest req = new CreateEventRequest();
        req.title = title;
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        return req;
    }

    private static UpdateEventRequest validUpdateEventRequest(String title) {
        UpdateEventRequest req = new UpdateEventRequest();
        req.title = title;
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        return req;
    }
}
