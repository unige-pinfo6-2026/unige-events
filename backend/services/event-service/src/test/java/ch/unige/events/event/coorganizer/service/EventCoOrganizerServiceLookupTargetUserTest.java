package ch.unige.events.event.coorganizer.service;

import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import io.quarkus.test.junit.QuarkusTest;
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
 * <p>{@code @QuarkusTest} so the executed bytecode is captured by
 * quarkus-jacoco (a plain unit test would pass but contribute zero
 * coverage). The {@link UserServiceClient} is passed as a plain Mockito
 * mock <em>argument</em> to the static method — it is never CDI-injected,
 * so the MicroProfile FT {@code @Fallback} proxy never wraps it and the
 * {@code catch (RuntimeException)} → 503 path (D19) is exercised directly,
 * not collapsed into the 404/fallback branch.
 */
@QuarkusTest
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
