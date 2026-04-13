package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.EventStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

    @Inject EntityManager entityManager;

    @Transactional
    public List<EventDTO> search(String q, EventCategory category, Faculty faculty,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        // ILIKE simulé via LOWER() — compatible JPQL + PostgreSQL
        // Les parenthèses sont obligatoires pour isoler le OR face aux AND suivants
        // startDate is stored as UTC LocalDateTime; Zurich day boundaries are converted to UTC before comparing.
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
        if (faculty != null) {
            conditions.add("e.faculty = :faculty");
            params.put("faculty", faculty);
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

        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
                ids, AttendanceStatus.ATTENDING, entityManager);

        return events.stream()
                .map(e -> EventDTO.from(e, attendingCounts.getOrDefault(e.id, 0L)))
                .toList();
    }
}