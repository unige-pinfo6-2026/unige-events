package ch.unige.events.shared.domain.projections;

import ch.unige.events.shared.client.UserMeClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallerIdentityTest {

    @Test
    void requireUuid_resolvesViaUserMeAndCachesResult() {
        UUID expected = UUID.randomUUID();
        UserMeClient userMeClient = mock(UserMeClient.class);
        when(userMeClient.getMe()).thenReturn(user(expected));

        CallerIdentity identity = identity(userMeClient);

        assertEquals(expected, identity.requireUuid());
        assertEquals(expected, identity.requireUuid());
        verify(userMeClient).getMe();
    }

    @Test
    void getUuid_returnsResolvedUuidOnSuccess() {
        UUID expected = UUID.randomUUID();
        UserMeClient userMeClient = mock(UserMeClient.class);
        when(userMeClient.getMe()).thenReturn(user(expected));

        CallerIdentity identity = identity(userMeClient);

        assertEquals(expected, identity.getUuid());
    }

    @Test
    void getUuid_returnsNullWhenUserMeCannotResolveCaller() {
        UserMeClient userMeClient = mock(UserMeClient.class);
        when(userMeClient.getMe()).thenThrow(new NotFoundException());

        CallerIdentity identity = identity(userMeClient);

        assertNull(identity.getUuid());
        assertThrows(NotFoundException.class, identity::requireUuid);
    }

    @Test
    void requireUuid_rejectsEmptyUserMeResponse() {
        UserMeClient userMeClient = mock(UserMeClient.class);
        when(userMeClient.getMe()).thenReturn(null);

        CallerIdentity identity = identity(userMeClient);

        assertNull(identity.getUuid());
        assertThrows(NotFoundException.class, identity::requireUuid);
    }

    @Test
    void requireUuid_rejectsResponseWithNullId() {
        // me != null but me.id() == null — the second arm of the guard.
        UserMeClient userMeClient = mock(UserMeClient.class);
        when(userMeClient.getMe()).thenReturn(user(null));

        CallerIdentity identity = identity(userMeClient);

        assertNull(identity.getUuid());
        assertThrows(NotFoundException.class, identity::requireUuid);
    }

    private static CallerIdentity identity(UserMeClient userMeClient) {
        CallerIdentity identity = new CallerIdentity();
        identity.userMeClient = userMeClient;
        return identity;
    }

    private static UserPublicResponse user(UUID id) {
        return new UserPublicResponse(id, "User", null, null, null,
                null, null, null, 0L, 0L, null);
    }
}
