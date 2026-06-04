package ch.unige.events.notification.service;

import ch.unige.events.notification.dto.NotificationDTO;
import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.IdProjection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Business layer for the in-app notification feed. SCRUM-99 phase 1.
 *
 * <p>{@link #create(UUID, NotificationType, Long, UUID, String)} is the
 * single mutation point used by the 3 Kafka consumers
 * ({@code EventCancelledConsumer}, {@code EventUpdatedConsumer},
 * {@code AttendanceCreatedConsumer}). At-least-once delivery is accepted
 * (Décision D) — a duplicate Kafka delivery produces a duplicate row, no
 * UK-based deduplication.
 *
 * <p>Listing / mark-read / mark-all-read resolve the caller via the
 * internal endpoint {@code GET /users/_internal-by-auth0-id/{auth0Id}}
 * exposed by user-service (Décision E). The resolution is per-call ; if
 * the call fails (downstream unavailable, user not provisioned), the
 * {@link UserServiceClient} fallback throws 503, which the resource layer
 * propagates to the caller.
 *
 * <p>Anti-oracle on {@code markRead} : a notification row that belongs to
 * another user produces the same {@link NotFoundException} as an unknown
 * id (Décision E). The resource layer surfaces this as a 404 with the
 * standard {@code ApiErrorResponse} envelope — never as a 403 (which
 * would leak the existence of the notification).
 */
@ApplicationScoped
public class NotificationService {

    private final UserServiceClient userClient;

    @Inject
    public NotificationService(@RestClient UserServiceClient userClient) {
        this.userClient = userClient;
    }

    /**
     * Create-and-persist primitive used by Kafka consumers. Always returns
     * the persisted entity (id populated).
     */
    @Transactional
    public Notification create(UUID userId, NotificationType type, Long eventId,
                                UUID relatedUserId, String message) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.eventId = eventId;
        n.relatedUserId = relatedUserId;
        n.message = message;
        n.persist();
        return n;
    }

    /**
     * Paginated listing of notifications for the calling user. Unread
     * first, then most recent (cf. {@link Notification#findByUser}).
     */
    public List<NotificationDTO> listMine(String auth0Id, int page, int size) {
        UUID userId = resolveUserId(auth0Id);
        return Notification.findByUser(userId, page, size).stream()
                .map(NotificationDTO::from)
                .toList();
    }

    /**
     * Counter exposed via the {@code X-Unread-Count} response header on the
     * listing endpoint (cf. Décision G).
     */
    public long countUnread(String auth0Id) {
        UUID userId = resolveUserId(auth0Id);
        return Notification.countUnreadByUser(userId);
    }

    /**
     * Anti-oracle 404 on cross-user access. Idempotent : already-read row
     * is a no-op.
     */
    @Transactional
    public void markRead(String auth0Id, Long notificationId) {
        UUID userId = resolveUserId(auth0Id);
        Notification n = Notification.findByIdAndUser(notificationId, userId)
                .orElseThrow(NotFoundException::new);
        if (!n.read) {
            n.read = true;
            n.readAt = LocalDateTime.now(ZoneId.systemDefault());
        }
    }

    /**
     * Bulk mark-as-read. Returns the count of rows transitioned from
     * {@code read=false} to {@code read=true} (input for
     * {@code ReadAllResponse.updated}).
     */
    @Transactional
    public long markAllRead(String auth0Id) {
        UUID userId = resolveUserId(auth0Id);
        return Notification.markAllReadByUser(userId);
    }

    /**
     * Resolves the caller's identity via the internal user-service endpoint
     * (Décision E). The {@link UserServiceClient} fallback throws 503 on
     * downstream outage and aborts the retry/CB on a legitimate 404
     * (unknown {@code auth0Id}) — both surface as a 5xx / 4xx to the
     * browser without polluting the circuit breaker.
     */
    private UUID resolveUserId(String auth0Id) {
        IdProjection p = userClient.getInternalByAuth0Id(auth0Id);
        if (p == null || p.id() == null) {
            // Defensive — the @Fallback throws on outage, so we should
            // not reach here, but a misbehaving impl could still return null.
            throw new NotFoundException("User not provisioned");
        }
        return p.id();
    }
}
