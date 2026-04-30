package ch.unige.events.service;

import ch.unige.events.entity.EventStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class EventExpirationService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public int expireEvents() {
        return entityManager.createQuery(
                "UPDATE Event e SET e.status = :expired " +
                "WHERE e.status = :published AND e.endDate < :now")
                .setParameter("expired", EventStatus.EXPIRED)
                .setParameter("published", EventStatus.PUBLISHED)
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();
    }
}
