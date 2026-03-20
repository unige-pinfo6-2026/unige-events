package ch.unige.events;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@Mock
@Dependent
public class MockUserInfoProducer {

    @Inject
    SecurityIdentity securityIdentity;

    @Produces
    @Dependent
    public UserInfo userInfo() {
        // Check if there's an email attribute from @TestSecurity
        Object email = securityIdentity.getAttribute("email");
        if (email != null && !email.toString().trim().isEmpty()) {
            return new UserInfo("{\"email\": \"" + email + "\"}");
        }
        // Return null if no email attribute - this will trigger the validation in UserService
        return null;
    }
}
