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
    @SuppressWarnings("java:S107") // Filter-heavy search endpoint — flat params match the REST query signature 1:1.
    public List<EventDTO> search(String q, EventCategory category, Faculty faculty, Boolean facultyNone,
                                  List<String> tags,
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
        // facultyNone=true has priority over faculty — mutually exclusive filter.
        if (Boolean.TRUE.equals(facultyNone)) {
            conditions.add("e.faculty IS NULL");
        } else if (faculty != null) {
            conditions.add("e.faculty = :faculty");
            params.put("faculty", faculty);
        }
        // Tags (SCRUM-131) : substring match case-insensitive, sémantique OR entre les valeurs.
        // Ex. ?tags=foot matche un event dont un tag est "football". `%` et `_` saisis sont traités
        // littéralement via ESCAPE '|' + escapeLikePattern.
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

        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
                ids, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(
                ids, AttendanceStatus.WAITLISTED, entityManager);

        return events.stream()
                .map(e -> {
                    long att = attendingCounts.getOrDefault(e.id, 0L);
                    long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                    return EventDTO.from(e, att, EventService.computeAvailableSpots(e.capacity, att), wait);
                })
                .toList();
    }

    // Ordre important : échapper d'abord le char d'échappement '|' lui-même,
    // sinon "|%" deviendrait "||%" puis "||||%" etc.
    private static String escapeLikePattern(String s) {
        return s.replace("|", "||").replace("%", "|%").replace("_", "|_");
    }
}