package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class EventSearchService {

    @Transactional
    public List<EventDTO> search(String q, EventCategory category,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (q != null && !q.isBlank()) {
            // ILIKE simulé via LOWER() — compatible JPQL + PostgreSQL
            // Les parenthèses sont obligatoires pour isoler le OR face aux AND suivants
            conditions.add("(lower(title) like :q or lower(description) like :q)");
            params.put("q", "%" + q.toLowerCase(Locale.ROOT) + "%");
        }
        if (category != null) {
            conditions.add("category = :category");
            params.put("category", category);
        }
        if (dateFrom != null) {
            conditions.add("startDate >= :dateFrom");
            params.put("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conditions.add("startDate <= :dateTo");
            params.put("dateTo", dateTo.atTime(23, 59, 59));
        }

        PanacheQuery<Event> query;
        if (conditions.isEmpty()) {
            query = Event.find("order by startDate, id");
        } else {
            query = Event.find(String.join(" AND ", conditions) + " order by startDate, id", params);
        }

        return query.page(page, size).list().stream().map(EventDTO::from).toList();
    }
}
