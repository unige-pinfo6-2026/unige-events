package ch.unige.events.report.service;

import ch.unige.events.report.config.AppConfig;
import ch.unige.events.report.entity.ReportStatus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hides events whose PENDING report count has reached the configured
 * auto-hide threshold.
 *
 * <p>Étape 3.2 finalization-ultimate (STUB-001 / Décision H): the
 * {@code EventStub} navigation is replaced by a Long event id; the
 * auto-ban path fires {@code events.banned} via Kafka (consumer
 * event-service applies status=BANNED locally — no more cross-schema
 * mutation).
 */
@ApplicationScoped
public class ModerationCleanupService {

    private static final Logger log = Logger.getLogger(ModerationCleanupService.class);

    @Inject AppConfig appConfig;

    @Inject EntityManager em;

    @Inject jakarta.enterprise.event.Event<ch.unige.events.shared.kafka.events.EventBannedEvent> bannedEvent;

    int threshold;

    @PostConstruct
    void init() {
        this.threshold = appConfig.moderationAutoHideThreshold();
    }

    @Transactional
    public void runCleanup() {
        Map<Long, Long> pendingCounts = fetchPendingReportCounts();
        List<Long> toHide = atOrAboveThreshold(pendingCounts, threshold);
        toHide.forEach(this::hide);
        log.infof("ModerationCleanup: %d event(s) auto-hidden (threshold=%d).", toHide.size(), threshold);
    }

    Map<Long, Long> fetchPendingReportCounts() {
        List<Object[]> rows = em.createQuery(
                "SELECT r.eventId, COUNT(r) FROM Report r WHERE r.status = :status GROUP BY r.eventId",
                Object[].class
        ).setParameter("status", ReportStatus.PENDING).getResultList();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    static List<Long> atOrAboveThreshold(Map<Long, Long> pendingCounts, int threshold) {
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : pendingCounts.entrySet()) {
            if (entry.getValue() >= threshold) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    void hide(Long eventId) {
        // Auto-ban path: fire events.banned via CDI ; the bridge publishes
        // AFTER_SUCCESS, event-service consumer applies status=BANNED.
        // bannedBy is null — no human admin in the auto-cleanup flow.
        bannedEvent.fire(ch.unige.events.shared.kafka.events.EventBannedEvent.banned(
                eventId, null, "auto-cleanup-threshold-exceeded"));
    }
}
