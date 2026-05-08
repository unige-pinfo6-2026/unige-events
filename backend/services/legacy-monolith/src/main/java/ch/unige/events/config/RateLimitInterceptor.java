package ch.unige.events.config;

import ch.unige.events.exception.RateLimitExceededException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.security.Principal;

/**
 * CDI interceptor binding implementation for {@link PerUserRateLimit}.
 *
 * <p>The interceptor is intentionally stateless — it delegates bucket lookup
 * and accounting to the {@link RateLimitState} singleton. Per CDI spec,
 * interceptors must be {@code @Dependent} scoped, so any shared state has to
 * live on a separate bean. That separation also keeps the security primitive
 * (the buckets) testable independently of the AOP wiring.
 *
 * <p>Pentest 2026-04-17 finding 4.14 — application-level back-pressure on write
 * endpoints (issue #98).
 */
@PerUserRateLimit(name = "", max = 0)
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class RateLimitInterceptor {

    static final String ANONYMOUS_KEY = "__anonymous__";

    private final RateLimitState state;
    private final Instance<SecurityIdentity> identityInstance;

    @Inject
    public RateLimitInterceptor(RateLimitState state,
                                Instance<SecurityIdentity> identityInstance) {
        this.state = state;
        this.identityInstance = identityInstance;
    }

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        PerUserRateLimit limit = resolveAnnotation(ctx);
        if (limit == null || limit.max() <= 0 || limit.windowSeconds() <= 0) {
            // Belt-and-braces: misconfigured annotation should not block traffic.
            return ctx.proceed();
        }

        String principalKey = resolvePrincipalKey();
        long retryAfterSeconds = state.tryAcquire(
                limit.name(), principalKey, limit.max(), limit.windowSeconds());
        if (retryAfterSeconds > 0) {
            throw new RateLimitExceededException(
                    "Too many requests. Retry in " + retryAfterSeconds + " seconds.",
                    retryAfterSeconds);
        }
        return ctx.proceed();
    }

    private PerUserRateLimit resolveAnnotation(InvocationContext ctx) {
        PerUserRateLimit method = ctx.getMethod().getAnnotation(PerUserRateLimit.class);
        if (method != null) {
            return method;
        }
        return ctx.getMethod().getDeclaringClass().getAnnotation(PerUserRateLimit.class);
    }

    private String resolvePrincipalKey() {
        if (identityInstance.isUnsatisfied()) {
            return ANONYMOUS_KEY;
        }
        SecurityIdentity identity = identityInstance.get();
        if (identity.isAnonymous()) {
            return ANONYMOUS_KEY;
        }
        Principal principal = identity.getPrincipal();
        String name = principal.getName();
        return (name == null || name.isBlank()) ? ANONYMOUS_KEY : name;
    }
}
