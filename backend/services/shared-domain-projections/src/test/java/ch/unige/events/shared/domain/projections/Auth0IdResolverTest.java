package ch.unige.events.shared.domain.projections;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Auth0IdResolverTest {

    @Test
    void nullJwt_returnsNull() {
        assertNull(Auth0IdResolver.resolveUserId(null));
    }

    @Test
    void presentJwt_returnsSubClaim() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getName()).thenReturn("auth0|123");
        assertEquals("auth0|123", Auth0IdResolver.resolveUserId(jwt));
    }

    @Test
    void resolveUserUuid_nullJwt_returnsNull() {
        assertNull(Auth0IdResolver.resolveUserUuid(null));
    }

    @Test
    void resolveUserUuid_missingClaim_returnsNull() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getClaim("uuid")).thenReturn(null);
        assertNull(Auth0IdResolver.resolveUserUuid(jwt));
    }

    @Test
    void resolveUserUuid_validClaim_returnsParsedUuid() {
        UUID expected = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getClaim("uuid")).thenReturn(expected.toString());
        assertEquals(expected, Auth0IdResolver.resolveUserUuid(jwt));
    }

    @Test
    void resolveUserUuid_malformedClaim_returnsNull() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getClaim("uuid")).thenReturn("not-a-uuid");
        assertNull(Auth0IdResolver.resolveUserUuid(jwt));
    }
}
