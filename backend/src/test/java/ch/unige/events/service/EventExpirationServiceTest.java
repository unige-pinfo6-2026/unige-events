package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventExpirationServiceTest {

    @Inject
    EventExpirationService expirationService;

    @Inject
    EntityManager em;

    private User persistUser(String auth0Id, String email) {
        return ServiceCoverageTestHelper.persistUser(em, auth0Id, email);
    }

    private Event persistEventWithEndDate(String title, EventStatus status,
                                          LocalDateTime endDate, User creator) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = endDate.minusDays(1);
        event.endDate = endDate;
        event.category = EventCategory.ACADEMIC;
        event.status = status;
        event.creator = creator;
        em.persist(event);
        em.flush();
        return event;
    }

    @Test
    @TestTransaction
    void expireEvents_publishedPastEndDate_setsStatusExpired() {
        User user = persistUser("auth0|exp1", "exp1@test.com");
        Event past = persistEventWithEndDate("Past Event", EventStatus.PUBLISHED,
                LocalDateTime.now().minusHours(2), user);

        int count = expirationService.expireEvents();

        assertEquals(1, count);
        em.refresh(past);
        assertEquals(EventStatus.EXPIRED, past.status);
    }

    @Test
    @TestTransaction
    void expireEvents_publishedFutureEndDate_remainsPublished() {
        User user = persistUser("auth0|exp2", "exp2@test.com");
        Event future = persistEventWithEndDate("Future Event", EventStatus.PUBLISHED,
                LocalDateTime.now().plusHours(2), user);

        int count = expirationService.expireEvents();

        assertEquals(0, count);
        em.refresh(future);
        assertEquals(EventStatus.PUBLISHED, future.status);
    }

    @Test
    @TestTransaction
    void expireEvents_alreadyExpired_notReprocessed() {
        User user = persistUser("auth0|exp3", "exp3@test.com");
        persistEventWithEndDate("Already Expired", EventStatus.EXPIRED,
                LocalDateTime.now().minusDays(1), user);

        int count = expirationService.expireEvents();

        assertEquals(0, count);
    }

    @Test
    @TestTransaction
    void expireEvents_draftWithPastEndDate_notExpired() {
        User user = persistUser("auth0|exp4", "exp4@test.com");
        Event draft = persistEventWithEndDate("Draft Event", EventStatus.DRAFT,
                LocalDateTime.now().minusHours(1), user);

        int count = expirationService.expireEvents();

        assertEquals(0, count);
        em.refresh(draft);
        assertEquals(EventStatus.DRAFT, draft.status);
    }

    @Test
    @TestTransaction
    void expireEvents_noEventsToExpire_returnsZero() {
        int count = expirationService.expireEvents();
        assertEquals(0, count);
    }

    @Test
    @TestTransaction
    void expireEvents_multiplePastPublished_allExpired() {
        User user = persistUser("auth0|exp5", "exp5@test.com");
        persistEventWithEndDate("Past 1", EventStatus.PUBLISHED,
                LocalDateTime.now().minusHours(1), user);
        persistEventWithEndDate("Past 2", EventStatus.PUBLISHED,
                LocalDateTime.now().minusMinutes(30), user);

        int count = expirationService.expireEvents();

        assertEquals(2, count);
    }
}
