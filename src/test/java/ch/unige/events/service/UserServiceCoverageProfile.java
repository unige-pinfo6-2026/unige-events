package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class UserServiceCoverageProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("quarkus.datasource.active", "true");
        overrides.put("quarkus.hibernate-orm.active", "true");
        overrides.put("quarkus.datasource.devservices.enabled", "true");
        overrides.put("quarkus.arc.exclude-types", "ch.unige.events.service.UserServiceMock");
        return overrides;
    }
}
