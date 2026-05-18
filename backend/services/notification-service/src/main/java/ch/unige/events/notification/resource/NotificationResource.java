package ch.unige.events.notification.resource;

import ch.unige.events.notification.dto.NotificationDTO;
import ch.unige.events.notification.dto.ReadAllResponse;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Browser-facing notification endpoints. Routed by Kong under
 * {@code /api/users/me/notifications/*} (cf. configmap-routes.yaml).
 *
 * <p>All endpoints require an authenticated caller. Identity resolution
 * (Auth0 {@code sub} → {@code userId UUID}) happens inside
 * {@link NotificationService} via the internal endpoint on user-service
 * (Décision E).
 */
@Path("/users/me/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource {

    private final SecurityIdentity identity;
    private final NotificationService notificationService;

    @Inject
    public NotificationResource(SecurityIdentity identity, NotificationService notificationService) {
        this.identity = identity;
        this.notificationService = notificationService;
    }

    /**
     * Paginated listing of the caller's notifications, unread-first.
     * The {@code X-Unread-Count} response header carries the total
     * unread count (all pages combined) so the frontend can keep its
     * badge in sync without a second round-trip.
     */
    @GET
    @Authenticated
    public Response listMyNotifications(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        List<NotificationDTO> items = notificationService.listMine(auth0Id, page, size);
        long unread = notificationService.countUnread(auth0Id);
        return Response.ok(items)
                .header("X-Unread-Count", Long.toString(unread))
                .build();
    }

    /**
     * Mark a single notification as read. Idempotent (204 on already-read
     * rows). Anti-oracle 404 when the row belongs to another user.
     */
    @PATCH
    @Path("/{id}/read")
    @Authenticated
    @PerUserRateLimit(name = "notifications.read", max = 60, windowSeconds = 60)
    public Response markRead(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        notificationService.markRead(auth0Id, id);
        return Response.noContent().build();
    }

    /**
     * Bulk mark-as-read. Returns the count of rows transitioned —
     * {@code 0} if the inbox was already fully read (idempotent).
     */
    @PATCH
    @Path("/read-all")
    @Authenticated
    @PerUserRateLimit(name = "notifications.readAll", max = 10, windowSeconds = 60)
    public ReadAllResponse markAllRead() {
        String auth0Id = identity.getPrincipal().getName();
        long updated = notificationService.markAllRead(auth0Id);
        return new ReadAllResponse(updated);
    }
}
