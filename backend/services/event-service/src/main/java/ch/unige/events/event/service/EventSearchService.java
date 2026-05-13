package ch.unige.events.event.service;

import ch.unige.events.shared.domain.projections.EventCapacity;
import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class EventSearchService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Inject @RestClient EngagementServiceClient engagementClient;

    @Transactional
    @SuppressWarnings("java:S107")
    public List<EventDTO> search(String q, EventCategory category, Faculty faculty, Boolean facultyNone,
                                  List<String> tags,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        conditions.add("e.status = :status");
        params.put("status", EventStatus.PUBLISHED);

        if (q != null && !q.isBlank()) {
            conditions.add("(lower(e.title) like :q or lower(e.description) like :q)");
            params.put("q", "%" + q.toLowerCase(Locale.ROOT) + "%");
        }
        if (category != null) {
            conditions.add("e.category = :category");
            params.put("category", category);
        }
        if (Boolean.TRUE.equals(facultyNone)) {
            conditions.add("e.faculty IS NULL");
        } else if (faculty != null) {
            conditions.add("e.faculty = :faculty");
            params.put("faculty", faculty);
        }
        List<String> normalizedTags = EventService.normalizeTags(tags);
        if (!normalizedTags.isEmpty()) {
            List<String> tagClauses = new ArrayList<>();
            for (int i = 0; i < normalizedTags.size(); i++) {
                String paramName = "tag" + i;
                tagClauses.add("LOWER(t) LIKE :" + paramName + " ESCAPE '|'");
                params.put(paramName, "%" + escapeLikePattern(normalizedTags.get(i)) + "%");
            }
            conditions.add("EXISTS (SELECT 1 FROM Event e2 JOIN e2.tags t WHERE e2.id = e.id AND ("
                    + String.join(" OR ", tagClauses) + "))");
        }
        if (dateFrom != null) {
            LocalDateTime dateFromUtc = dateFrom.atStartOfDay(ZURICH).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            conditions.add("e.startDate >= :dateFrom");
            params.put("dateFrom", dateFromUtc);
        }
        if (dateTo != null) {
            LocalDateTime dateToUtc = dateTo.atTime(23, 59, 59).atZone(ZURICH).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            conditions.add("e.startDate <= :dateTo");
            params.put("dateTo", dateToUtc);
        }

        jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        jpql.append(" ORDER BY e.startDate, e.id");

        List<Event> events = Event.<Event>find(jpql.toString(), params)
                .page(page, size)
                .list();

        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(ids);
        if (summaries == null) {
            summaries = Map.of();
        }
        Map<Long, AttendanceSummary> finalSummaries = summaries;

        return events.stream()
                .map(e -> {
                    AttendanceSummary s = finalSummaries.getOrDefault(
                            e.id, AttendanceSummary.of(0L, 0L));
                    long att = s.attending();
                    long wait = s.waitlisted();
                    return EventDTO.from(e, att, EventCapacity.computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private static String escapeLikePattern(String s) {
        return s.replace("|", "||").replace("%", "|%").replace("_", "|_");
    }
}
