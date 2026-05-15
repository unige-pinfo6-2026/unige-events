package ch.unige.events.user.calendar.service;

import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.user.config.AppConfig;
import ch.unige.events.user.calendar.dto.CalendarTokenResponse;
import ch.unige.events.user.calendar.util.IcsBuilder;
import ch.unige.events.user.entity.User;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Same semantics as the legacy monolith's CalendarService — token rotation
 * (lazy create on first read, regenerate on POST) and ICS feed assembly.
 *
 * <p>STUB-001 / Décisions A, B, F: the legacy {@code FavoriteStub} /
 * {@code AttendanceStub} / {@code EventStub} navigations are replaced by
 * REST clients to
 * engagement-service ({@code GET /users/{id}/attendances?status=ATTENDING}
 * — Décision B) and event-service ({@code GET /events?ids=...}). The
 * favorites projection is dropped from the ICS feed: the favorites
 * table moved into event-service in 2.2.3 with no internal endpoint
 * for "user favorite event ids" (deferred to S9 — cf.
 * internal-endpoints.md "endpoints à ajouter S9"). For S8 the ICS
 * feed only includes events the user is ATTENDING — minor functional
 * regression, acted in commit message.
 *
 * <p>SCRUM-169 — token read-modify-write is wrapped in a bounded retry
 * loop. The ProfileEditPage submits PUT /users/me (writes the User row,
 * bumps {@code @Version}) and GET /users/me/calendar-token (lazy token
 * generation, also writes the User row) concurrently. The token TX is
 * a passive side-effect the user did not explicitly request, so racing
 * the write must not surface as a 409 — we retry transparently. See
 * {@link #withOptimisticLockRetry}.
 */
@ApplicationScoped
public class CalendarService {

    /**
     * Maximum number of times the token read-modify-write is retried after
     * an {@link OptimisticLockException}. Three is more than enough given
     * the contended window is ~milliseconds and the concurrent writer
     * (ProfileEditPage save) only writes once per click.
     */
    static final int MAX_TOKEN_WRITE_ATTEMPTS = 3;

    /** Back-off between retries, in milliseconds. */
    static final long TOKEN_WRITE_RETRY_DELAY_MS = 50L;

    @Inject
    AppConfig appConfig;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient EngagementServiceClient engagementClient;

    /**
     * Self-injection so the {@code @Transactional} attempt methods are
     * invoked through the CDI proxy — calling {@code this.…Attempt(…)}
     * directly would bypass the interceptor and execute the body outside
     * a transaction, defeating the per-attempt rollback isolation the
     * retry loop relies on.
     */
    @Inject Instance<CalendarService> self;

    /**
     * SCRUM-169 — the {@code @Transactional} lives on
     * {@link #getOrCreateTokenAttempt}; the public entry point is
     * non-transactional so each retry runs in a fresh JTA transaction.
     */
    public CalendarTokenResponse getOrCreateToken(String auth0Id) {
        return withOptimisticLockRetry("getOrCreateToken",
                () -> self.get().getOrCreateTokenAttempt(auth0Id));
    }

    /**
     * SCRUM-169 — same retry shape as {@link #getOrCreateToken}. The
     * regenerate path is operator-triggered (POST /me/calendar-token), so
     * a race against a profile save is rarer but the same window exists,
     * and the user shouldn't have to retry their click.
     */
    public CalendarTokenResponse regenerateToken(String auth0Id) {
        return withOptimisticLockRetry("regenerateToken",
                () -> self.get().regenerateTokenAttempt(auth0Id));
    }

    @Transactional
    public CalendarTokenResponse getOrCreateTokenAttempt(String auth0Id) {
        User user = resolveUser(auth0Id);
        if (user.calendarToken == null) {
            user.calendarToken = UUID.randomUUID();
        }
        return buildTokenResponse(user.calendarToken);
    }

    @Transactional
    public CalendarTokenResponse regenerateTokenAttempt(String auth0Id) {
        User user = resolveUser(auth0Id);
        user.calendarToken = UUID.randomUUID();
        return buildTokenResponse(user.calendarToken);
    }

    public String generateIcsFeed(UUID calendarToken) {
        User user = User.findByCalendarToken(calendarToken)
                .orElseThrow(() -> new NotFoundException("Calendar token not found"));

        List<AttendanceDTO> attendances = engagementClient.getUserAttendances(
                user.id, AttendanceStatus.ATTENDING.name());
        if (attendances == null || attendances.isEmpty()) {
            return IcsBuilder.buildIcsContent(List.of(), appConfig.frontendUrl());
        }

        List<Long> eventIds = attendances.stream()
                .map(AttendanceDTO::eventId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (eventIds.isEmpty()) {
            return IcsBuilder.buildIcsContent(List.of(), appConfig.frontendUrl());
        }

        List<EventDTO> events = eventClient.findByIds(eventIds, "PUBLISHED");
        if (events == null) {
            events = List.of();
        }
        return IcsBuilder.buildIcsContent(events, appConfig.frontendUrl());
    }

    /**
     * SCRUM-169 — wrap a transactional token write in a bounded retry
     * loop. Each invocation of {@code attempt} runs in its own JTA
     * transaction (the {@code @Transactional} sits on the {@code …Attempt}
     * methods invoked via the CDI proxy), so a failed attempt's
     * rollback-only flag does not poison the next attempt's read.
     *
     * <p>Pattern mirrors {@code UserService.isOptimisticLockConflict} —
     * Hibernate sometimes wraps the JPA exception in a generic
     * {@link PersistenceException}, so we unwrap before deciding whether
     * to retry. {@link NotFoundException} is never retried (a missing
     * user won't become un-missing on the next read).
     */
    <T> T withOptimisticLockRetry(String operation, Supplier<T> attempt) {
        RuntimeException lastFailure = null;
        for (int attemptIndex = 1; attemptIndex <= MAX_TOKEN_WRITE_ATTEMPTS; attemptIndex++) {
            try {
                return attempt.get();
            } catch (NotFoundException notFound) {
                throw notFound;
            } catch (RuntimeException exception) {
                if (!isOptimisticLockConflict(exception)) {
                    throw exception;
                }
                lastFailure = exception;
                Log.warnf("%s hit OptimisticLock on attempt %d/%d — retrying",
                        operation, attemptIndex, MAX_TOKEN_WRITE_ATTEMPTS);
                if (attemptIndex < MAX_TOKEN_WRITE_ATTEMPTS) {
                    sleepBackoff(TOKEN_WRITE_RETRY_DELAY_MS);
                }
            }
        }
        Log.errorf("%s exhausted %d attempts under contention",
                operation, MAX_TOKEN_WRITE_ATTEMPTS);
        throw lastFailure;
    }

    private void sleepBackoff(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isOptimisticLockConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OptimisticLockException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("OptimisticLock") || className.contains("StaleState")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CalendarTokenResponse buildTokenResponse(UUID token) {
        String httpsUrl = appConfig.frontendUrl() + "/api/calendar/" + token + ".ics";
        String webcalUrl = httpsUrl.replaceFirst("^https?://", "webcal://");
        return new CalendarTokenResponse(token, webcalUrl, httpsUrl);
    }

    private User resolveUser(String auth0Id) {
        return User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }
}
