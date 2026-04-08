package ch.unige.events.service;

import java.util.HashMap;
import java.util.Map;

public class AttendanceServiceCoverageProfile extends SharedServiceCoverageProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
        // Also exclude EventServiceMock so the real EventService (with DB) is used
        // when these tests call eventService.getById() to verify attendance counts.
        overrides.put("quarkus.arc.exclude-types",
                "ch.unige.events.service.FavoriteServiceMock," +
                "ch.unige.events.service.ShareServiceMock," +
                "ch.unige.events.service.CalendarServiceMock," +
                "ch.unige.events.service.AttendanceServiceMock," +
                "ch.unige.events.service.EventServiceMock," +
                "ch.unige.events.resource.*");
        return overrides;
    }
}
