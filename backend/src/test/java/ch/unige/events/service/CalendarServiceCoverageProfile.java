package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class CalendarServiceCoverageProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("quarkus.datasource.active", "true");
        overrides.put("quarkus.hibernate-orm.active", "true");
        overrides.put("quarkus.datasource.devservices.enabled", "true");
        overrides.put("quarkus.arc.exclude-types",
                "ch.unige.events.service.FavoriteServiceMock," +
                "ch.unige.events.service.ShareServiceMock," +
                "ch.unige.events.service.CalendarServiceMock," +
                "ch.unige.events.service.AttendanceServiceMock," +
                "ch.unige.events.resource.*");
        return overrides;
    }
}
