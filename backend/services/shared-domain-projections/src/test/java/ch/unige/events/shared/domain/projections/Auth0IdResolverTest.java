package ch.unige.events.shared.domain.projections;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

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
}
