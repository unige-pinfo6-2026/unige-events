package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.IdProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit test of the @Fallback default methods on UserServiceClient.
 * Same rationale as EventServiceClientFallbackTest.
 */
class UserServiceClientFallbackTest {

    private static final UserServiceClient CLIENT = new UserServiceClient() {
        @Override public UserPublicResponse getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public List<AttendeeProjection> getAttendeeProjections(List<UUID> ids) {
            throw new UnsupportedOperationException();
        }
        @Override public IdProjection getInternalByAuth0Id(String auth0Id) {
            throw new UnsupportedOperationException();
        }
        @Override public List<UUID> getFollowedIds(UUID followerId) {
            throw new UnsupportedOperationException();
        }
        @Override public List<IdProjection> getByUsernames(String csv) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void getByIdFallback_returnsNull() {
        assertNull(CLIENT.getByIdFallback(UUID.randomUUID()));
    }

    @Test
    void getAttendeeProjectionsFallback_returnsEmptyList() {
        // Degraded enrichment: caller should treat every row as anonymous
        // rather than fail the whole request. An empty list satisfies that.
        List<AttendeeProjection> fallback = CLIENT.getAttendeeProjectionsFallback(
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, fallback.size());
    }

    @Test
    void getAttendeeProjectionsFallback_acceptsNullIdsWithoutNpe() {
        // Defensive: the fallback should never NPE on the size() log statement
        // even if a degenerate caller hands it a null list.
        assertTrue(CLIENT.getAttendeeProjectionsFallback(null).isEmpty());
    }

    @Test
    void getInternalByAuth0IdFallback_throws503() {
        // Identity resolution is critical — returning null would surface as
        // a NullPointerException in NotificationService. The fallback
        // converts to 503 Service Unavailable so the resource layer can
        // translate it to a meaningful caller-facing error.
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> CLIENT.getInternalByAuth0IdFallback("auth0|abc"));
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                ex.getResponse().getStatus());
    }

    @Test
    void getFollowedIdsFallback_returnsEmptyList() {
        // Graceful degradation: a transient user-service outage should show
        // an empty feed rather than a 503 to the browser.
        List<UUID> fallback = CLIENT.getFollowedIdsFallback(UUID.randomUUID());
        assertEquals(0, fallback.size());
    }

    @Test
    void getFollowedIdsFallback_acceptsNullFollowerIdWithoutNpe() {
        assertTrue(CLIENT.getFollowedIdsFallback(null).isEmpty());
    }

    @Test
    void getByUsernamesFallback_returnsEmptyList() {
        // SCRUM-145 — mention resolution is best-effort. Crashing the Kafka
        // consumer on a transient downstream failure would be far worse than
        // missing a notification, so the fallback degrades to "zero handles
        // resolved → zero notifications fired".
        List<IdProjection> fallback = CLIENT.getByUsernamesFallback("alice.dosh,bob.smith");
        assertTrue(fallback.isEmpty());
    }

    @Test
    void getByUsernamesFallback_acceptsNullCsvWithoutNpe() {
        // Defensive : the log statement reads csv.length() ; the fallback
        // must guard against null callers (degenerate code path).
        assertTrue(CLIENT.getByUsernamesFallback(null).isEmpty());
    }
}
