package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class EventSearchServiceCoverageProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.arc.exclude-types",
                "ch.unige.events.service.EventSearchServiceMock,ch.unige.events.resource.*");
    }
}
