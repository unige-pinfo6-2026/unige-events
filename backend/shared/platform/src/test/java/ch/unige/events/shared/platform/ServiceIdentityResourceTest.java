package ch.unige.events.shared.platform;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceIdentityResourceTest {

    @Test
    void identity_returnsServiceAndModuleFromConfigProperty() {
        ServiceIdentityResource r = new ServiceIdentityResource();
        r.serviceName = "event-service";

        Map<String, String> body = r.identity();

        assertEquals("event-service", body.get("service"));
        assertEquals("ch.unige.events:event-service", body.get("module"));
        assertEquals(2, body.size());
    }

    @Test
    void identity_withDifferentServiceName_reflectsValue() {
        ServiceIdentityResource r = new ServiceIdentityResource();
        r.serviceName = "user-service";

        Map<String, String> body = r.identity();

        assertEquals("user-service", body.get("service"));
        assertTrue(body.get("module").endsWith(":user-service"));
    }
}
