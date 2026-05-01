package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShareServiceCoverageProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("quarkus.arc.exclude-types",
            List.of(
                    "ch.unige.events.service.AttendanceServiceMock",
                    "ch.unige.events.service.CalendarServiceMock",
                    "ch.unige.events.service.EventSearchServiceMock",
                    "ch.unige.events.service.EventServiceMock",
                    "ch.unige.events.service.EventStatsServiceMock",
                    "ch.unige.events.service.EventViewServiceMock",
                    "ch.unige.events.service.FavoriteServiceMock",
                    "ch.unige.events.service.FeaturedServiceMock",
                    "ch.unige.events.service.ShareServiceMock",
                    "ch.unige.events.service.UserServiceMock",
                    "ch.unige.events.resource.*"
            ).stream().collect(Collectors.joining(","))
        );

        return overrides;
    }
}
