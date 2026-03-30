package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class EventSearchServiceCoverageProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("quarkus.datasource.active", "true");
        overrides.put("quarkus.hibernate-orm.active", "true");
        overrides.put("quarkus.datasource.devservices.enabled", "true");
        overrides.put("quarkus.arc.exclude-types",
                "ch.unige.events.service.EventSearchServiceMock,ch.unige.events.resource.*");
        return overrides;
    }
}
