package ch.unige.events.engagement.attendance.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.engagement.attendance.dto.AttendanceDTOMapper;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.jaxrs.Timeframe;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.domain.projections.EventCapacity;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
 * <p>STUB-001 / Décisions A, F, G: the legacy {@code EventStub}
 * pessimistic-write lock is replaced by
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

    private static final String ROLE_ADMIN = "ADMIN";

    @Inject EntityManager entityManager;
    @Inject CallerIdentity callerIdentity;
    @Inject SecurityIdentity identity;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient UserServiceClient userClient;

    // SCRUM-99: fired post-commit for the AttendanceCreatedKafkaBridge to
    // publish attendances.created — only when effective status is ATTENDING
    // (Décision M : promotions WAITLISTED→ATTENDING do not re-emit).
    @Inject jakarta.enterprise.event.Event<ch.unige.events.shared.kafka.events.AttendanceCreatedEvent> attendanceCreatedEvent;

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

        UUID userId = callerIdentity.requireUuid();
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

        // SCRUM-99 Décision M: emit attendances.created only for ATTENDING.
        // WAITLISTED signups don't generate a "new attendee" notification —
        // the creator is notified once when the user effectively attends.
        if (effective == AttendanceStatus.ATTENDING) {
            attendanceCreatedEvent.fire(
                    ch.unige.events.shared.kafka.events.AttendanceCreatedEvent.of(
                            attendance.id, eventId, userId));
        }

        return AttendanceDTOMapper.from(attendance, safeGetUser(userId));
    }

    @Transactional
    public void removeAttendance(String auth0Id, Long eventId) {
        // Décision B: same advisory lock as attend(...) — protects the
        // delete + WAITLISTED→ATTENDING promotion from racing concurrent
        // removes (each release would otherwise promote a different
        // waitlisted row).
        acquireAdvisoryLock(eventId);

        UUID userId = callerIdentity.requireUuid();
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
                || event.status().isTerminal()) {
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

    /**
     * Returns the paginated attendees of an event with a privacy filter
     * applied at the DTO layer (SCRUM-S7).
     *
     * <p>Visibility contract:
     * <ul>
     *   <li><b>Organizer view</b> (creator, ACCEPTED co-organizer, or admin)
     *       → real {@code displayName}, {@code avatarUrl}, {@code userId} for
     *       every row, including private profiles.</li>
     *   <li><b>Other authenticated callers</b> → real identity for
     *       public-profile rows and for private attendees the caller follows
     *       with ACCEPTED status (consistent with seeing their participations).
     *       Every other private-profile row is anonymized with
     *       {@code displayName=null}, {@code avatarUrl=null}, and
     *       {@code userId=null} so the caller cannot probe
     *       {@code GET /users/{id}} to de-anonymize the participant.</li>
     * </ul>
     *
     * <p>Authentication is required (the resource carries
     * {@code @Authenticated}); the endpoint does not 403 non-organizers
     * since the privacy filter is applied at the DTO layer rather than via
     * an access gate (review-driven design: ship the anonymized projection
     * to every authenticated caller instead of leaking the existence/absence
     * of the list itself).
     */
    @Transactional
    public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
        UUID callerUuid = callerIdentity.getUuid();
        ch.unige.events.shared.domain.dto.EventDTO event = (callerUuid != null)
                ? eventClient.getByIdWithCoOrgCheck(eventId, callerUuid)
                : eventClient.getById(eventId);
        if (event == null) {
            throw new NotFoundException("Event not found");
        }

        boolean isAdmin = identity.hasRole(ROLE_ADMIN);
        boolean isCreator = callerUuid != null && callerUuid.equals(event.creatorId());
        // coOrganizerOf is a tri-state: TRUE / FALSE / null. The
        // getByIdWithCoOrgCheck call honors the self-check only for
        // authenticated callers whose uuid matches the query param
        // (SEC-002 / Décision C) — non-null values are authoritative.
        // Fall back to the organizer-uuids endpoint only when the self-check
        // wasn't honored (null), so the hot path for ordinary authenticated
        // viewers doesn't pay a needless cross-service round-trip.
        Boolean coOrgOf = event.coOrganizerOf();
        boolean isCoOrganizer;
        if (coOrgOf != null) {
            isCoOrganizer = coOrgOf;
        } else if (!isCreator && callerUuid != null) {
            isCoOrganizer = eventClient.getOrganizerUuids(eventId).contains(callerUuid);
        } else {
            isCoOrganizer = false;
        }
        boolean isOrganizerView = isCreator || isCoOrganizer || isAdmin;

        List<Attendance> rows = Attendance.findByEvent(eventId, page, size);
        Set<UUID> userIds = rows.stream().map(a -> a.userId).collect(Collectors.toSet());
        Map<UUID, AttendeeProjection> projectionsById =
                fetchAttendeeProjections(userIds);

        // Accepted followers of a (private) attendee see that attendee's real
        // identity — consistent with seeing their participations. Organizers
        // already see everyone, so skip the extra follow lookup for them.
        Set<UUID> followedByCaller = (!isOrganizerView && callerUuid != null)
                ? Set.copyOf(userClient.getFollowedIds(callerUuid))
                : Set.of();

        return rows.stream()
                .map(a -> AttendanceDTOMapper.fromWithPrivacy(
                        a, projectionsById.get(a.userId), isOrganizerView,
                        followedByCaller.contains(a.userId)))
                .toList();
    }

    /**
     * Bulk-resolves the privacy projection for every distinct user id in
     * the page, with a graceful degradation when user-service is down: an
     * empty map causes every row to be anonymized (no real identity exposed)
     * rather than failing the whole request.
     */
    private Map<UUID, AttendeeProjection> fetchAttendeeProjections(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<AttendeeProjection> projections =
                    userClient.getAttendeeProjections(List.copyOf(userIds));
            if (projections == null) {
                return Map.of();
            }
            Map<UUID, AttendeeProjection> byId = new HashMap<>();
            for (AttendeeProjection p : projections) {
                if (p != null && p.id() != null) {
                    byId.put(p.id(), p);
                }
            }
            return byId;
        } catch (RuntimeException e) {
            LOG.warnf(e, "[USER_ENRICHMENT_FAIL] getAttendeeProjections(size=%d) — returning empty map (degraded enrichment, attendees rendered anonymous)", userIds.size());
            return Map.of();
        }
    }

    @Transactional
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        UUID userId = callerIdentity.getUuid();
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
        UUID userId = callerIdentity.getUuid();
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

    /**
     * Public-profile variant of {@link #getMyParticipationEvents}: the PUBLISHED
     * events the {@code targetUserId} attends (ATTENDING only), shown on the
     * public profile page (SCRUM-141 follow-up). Privacy gate: visible to the
     * target themselves, an admin, anyone when the target's profile is public,
     * or an accepted follower of a private target (an accepted follower sees a
     * private account's participations — Instagram model). Everyone else gets an
     * empty list (mirrors the SCRUM-169 posture: no 404 oracle). Fail-closed on
     * degraded user-service reads (an indeterminate profile / follow lookup is
     * treated as private / non-follower).
     */
    @Transactional
    public List<ch.unige.events.shared.domain.dto.EventDTO> getUserParticipationEvents(
            UUID targetUserId, Timeframe timeframeFilter) {
        if (targetUserId == null) {
            return List.of();
        }
        UUID callerUuid = callerIdentity.getUuid();
        boolean isSelf = targetUserId.equals(callerUuid);
        boolean isAdmin = identity.hasRole(ROLE_ADMIN);
        // Gate: self, admins (moderation), a public target, or a target the
        // caller follows with ACCEPTED status (an accepted follower sees a
        // private account's participations — Instagram model). Anyone else gets
        // an empty list (no 404 oracle). Ordered cheapest-first; the follow
        // lookup only fires for a private target viewed by a non-self non-admin.
        if (!isSelf && !isAdmin
                && !isTargetProfilePublic(targetUserId)
                && !callerFollowsTarget(callerUuid, targetUserId)) {
            return List.of();
        }
        List<Long> eventIds = Attendance.findAllByUser(targetUserId).stream()
                .filter(a -> a.status == AttendanceStatus.ATTENDING)
                .map(a -> a.eventId)
                .toList();
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
                eventIds, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(
                eventIds, AttendanceStatus.WAITLISTED, entityManager);
        // PUBLISHED-only: never expose a third party's DRAFT/CANCELLED events.
        List<ch.unige.events.shared.domain.dto.EventDTO> events =
                eventClient.findByIds(eventIds, EventStatus.PUBLISHED.name());
        LocalDateTime now = LocalDateTime.now();
        return events.stream()
                .filter(e -> matchesTimeframe(e, timeframeFilter, now))
                .map(e -> withCounts(e,
                        attendingCounts.getOrDefault(e.id(), 0L),
                        waitlistedCounts.getOrDefault(e.id(), 0L)))
                .toList();
    }

    /**
     * Resolves the target's {@code profilePublic} via the bulk attendee
     * projection (the only user field engagement needs for the gate). Fail-closed:
     * a missing projection or a degraded user-service read returns {@code false},
     * so a non-owner never sees participations of an indeterminate profile.
     */
    private boolean isTargetProfilePublic(UUID targetUserId) {
        AttendeeProjection p = fetchAttendeeProjections(Set.of(targetUserId)).get(targetUserId);
        return p != null && p.profilePublic();
    }

    /**
     * Whether the authenticated caller follows {@code targetUserId} with an
     * ACCEPTED status — the condition that lets a follower see a private
     * account's participations. Resolved via the user-service
     * {@code _internal-followed-ids} endpoint (internal-endpoints.md entry #11).
     * Fail-closed: a degraded read returns an empty list, so a follower is
     * treated as a non-follower rather than leaking a private account's
     * participations.
     */
    private boolean callerFollowsTarget(UUID callerUuid, UUID targetUserId) {
        if (callerUuid == null) {
            return false;
        }
        return userClient.getFollowedIds(callerUuid).contains(targetUserId);
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
            // A null eventId means upstream code skipped event resolution before
            // reaching the capacity-gating path. The
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
