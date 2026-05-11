package ch.unige.events.shared.domain.projections;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void resolveUserUuid_malformedClaim_logsWarnAndReturnsNull() {
        Logger jul = Logger.getLogger(Auth0IdResolver.class.getName());
        Level originalLevel = jul.getLevel();
        boolean originalUseParent = jul.getUseParentHandlers();
        List<LogRecord> captured = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        jul.addHandler(handler);
        jul.setLevel(Level.ALL);
        try {
            JsonWebToken jwt = mock(JsonWebToken.class);
            when(jwt.getClaim("uuid")).thenReturn("not-a-uuid");
            when(jwt.getName()).thenReturn("auth0|abc");

            assertNull(Auth0IdResolver.resolveUserUuid(jwt));

            assertTrue(
                captured.stream().anyMatch(r -> {
                    String msg = r.getMessage();
                    return msg != null && msg.contains("[AUTH0_UUID_MALFORMED]");
                }),
                "expected a log record containing [AUTH0_UUID_MALFORMED] but captured=" + captured);
        } finally {
            jul.removeHandler(handler);
            jul.setLevel(originalLevel);
            jul.setUseParentHandlers(originalUseParent);
        }
    }
}
