package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.Faculty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// startDate is stored as UTC LocalDateTime (TIMESTAMP WITHOUT TZ in PostgreSQL).
// To filter by calendar day in Europe/Zurich we convert the Zurich date boundary to UTC
// before comparing, rather than using AT TIME ZONE in HQL.
// Reason: TIMESTAMP WITHOUT TZ AT TIME ZONE 'zone' in PostgreSQL treats the stored value
// AS IF it were already in that zone (wrong direction). The Java conversion is unambiguous.

@ApplicationScoped
public class EventSearchService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Transactional
    public List<EventDTO> search(String q, EventCategory category, List<Faculty> faculties,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        boolean filterFaculties = faculties != null && !faculties.isEmpty();

        // ILIKE simulé via LOWER() — compatible JPQL + PostgreSQL
        // Les parenthèses sont obligatoires pour isoler le OR face aux AND suivants
        // startDate is stored as UTC LocalDateTime; Zurich day boundaries are converted to UTC before comparing.
        StringBuilder jpql = new StringBuilder("SELECT DISTINCT e FROM Event e");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (filterFaculties) {
            jpql.append(" JOIN e.faculties f");
            conditions.add("f IN :faculties");
            params.put("faculties", faculties);
        }
        if (q != null && !q.isBlank()) {
            conditions.add("(lower(e.title) like :q or lower(e.description) like :q)");
            params.put("q", "%" + q.toLowerCase(Locale.ROOT) + "%");
        }
        if (category != null) {
            conditions.add("e.category = :category");
            params.put("category", category);
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

        if (!conditions.isEmpty()) {
            jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        jpql.append(" ORDER BY e.startDate, e.id");

        return Event.<Event>find(jpql.toString(), params)
                .page(page, size)
                .list()
                .stream()
                .map(EventDTO::from)
                .toList();
    }
}
