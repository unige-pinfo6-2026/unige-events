package ch.unige.events.service;

import ch.unige.events.config.AppConfig;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.ReportStatus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModerationCleanupService {

    private static final Logger log = Logger.getLogger(ModerationCleanupService.class);

    @Inject
    AppConfig appConfig;

    @Inject
    EntityManager em;

    int threshold;

    @PostConstruct
    void init() {
        this.threshold = appConfig.moderationAutoHideThreshold();
    }

    @Transactional
    public void runCleanup() {
        Map<Event, Long> pendingCounts = fetchPendingReportCounts();
        List<Event> toHide = atOrAboveThreshold(pendingCounts, threshold);
        toHide.forEach(this::hide);
        log.infof("ModerationCleanup: %d event(s) auto-hidden (threshold=%d).", toHide.size(), threshold);
    }

    Map<Event, Long> fetchPendingReportCounts() {
        List<Object[]> rows = em.createQuery(
                "SELECT r.event, COUNT(r) FROM Report r WHERE r.status = :status GROUP BY r.event",
                Object[].class
        ).setParameter("status", ReportStatus.PENDING).getResultList();
        return rows.stream().collect(Collectors.toMap(row -> (Event) row[0], row -> (Long) row[1]));
    }

    static List<Event> atOrAboveThreshold(Map<Event, Long> pendingCounts, int threshold) {
        List<Event> result = new ArrayList<>();
        for (Map.Entry<Event, Long> entry : pendingCounts.entrySet()) {
            if (entry.getValue() >= threshold) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    void hide(Event event) {
        event.status = EventStatus.CANCELLED;
        // TODO: create a Notification of type EVENT_AUTO_REMOVED for event.creator once the Notification entity exists
    }
}
