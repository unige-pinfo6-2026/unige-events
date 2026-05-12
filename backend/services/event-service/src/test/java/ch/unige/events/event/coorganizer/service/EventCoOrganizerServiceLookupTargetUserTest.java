package ch.unige.events.event.coorganizer.service;

import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventCoOrganizerService#lookupTargetUser}.
 *
 * <p>Run as a plain JUnit unit test (no {@code @QuarkusTest}) so the
 * {@link UserServiceClient} mock is not wrapped by the MicroProfile FT
 * proxy that owns the {@code @Fallback} on
 * {@link UserServiceClient#getById}. The FT layer would otherwise swallow
 * every {@link RuntimeException} and return null, collapsing the 503
 * branch into the 404 branch — pinning D19 requires exercising the
 * {@code catch (RuntimeException)} path directly.
 */
class EventCoOrganizerServiceLookupTargetUserTest {

    @Test
    void invite_userServiceDown_throws503() {
        UserServiceClient client = mock(UserServiceClient.class);
        UUID downId = UUID.randomUUID();
        when(client.getById(downId))
                .thenThrow(new RuntimeException("CB open: connection timeout"));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> EventCoOrganizerService.lookupTargetUser(client, downId, 42L));
        assertEquals(503, ex.getResponse().getStatus());
    }

    @Test
    void invite_userNotFoundException_throws404() {
        UserServiceClient client = mock(UserServiceClient.class);
        UUID missingId = UUID.randomUUID();
        when(client.getById(missingId))
                .thenThrow(new NotFoundException("rest 404"));

        assertThrows(NotFoundException.class,
                () -> EventCoOrganizerService.lookupTargetUser(client, missingId, 42L));
    }

    @Test
    void invite_userClientReturnsNull_throws404() {
        UserServiceClient client = mock(UserServiceClient.class);
        UUID fallbackId = UUID.randomUUID();
        when(client.getById(fallbackId)).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> EventCoOrganizerService.lookupTargetUser(client, fallbackId, 42L));
    }

    @Test
    void invite_userFound_returnsResponse() {
        UserServiceClient client = mock(UserServiceClient.class);
        UUID userId = UUID.randomUUID();
        UserPublicResponse expected = new UserPublicResponse(
                userId, "User-" + userId, null, null, null,
                null, null, null, 0L, 0L, null);
        when(client.getById(userId)).thenReturn(expected);

        UserPublicResponse actual = EventCoOrganizerService.lookupTargetUser(client, userId, 42L);
        assertNotNull(actual);
        assertEquals(userId, actual.id());
    }
}
