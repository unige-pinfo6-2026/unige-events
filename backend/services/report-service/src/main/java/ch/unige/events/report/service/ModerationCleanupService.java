package ch.unige.events.report.service;

import ch.unige.events.report.config.AppConfig;
import ch.unige.events.report.entity.EventStatus;
import ch.unige.events.report.entity.EventStub;
import ch.unige.events.report.entity.ReportStatus;
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

/**
 * Hides events whose PENDING report count has reached the configured
 * auto-hide threshold. Same logic as the legacy
 * ch.unige.events.service.ModerationCleanupService — only the entity
 * names changed (Event → EventStub, Report's owning module is now this
 * service).
 */
@ApplicationScoped
public class ModerationCleanupService {

    private static final Logger log = Logger.getLogger(ModerationCleanupService.class);

    @Inject AppConfig appConfig;

    @Inject EntityManager em;

    int threshold;

    @PostConstruct
    void init() {
        this.threshold = appConfig.moderationAutoHideThreshold();
    }

    @Transactional
    public void runCleanup() {
        Map<EventStub, Long> pendingCounts = fetchPendingReportCounts();
        List<EventStub> toHide = atOrAboveThreshold(pendingCounts, threshold);
        toHide.forEach(this::hide);
        log.infof("ModerationCleanup: %d event(s) auto-hidden (threshold=%d).", toHide.size(), threshold);
    }

    Map<EventStub, Long> fetchPendingReportCounts() {
        List<Object[]> rows = em.createQuery(
                "SELECT r.event, COUNT(r) FROM Report r WHERE r.status = :status GROUP BY r.event",
                Object[].class
        ).setParameter("status", ReportStatus.PENDING).getResultList();
        return rows.stream().collect(Collectors.toMap(row -> (EventStub) row[0], row -> (Long) row[1]));
    }

    static List<EventStub> atOrAboveThreshold(Map<EventStub, Long> pendingCounts, int threshold) {
        List<EventStub> result = new ArrayList<>();
        for (Map.Entry<EventStub, Long> entry : pendingCounts.entrySet()) {
            if (entry.getValue() >= threshold) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    void hide(EventStub event) {
        event.status = EventStatus.BANNED;
        // TODO: emit events.banned Kafka message once event-service ships
        // (PR 13). Until then the legacy notification path still observes
        // event.status flip in-process within legacy-monolith via the
        // shared schema.
    }
}
