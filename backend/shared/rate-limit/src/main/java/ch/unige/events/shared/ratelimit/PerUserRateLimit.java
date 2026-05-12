package ch.unige.events.shared.ratelimit;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-authenticated-user rate limit on a JAX-RS resource method.
 *
 * <p>Buckets are keyed on {@code SecurityIdentity.getPrincipal().getName()} +
 * {@link #name()} so two users hitting the same endpoint have independent
 * budgets, and the same user hitting two different endpoints has independent
 * budgets per endpoint group. Exceeding the limit makes the interceptor throw
 * {@link RateLimitExceededException}, which the dedicated mapper translates
 * into {@code 429 Too Many Requests} with a {@code Retry-After} header.
 *
 * <p>Pentest 2026-04-17 finding 4.14 — application-level back-pressure on
 * write endpoints (issue #98). Single-instance, in-process buckets backed by
 * Caffeine. Distributed (Redis) buckets are explicitly out of scope.
 *
 * <p>Anonymous callers (paths that allow {@code @PermitAll} but happen to
 * flow through an annotated method) are bucketed under a single shared key —
 * see {@link RateLimitInterceptor} for the resolution rule.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PerUserRateLimit {

    /** Logical bucket name — distinct per endpoint group, e.g. {@code "events.create"}. */
    @Nonbinding String name();

    /** Maximum number of permitted requests per {@link #windowSeconds()} window. */
    @Nonbinding int max();

    /** Sliding window length, in seconds. Must be {@code > 0}. */
    @Nonbinding int windowSeconds() default 60;
}
