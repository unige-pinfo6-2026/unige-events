package ch.unige.events.event.share.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.share.dto.ShareResponse;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ShareServiceTest {

    @Inject ShareService service;
    @Inject EntityManager em;

    private Event create() {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void getShareInfo_assignsShareCode() {
        Event e = create();
        em.flush();
        ShareResponse resp = service.getShareInfo(e.id);
        assertNotNull(resp.shortCode());
        assertEquals(8, resp.shortCode().length());
        assertTrue(resp.shareUrl().contains("/events/" + e.id));
    }

    @Test
    @TestTransaction
    void getShareInfo_idempotent_keepsCode() {
        Event e = create();
        em.flush();
        ShareResponse first = service.getShareInfo(e.id);
        ShareResponse second = service.getShareInfo(e.id);
        assertEquals(first.shortCode(), second.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_unknown_throws404() {
        assertThrows(NotFoundException.class, () -> service.getShareInfo(99999L));
    }

    @Test
    @TestTransaction
    void resolveByShortCode_returnsEvent() {
        Event e = create();
        em.flush();
        ShareResponse resp = service.getShareInfo(e.id);
        em.flush();
        Event resolved = service.resolveByShortCode(resp.shortCode());
        assertEquals(e.id, resolved.id);
    }

    @Test
    @TestTransaction
    void resolveByShortCode_unknown_throws404() {
        assertThrows(NotFoundException.class, () -> service.resolveByShortCode("nope"));
    }
}
