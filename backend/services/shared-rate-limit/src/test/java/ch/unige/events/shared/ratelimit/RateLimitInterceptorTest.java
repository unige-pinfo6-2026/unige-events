package ch.unige.events.shared.ratelimit;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.Instance;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    /** Fixture target class — annotated method-level. */
    @SuppressWarnings("unused")
    static class MethodAnnotated {
        @PerUserRateLimit(name = "events.create", max = 2, windowSeconds = 60)
        public String limited() { return "ok"; }

        public String notLimited() { return "ok"; }

        @PerUserRateLimit(name = "events.publish", max = 0, windowSeconds = 60)
        public String misconfiguredZeroMax() { return "ok"; }

        @PerUserRateLimit(name = "events.publish", max = 5, windowSeconds = 0)
        public String misconfiguredZeroWindow() { return "ok"; }
    }

    /** Fixture target class — annotated class-level. */
    @PerUserRateLimit(name = "users.updateMe", max = 2, windowSeconds = 60)
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public String anyMethod() { return "ok"; }
    }

    private RateLimitState state;
    private AtomicLong fakeNow;

    @BeforeEach
    void setUp() {
        state = new RateLimitState();
        fakeNow = new AtomicLong(0L);
        state.setNowMillisSupplier(fakeNow::get);
    }

    private RateLimitInterceptor newInterceptor(SecurityIdentity identity) {
        @SuppressWarnings("unchecked")
        Instance<SecurityIdentity> instance = Mockito.mock(Instance.class);
        if (identity == null) {
            when(instance.isUnsatisfied()).thenReturn(true);
        } else {
            when(instance.isUnsatisfied()).thenReturn(false);
            when(instance.get()).thenReturn(identity);
        }
        return new RateLimitInterceptor(state, instance);
    }

    private SecurityIdentity authenticated(String name) {
        SecurityIdentity identity = Mockito.mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(false);
        Principal p = Mockito.mock(Principal.class);
        when(p.getName()).thenReturn(name);
        when(identity.getPrincipal()).thenReturn(p);
        return identity;
    }

    private SecurityIdentity anonymous() {
        SecurityIdentity identity = Mockito.mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(true);
        return identity;
    }

    private InvocationContext ctxFor(Class<?> target, String methodName) throws Exception {
        Method method = target.getDeclaredMethod(methodName);
        InvocationContext ctx = Mockito.mock(InvocationContext.class);
        when(ctx.getMethod()).thenReturn(method);
        when(ctx.proceed()).thenReturn("proceeded");
        return ctx;
    }

    @Test
    void allows_callsUpToMax_thenThrows() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(authenticated("auth0|alice"));
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "limited");

        assertEquals("proceeded", interceptor.intercept(ctx));
        assertEquals("proceeded", interceptor.intercept(ctx));
        // 3rd call exceeds max=2 → 429.
        assertThrows(RateLimitExceededException.class, () -> interceptor.intercept(ctx));
        verify(ctx, times(2)).proceed();
    }

    @Test
    void noAnnotation_proceedsWithoutBucketing() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(authenticated("auth0|alice"));
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "notLimited");
        // Hammer it 100 times — must always proceed because there's no annotation
        // on the method or its declaring class.
        for (int i = 0; i < 100; i++) {
            interceptor.intercept(ctx);
        }
        verify(ctx, times(100)).proceed();
    }

    @Test
    void misconfiguredZeroMax_proceedsWithoutBucketing() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(authenticated("auth0|alice"));
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "misconfiguredZeroMax");
        // Belt-and-braces: an annotation with max=0 must not block traffic.
        for (int i = 0; i < 5; i++) {
            interceptor.intercept(ctx);
        }
        verify(ctx, times(5)).proceed();
    }

    @Test
    void misconfiguredZeroWindow_proceedsWithoutBucketing() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(authenticated("auth0|alice"));
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "misconfiguredZeroWindow");
        for (int i = 0; i < 5; i++) {
            interceptor.intercept(ctx);
        }
        verify(ctx, times(5)).proceed();
    }

    @Test
    void classLevelAnnotation_isUsedAsFallback() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(authenticated("auth0|alice"));
        InvocationContext ctx = ctxFor(ClassAnnotated.class, "anyMethod");

        interceptor.intercept(ctx);
        interceptor.intercept(ctx);
        // 3rd call exceeds class-level max=2 → 429.
        assertThrows(RateLimitExceededException.class, () -> interceptor.intercept(ctx));
    }

    @Test
    void anonymousIdentity_bucketsUnderSharedAnonymousKey() throws Exception {
        RateLimitInterceptor interceptor = newInterceptor(anonymous());
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "limited");

        interceptor.intercept(ctx);
        interceptor.intercept(ctx);
        // 3rd anonymous call hits the same shared key → 429.
        assertThrows(RateLimitExceededException.class, () -> interceptor.intercept(ctx));
    }

    @Test
    void unsatisfiedIdentityInstance_bucketsAsAnonymous() throws Exception {
        // Quarkus may not have OIDC active in some contexts — the Instance<>
        // injection point is then unsatisfied. Must not NPE: fall back to
        // ANONYMOUS_KEY.
        RateLimitInterceptor interceptor = newInterceptor(null);
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "limited");

        interceptor.intercept(ctx);
        interceptor.intercept(ctx);
        assertThrows(RateLimitExceededException.class, () -> interceptor.intercept(ctx));
    }

    @Test
    void blankPrincipalName_bucketsAsAnonymous() throws Exception {
        SecurityIdentity weird = Mockito.mock(SecurityIdentity.class);
        when(weird.isAnonymous()).thenReturn(false);
        Principal p = Mockito.mock(Principal.class);
        when(p.getName()).thenReturn("   ");
        when(weird.getPrincipal()).thenReturn(p);

        RateLimitInterceptor interceptor = newInterceptor(weird);
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "limited");

        interceptor.intercept(ctx);
        interceptor.intercept(ctx);
        // Treated as anonymous → shares the same bucket as other anonymous callers.
        assertThrows(RateLimitExceededException.class, () -> interceptor.intercept(ctx));
    }

    @Test
    void nullPrincipalName_bucketsAsAnonymous() throws Exception {
        SecurityIdentity weird = Mockito.mock(SecurityIdentity.class);
        when(weird.isAnonymous()).thenReturn(false);
        Principal p = Mockito.mock(Principal.class);
        when(p.getName()).thenReturn(null);
        when(weird.getPrincipal()).thenReturn(p);

        RateLimitInterceptor interceptor = newInterceptor(weird);
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "limited");

        interceptor.intercept(ctx);
        interceptor.intercept(ctx);
        assertThrows(RateLimitExceededException.class, () -> interceptor.intercept(ctx));
    }

    @Test
    void thrownException_carriesRetryAfterFromState() throws Exception {
        // Arrange the bucket to be already exhausted, then trigger a refusal
        // and inspect the seconds-to-retry surfaced by the exception.
        RateLimitInterceptor interceptor = newInterceptor(authenticated("auth0|alice"));
        InvocationContext ctx = ctxFor(MethodAnnotated.class, "limited");
        interceptor.intercept(ctx);
        interceptor.intercept(ctx);

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class, () -> interceptor.intercept(ctx));
        // Window is 60s, 0ms elapsed since first call → ~60s retry-after.
        assertEquals(60L, ex.getRetryAfterSeconds());
    }

    @Test
    void differentPrincipalsDoNotShareBucket() throws Exception {
        SecurityIdentity alice = authenticated("auth0|alice");
        SecurityIdentity bob = authenticated("auth0|bob");

        RateLimitInterceptor aliceInterceptor = newInterceptor(alice);
        RateLimitInterceptor bobInterceptor = newInterceptor(bob);

        InvocationContext aliceCtx = ctxFor(MethodAnnotated.class, "limited");
        InvocationContext bobCtx = ctxFor(MethodAnnotated.class, "limited");

        aliceInterceptor.intercept(aliceCtx);
        aliceInterceptor.intercept(aliceCtx);
        // Alice exhausted — bob still has full budget.
        assertSame("proceeded", bobInterceptor.intercept(bobCtx));
        assertSame("proceeded", bobInterceptor.intercept(bobCtx));
        assertThrows(RateLimitExceededException.class,
                () -> bobInterceptor.intercept(bobCtx));
    }
}
