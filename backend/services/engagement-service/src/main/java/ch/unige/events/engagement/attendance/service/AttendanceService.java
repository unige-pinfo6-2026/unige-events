package ch.unige.events.engagement.attendance.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.engagement.attendance.dto.AttendanceDTOMapper;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.jaxrs.Timeframe;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;
import ch.unige.events.shared.domain.projections.EventCapacity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Same contract as the legacy AttendanceService — capacity gating,
 * idempotent attend, WAITLISTED auto-promotion on remove.
 *
 * <p>Étape 3.1 finalization-ultimate (STUB-001 / Décisions A, F, G):
 * the legacy {@code EventStub} pessimistic-write lock is replaced by
 * a pragmatic applicative check on the local {@code attendances} count
 * (acceptable trade-off for a pinfo6 project per Annexe E of the spec —
 * a borderline simultaneous attend may end up WAITLISTED ; idempotent).
 * The SCRUM-136 cascade is delegated to event-service via
 * {@link EventServiceClient#getByIdWithCoOrgCheck}. User enrichment
 * uses {@link UserServiceClient#getById}.
 */
@ApplicationScoped
public class AttendanceService {

    private static final Logger LOG = Logger.getLogger(AttendanceService.class);

    @Inject EntityManager entityManager;
    /**
     * Lazy via {@link Instance} so {@code @QuarkusTest} runs (which set
     * {@code quarkus.oidc.enabled=false}) don't have to resolve the
     * JsonWebToken bean at startup.
     */
    @Inject Instance<JsonWebToken> jwt;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient UserServiceClient userClient;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        if (status != AttendanceStatus.ATTENDING) {
            throw new BadRequestException("Only ATTENDING is accepted as a request status");
        }

        // Décision B: serialise concurrent attend/remove on the same event so
        // capacity gating + WAITLISTED auto-promotion stay consistent without
        // re-introducing an EventStub. The advisory lock is per-eventId and
        // released automatically at transaction end.
        acquireAdvisoryLock(eventId);

        ch.unige.events.shared.domain.dto.EventDTO event = eventClient.getById(eventId);
        if (event == null) {
            throw new NotFoundException("Event not found");
        }

        if (event.status() != EventStatus.PUBLISHED) {
            throw new BadRequestException("Cannot attend a non-published event");
        }

        if (event.registrationDeadline() != null
                && LocalDateTime.now().isAfter(event.registrationDeadline())) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(new ApiErrorResponse(
                                    "registration_closed",
                                    "La deadline d'inscription est dépassée."))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }

        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }

        Attendance existing = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElse(null);
        if (existing != null) {
            return AttendanceDTOMapper.from(existing, safeGetUser(userId));
        }

        AttendanceStatus effective;
        if (event.capacity() == null) {
            effective = AttendanceStatus.ATTENDING;
        } else {
            long currentAttending = Attendance.count(
                    "eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
            effective = (currentAttending < event.capacity())
                    ? AttendanceStatus.ATTENDING
                    : AttendanceStatus.WAITLISTED;
        }

        Attendance attendance = new Attendance();
        attendance.userId = userId;
        attendance.eventId = eventId;
        attendance.status = effective;
        attendance.persist();

        return AttendanceDTOMapper.from(attendance, safeGetUser(userId));
    }

    @Transactional
    public void removeAttendance(String auth0Id, Long eventId) {
        // Décision B: same advisory lock as attend(...) — protects the
        // delete + WAITLISTED→ATTENDING promotion from racing concurrent
        // removes (each release would otherwise promote a different
        // waitlisted row).
        acquireAdvisoryLock(eventId);

        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("Attendance not found");
        }

        Attendance attendance = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Attendance not found"));

        AttendanceStatus removed = attendance.status;

        ch.unige.events.shared.domain.dto.EventDTO event = eventClient.getById(eventId);

        attendance.delete();

        if (event == null) {
            LOG.warnf(
                "[WAITLIST_PROMOTION_SKIPPED] event-service unreachable for event=%d — promotion deferred until next attend/leave",
                eventId
            );
            return;
        }
        if (removed != AttendanceStatus.ATTENDING
                || event.capacity() == null
                || event.status() == EventStatus.CANCELLED
                || event.status() == EventStatus.EXPIRED
                || event.status() == EventStatus.BANNED) {
            return;
        }

        Attendance promoted = Attendance.<Attendance>find(
                "eventId = ?1 and status = ?2 order by createdAt asc, id asc",
                eventId, AttendanceStatus.WAITLISTED)
                .firstResultOptional()
                .orElse(null);
        if (promoted == null) {
            return;
        }

        promoted.status = AttendanceStatus.ATTENDING;
        LOG.infof("[WAITLIST_PROMOTION] event=%d user=%s promoted from WAITLISTED to ATTENDING",
                eventId, promoted.userId);
    }

    @Transactional
    public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
        UUID callerUuid = Auth0IdResolver.resolveUserUuid(jwt());
        ch.unige.events.shared.domain.dto.EventDTO event = (callerUuid != null)
                ? eventClient.getByIdWithCoOrgCheck(eventId, callerUuid)
                : eventClient.getById(eventId);
        if (event == null) {
            throw new NotFoundException("Event not found");
        }

        boolean isCreator = callerUuid != null && callerUuid.equals(event.creatorId());
        boolean isCoOrganizer = Boolean.TRUE.equals(event.coOrganizerOf());
        if (!isCreator && !isCoOrganizer) {
            // Defense in depth: even if coOrganizerOf came back null because
            // ?check-co-org-of= was not honored, double-check via the
            // organizer-uuids endpoint (Décision G).
            if (callerUuid == null
                    || !eventClient.getOrganizerUuids(eventId).contains(callerUuid)) {
                throw new ForbiddenException(
                        "Only the event creator or an accepted co-organizer can view attendees");
            }
        }

        List<Attendance> rows = Attendance.findByEvent(eventId, page, size);
        Set<UUID> userIds = rows.stream().map(a -> a.userId).collect(Collectors.toSet());
        Map<UUID, UserPublicResponse> usersById = new HashMap<>();
        for (UUID uid : userIds) {
            UserPublicResponse u = safeGetUser(uid);
            if (u != null) usersById.put(uid, u);
        }

        return rows.stream()
                .map(a -> AttendanceDTOMapper.from(a, usersById.get(a.userId)))
                .toList();
    }

    @Transactional
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            return List.of();
        }
        UserPublicResponse user = safeGetUser(userId);
        return Attendance.findAllByUser(userId).stream()
                .map(a -> AttendanceDTOMapper.from(a, user))
                .toList();
    }

    /**
     * Cross-service projection (Décision B finalization-ultimate REST-002):
     * returns the user's attendances filtered by status, mapped to the
     * service-local {@link AttendanceDTO} without user enrichment (id-only
     * payload — the consumer user-service knows its own user data).
     * Backing endpoint:
     * {@code GET /users/{id}/attendances?status=...} exposed by
     * {@link ch.unige.events.engagement.attendance.resource.UserAttendancesInternalResource}.
     */
    @Transactional
    public List<AttendanceDTO> findByUser(UUID userId, AttendanceStatus status) {
        List<Attendance> rows = (status == null)
                ? Attendance.<Attendance>list("userId", userId)
                : Attendance.<Attendance>list("userId = ?1 and status = ?2", userId, status);
        return rows.stream()
                .map(a -> AttendanceDTOMapper.from(a, null))
                .toList();
    }

    @Transactional
    public List<ch.unige.events.shared.domain.dto.EventDTO> getMyParticipationEvents(
            String auth0Id, AttendanceStatus statusFilter, Timeframe timeframeFilter) {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            return List.of();
        }
        List<Attendance> rows = Attendance.findAllByUser(userId);
        List<Long> eventIds = rows.stream()
                .filter(a -> statusFilter == null || a.status == statusFilter)
                .map(a -> a.eventId)
                .toList();
        if (eventIds.isEmpty()) {
            return List.of();
        }
        // Map<event id → local attending/waitlisted count> — engagement
        // owns the attendances table, so this stays a local SQL count.
        Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
                eventIds, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(
                eventIds, AttendanceStatus.WAITLISTED, entityManager);

        List<ch.unige.events.shared.domain.dto.EventDTO> events =
                eventClient.findByIds(eventIds, null);
        LocalDateTime now = LocalDateTime.now();
        return events.stream()
                .filter(e -> matchesTimeframe(e, timeframeFilter, now))
                .map(e -> withCounts(e,
                        attendingCounts.getOrDefault(e.id(), 0L),
                        waitlistedCounts.getOrDefault(e.id(), 0L)))
                .toList();
    }

    private static boolean matchesTimeframe(
            ch.unige.events.shared.domain.dto.EventDTO event,
            Timeframe filter,
            LocalDateTime now) {
        if (filter == null) {
            return true;
        }
        if (event.endDate() == null) {
            return false;
        }
        boolean isPast = event.endDate().isBefore(now);
        return filter == Timeframe.PAST ? isPast : !isPast;
    }

    private static ch.unige.events.shared.domain.dto.EventDTO withCounts(
            ch.unige.events.shared.domain.dto.EventDTO e,
            long attending,
            long waitlisted) {
        return new ch.unige.events.shared.domain.dto.EventDTO(
                e.id(), e.title(), e.description(), e.location(),
                e.startDate(), e.endDate(),
                e.category(), e.faculty(), e.bannerUrl(),
                e.creatorId(), e.status(), e.capacity(),
                e.allDay(), e.featured(), e.featuredAt(),
                attending,
                EventCapacity.computeAvailableSpots(e.capacity(), attending),
                waitlisted,
                e.viewCount(), e.interestedCount(),
                e.websiteUrl(), e.contactEmail(), e.registrationDeadline(),
                e.tags(),
                e.createdAt(), e.updatedAt(),
                e.parentEventId(), e.recurrenceRule(),
                e.coOrganizerOf());
    }

    private UserPublicResponse safeGetUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userClient.getById(userId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            // Semantic absence: user was hard-deleted or never existed. Caller
            // already treats null as "anonymous author" — no log needed.
            return null;
        } catch (RuntimeException e) {
            // Infra failure (timeout, CB open, JSON parse error, etc.). Log so
            // ops can correlate degraded enrichment to a downstream incident.
            LOG.warnf(e, "[USER_ENRICHMENT_FAIL] safeGetUser(%s) — returning null (degraded enrichment due to downstream failure)", userId);
            return null;
        }
    }

    private void acquireAdvisoryLock(Long eventId) {
        if (eventId == null) {
            // Étape 24.5.3 (A11): a null eventId means upstream code skipped
            // event resolution before reaching the capacity-gating path. The
            // REST entry points (EventAttendanceResource) always resolve
            // event-id from the path param before dispatching, and the
            // service callers (markGoing, markInterested) require a non-null
            // eventId by contract. Returning silently here would bypass the
            // advisory lock and let two concurrent capacity-gated inserts
            // race past max_attendees — a correctness bug masquerading as a
            // defensive null check. Fail fast instead.
            throw new IllegalStateException(
                "acquireAdvisoryLock called with null eventId — capacity gating bypassed. " +
                "This indicates a programming error upstream (REST path always passes a non-null eventId)."
            );
        }
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
                .setParameter(1, eventId)
                .getSingleResult();
    }
}
