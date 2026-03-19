package ch.unige.events.resource;

import ch.unige.events.service.UserService;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class UserResourceCoverageTest {

    @Test
    void extractEmailReturnsJwtClaimWhenPresent() throws Exception {
        JsonWebToken jwt = new StubJwt("auth0|jwt", "jwt@example.com");
        var identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(jwt)
            .addAttribute("email", "attribute@example.com")
            .build();

        UserResource resource = new UserResource(identity, new UserService(new NullInstance<>()));

        assertEquals("jwt@example.com", invokeExtractEmail(resource));
    }

    @Test
    void extractEmailFallsBackToSecurityAttributeWhenJwtEmailMissing() throws Exception {
        JsonWebToken jwt = new StubJwt("auth0|jwt", "   ");
        var identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(jwt)
            .addAttribute("email", "attribute@example.com")
            .build();

        UserResource resource = new UserResource(identity, new UserService(new NullInstance<>()));

        assertEquals("attribute@example.com", invokeExtractEmail(resource));
    }

    @Test
    void extractEmailFallsBackToSecurityAttributeWhenJwtEmailNull() throws Exception {
        JsonWebToken jwt = new StubJwt("auth0|jwt", null);
        var identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(jwt)
            .addAttribute("email", "attribute-null@example.com")
            .build();

        UserResource resource = new UserResource(identity, new UserService(new NullInstance<>()));

        assertEquals("attribute-null@example.com", invokeExtractEmail(resource));
    }

    @Test
    void extractEmailReturnsNullWhenNoSourceAvailable() throws Exception {
        Principal principal = () -> "auth0|plain";
        var identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(principal)
            .addAttribute("email", "   ")
            .build();

        UserResource resource = new UserResource(identity, new UserService(new NullInstance<>()));

        assertNull(invokeExtractEmail(resource));
    }

    private String invokeExtractEmail(UserResource resource) throws Exception {
        Method method = UserResource.class.getDeclaredMethod("extractEmail");
        method.setAccessible(true);
        return (String) method.invoke(resource);
    }

    private static final class StubJwt implements JsonWebToken {
        private final String name;
        private final String email;

        private StubJwt(String name, String email) {
            this.name = name;
            this.email = email;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getClaimNames() {
            return Set.of("email");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getClaim(String claimName) {
            if ("email".equals(claimName)) {
                return (T) email;
            }
            return null;
        }
    }

    private static final class NullInstance<T> implements Instance<T> {

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return true;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(T instance) {
        }

        @Override
        public Handle<T> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            return List.of();
        }

        @Override
        public T get() {
            return null;
        }

        @Override
        public Iterator<T> iterator() {
            return List.<T>of().iterator();
        }
    }
}
