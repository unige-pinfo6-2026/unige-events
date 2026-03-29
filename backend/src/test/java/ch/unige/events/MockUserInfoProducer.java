package ch.unige.events;

import org.eclipse.microprofile.jwt.JsonWebToken;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mock
@Dependent
public class MockUserInfoProducer {

    @Inject
    SecurityIdentity securityIdentity;

    @Produces
    @Dependent
    public JsonWebToken userInfo() {
        Map<String, Object> claims = new HashMap<>();
        if (securityIdentity.getPrincipal() != null) {
            claims.put("sub", securityIdentity.getPrincipal().getName());
        }
        copyClaim(claims, "email");
        copyClaim(claims, "name");
        copyClaim(claims, "given_name");
        copyClaim(claims, "family_name");
        copyClaim(claims, "picture");

        return new JsonWebToken() {
            @Override
            public String getName() {
                return (String) claims.get("sub");
            }

            @Override
            public Set<String> getClaimNames() {
                return claims.keySet();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getClaim(String claimName) {
                return (T) claims.get(claimName);
            }
        };
    }

    private void copyClaim(Map<String, Object> claims, String claimName) {
        Object value = securityIdentity.getAttribute(claimName);
        if (value != null && !value.toString().trim().isEmpty()) {
            claims.put(claimName, value.toString());
        }
    }
}
