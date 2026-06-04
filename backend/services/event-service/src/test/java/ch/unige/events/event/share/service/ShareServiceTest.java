package ch.unige.events.event.share.service;

import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.share.dto.ShareResponse;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestSecurity(user = "auth0|share-test")
@SuppressWarnings("java:S100")
class ShareServiceTest {

    @Inject ShareService service;
    @Inject EntityManager em;

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    private Event create(UUID creatorId, EventStatus status) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = creatorId;
        e.status = status;
        e.persist();
        return e;
    }

    private Event create() {
        return create(UUID.randomUUID(), EventStatus.PUBLISHED);
    }

    private static String auth0Of(UUID userId) {
        return "auth0|" + userId;
    }

    @Test
    @TestTransaction
    void getShareInfo_byCreator_returnsCode() {
        UUID creatorId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(creatorId), false);
        assertNotNull(resp.shortCode());
        assertEquals(8, resp.shortCode().length());
        assertTrue(resp.shareUrl().contains("/events/" + e.id));
    }

    @Test
    @TestTransaction
    void getShareInfo_idempotent_keepsCode() {
        UUID creatorId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));

        ShareResponse first = service.getShareInfo(e.id, auth0Of(creatorId), false);
        ShareResponse second = service.getShareInfo(e.id, auth0Of(creatorId), false);
        assertEquals(first.shortCode(), second.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_unknown_throws404() {
        JwtTestContext.set(JwtTestHelper.jwtFor(UUID.randomUUID()));
        assertThrows(NotFoundException.class,
                () -> service.getShareInfo(99999L, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void resolveByShortCode_returnsEvent() {
        UUID creatorId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(creatorId), false);
        em.flush();
        Event resolved = service.resolveByShortCode(resp.shortCode());
        assertEquals(e.id, resolved.id);
    }

    @Test
    @TestTransaction
    void resolveByShortCode_unknown_throws404() {
        assertThrows(NotFoundException.class, () -> service.resolveByShortCode("nope"));
    }

    // ---- ISSUE-92 anti-oracle + authorization sentinels ----

    @Test
    @TestTransaction
    void getShareInfo_byAcceptedCoOrg_returnsCode() {
        UUID creatorId = UUID.randomUUID();
        UUID coOrgId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = coOrgId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(coOrgId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(coOrgId), false);
        assertNotNull(resp.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_byAdmin_returnsCode() {
        UUID creatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(adminId), true);
        assertNotNull(resp.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_byOtherUserOnPublished_throwsForbidden() {
        UUID creatorId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(otherId));

        assertThrows(ForbiddenException.class,
                () -> service.getShareInfo(e.id, auth0Of(otherId), false));
    }

    @Test
    @TestTransaction
    void getShareInfo_byOtherUserOnDraft_throws404() {
        UUID creatorId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.DRAFT);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(otherId));

        assertThrows(NotFoundException.class,
                () -> service.getShareInfo(e.id, auth0Of(otherId), false));
    }

    @Test
    @TestTransaction
    void getShareInfo_byOtherUserOnBanned_throws404() {
        UUID creatorId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.BANNED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(otherId));

        assertThrows(NotFoundException.class,
                () -> service.getShareInfo(e.id, auth0Of(otherId), false));
    }

    // ---- anonymous caller (callerUuid == null) — L67/L68/L71 operand legs ----

    @Test
    @TestTransaction
    void getShareInfo_anonymousOnPublished_throwsForbidden() {
        // JWT cleared → callerUuid == null → isCreator/isAcceptedCoOrg both
        // short-circuit to false on the null operand (L67/L68). On a PUBLISHED
        // event the anti-oracle 404 (L71) is skipped (status == PUBLISHED) and
        // the authorization check rejects with 403.
        UUID creatorId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.clear();

        assertThrows(ForbiddenException.class,
                () -> service.getShareInfo(e.id, null, false));
    }

    @Test
    @TestTransaction
    void getShareInfo_anonymousOnDraft_throws404() {
        // callerUuid == null + status != PUBLISHED + not admin → the L71
        // anti-oracle branch fires (all operands false) → 404.
        UUID creatorId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.DRAFT);
        em.flush();
        JwtTestContext.clear();

        assertThrows(NotFoundException.class,
                () -> service.getShareInfo(e.id, null, false));
    }

    // ---- L71 anti-oracle operand legs on a non-PUBLISHED event ----
    // The previous pass only ran admin/creator/accepted-co-org on PUBLISHED
    // events, where `event.status != PUBLISHED` short-circuits L71 before the
    // `!isAdmin`/`!isCreator`/`!isAcceptedCoOrg` operands. These three drive a
    // DRAFT event so `status != PUBLISHED` is true and each of the negated
    // flags evaluates its false leg → no 404 (and no 403 at L74), code returned.

    @Test
    @TestTransaction
    void getShareInfo_byAdminOnDraft_returnsCode() {
        // L71: status != PUBLISHED (true) but isAdmin → `!isAdmin` is false →
        // anti-oracle 404 skipped; L74 also passes (admin) → code returned.
        UUID creatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.DRAFT);
        em.flush();
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(adminId), true);
        assertNotNull(resp.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_byCreatorOnDraft_returnsCode() {
        // L71: status != PUBLISHED (true), not admin, but isCreator →
        // `!isCreator` is false → anti-oracle 404 skipped; L74 passes too.
        UUID creatorId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.DRAFT);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(creatorId), false);
        assertNotNull(resp.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_byAcceptedCoOrgOnDraft_returnsCode() {
        // L71: status != PUBLISHED (true), not admin, not creator, but
        // isAcceptedCoOrg → `!isAcceptedCoOrg` is false → anti-oracle 404
        // skipped; L74 passes too → code returned.
        UUID creatorId = UUID.randomUUID();
        UUID coOrgId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.DRAFT);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = coOrgId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(coOrgId));

        ShareResponse resp = service.getShareInfo(e.id, auth0Of(coOrgId), false);
        assertNotNull(resp.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_doesNotPersistShareCode_whenForbidden() {
        UUID creatorId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Event e = create(creatorId, EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.set(JwtTestHelper.jwtFor(otherId));

        assertThrows(ForbiddenException.class,
                () -> service.getShareInfo(e.id, auth0Of(otherId), false));

        em.clear();
        Event reloaded = Event.findById(e.id);
        assertNull(reloaded.shareCode);
    }
}
