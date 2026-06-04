package ch.unige.events.event.coorganizer.service;

import ch.unige.events.event.coorganizer.dto.CoOrganizerDTO;
import ch.unige.events.event.coorganizer.dto.CoOrganizerInvitationDTO;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "auth0|coorg-test")
@SuppressWarnings({"java:S5961", "java:S100"})
class EventCoOrganizerServiceTest {

    @Inject EventCoOrganizerService service;
    @Inject EntityManager em;

    @InjectMock @RestClient UserServiceClient userClient;
    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID creatorId = UUID.randomUUID();
    private final UUID inviteeId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event createEvent(UUID creator) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(2);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = creator;
        e.status = EventStatus.PUBLISHED;
        e.persist();
        return e;
    }

    // ---- invite ----

    @Test
    @TestTransaction
    void invite_byCreator_createsInvitation() {
        Event e = createEvent(creatorId);
        em.flush();

        CoOrganizerDTO dto = service.invite(e.id, "auth0|x", inviteeId, false);
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
        assertEquals(inviteeId, dto.userId());
    }

    @Test
    @TestTransaction
    void invite_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.invite(99999L, "auth0|x", inviteeId, false));
    }

    @Test
    @TestTransaction
    void invite_byNonCreator_nonAdmin_throws403() {
        Event e = createEvent(UUID.randomUUID());
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.invite(e.id, "auth0|x", inviteeId, false));
    }

    @Test
    @TestTransaction
    void invite_self_throws400() {
        Event e = createEvent(creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.invite(e.id, "auth0|x", creatorId, false));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void invite_unknownTargetUser_throws404() {
        Event e = createEvent(creatorId);
        em.flush();
        UUID missingId = UUID.randomUUID();
        when(userClient.getById(missingId)).thenThrow(new NotFoundException("user not found"));
        assertThrows(NotFoundException.class,
                () -> service.invite(e.id, "auth0|x", missingId, false));
    }


    @Test
    @TestTransaction
    void invite_alreadyInvited_throws409() {
        Event e = createEvent(creatorId);
        EventCoOrganizer existing = new EventCoOrganizer();
        existing.eventId = e.id;
        existing.userId = inviteeId;
        existing.status = CoOrganizerStatus.PENDING;
        existing.persist();
        em.flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.invite(e.id, "auth0|x", inviteeId, false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void invite_byAdmin_succeeds() {
        Event e = createEvent(UUID.randomUUID()); // someone else's event
        em.flush();
        CoOrganizerDTO dto = service.invite(e.id, "auth0|admin", inviteeId, true);
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
    }

    @Test
    @TestTransaction
    void invite_anonymousCaller_throws404() {
        Event e = createEvent(creatorId);
        em.flush();
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.invite(e.id, "auth0|x", inviteeId, false));
    }

    // ---- accept ----

    @Test
    @TestTransaction
    void accept_pendingInvitation_movesToAccepted() {
        Event e = createEvent(creatorId);
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId; // caller's UUID
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();

        CoOrganizerDTO dto = service.accept(e.id, "auth0|x");
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
    }

    @Test
    @TestTransaction
    void accept_noInvitation_throws422() {
        Event e = createEvent(creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.accept(e.id, "auth0|x"));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void accept_anonymousCaller_throws404() {
        Event e = createEvent(creatorId);
        em.flush();
        JwtTestContext.clear();
        assertThrows(NotFoundException.class, () -> service.accept(e.id, "auth0|x"));
    }

    @Test
    @TestTransaction
    void accept_alreadyAccepted_idempotent() {
        Event e = createEvent(creatorId);
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.ACCEPTED;
        inv.persist();
        em.flush();

        CoOrganizerDTO dto = service.accept(e.id, "auth0|x");
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
    }

    // ---- decline ----

    @Test
    @TestTransaction
    void decline_pendingInvitation_deletes() {
        Event e = createEvent(creatorId);
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();

        service.decline(e.id, "auth0|x");
        em.flush();
        assertFalse(EventCoOrganizer.findByEventAndUser(e.id, creatorId).isPresent());
    }

    @Test
    @TestTransaction
    void decline_noInvitation_throws422() {
        Event e = createEvent(creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.decline(e.id, "auth0|x"));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void decline_anonymousCaller_throws404() {
        Event e = createEvent(creatorId);
        em.flush();
        JwtTestContext.clear();
        assertThrows(NotFoundException.class, () -> service.decline(e.id, "auth0|x"));
    }

    @Test
    @TestTransaction
    void decline_acceptedInvitation_throws422() {
        Event e = createEvent(creatorId);
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.ACCEPTED;
        inv.persist();
        em.flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.decline(e.id, "auth0|x"));
        assertEquals(422, ex.getResponse().getStatus());
        assertTrue(EventCoOrganizer.findByEventAndUser(e.id, creatorId).isPresent(),
                "Accepted invitation must not be deleted by decline() — use DELETE /events/{id}/co-organizers/{userId}");
    }

    // ---- remove ----

    @Test
    @TestTransaction
    void remove_byCreator_deletes() {
        Event e = createEvent(creatorId);
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = inviteeId;
        inv.status = CoOrganizerStatus.ACCEPTED;
        inv.persist();
        em.flush();

        service.remove(e.id, "auth0|x", inviteeId, false);
        em.flush();
        assertFalse(EventCoOrganizer.findByEventAndUser(e.id, inviteeId).isPresent());
    }

    @Test
    @TestTransaction
    void remove_byNonCreator_throws403() {
        Event e = createEvent(UUID.randomUUID());
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.remove(e.id, "auth0|x", inviteeId, false));
    }

    @Test
    @TestTransaction
    void remove_byAdmin_succeeds() {
        Event e = createEvent(UUID.randomUUID());
        em.flush();
        // Admin removal is a silent no-op when no invitation row exists.
        service.remove(e.id, "auth0|admin", inviteeId, true);
        assertFalse(EventCoOrganizer.findByEventAndUser(e.id, inviteeId).isPresent());
    }

    @Test
    @TestTransaction
    void remove_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.remove(99999L, "auth0|x", inviteeId, false));
    }

    // ---- getCoOrganizers ----

    @Test
    @TestTransaction
    void getCoOrganizers_returnsList() {
        Event e = createEvent(creatorId);
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = inviteeId;
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();

        List<CoOrganizerDTO> list = service.getCoOrganizers(e.id);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getCoOrganizers_empty() {
        Event e = createEvent(creatorId);
        em.flush();
        List<CoOrganizerDTO> list = service.getCoOrganizers(e.id);
        assertTrue(list.isEmpty());
    }

    @Test
    @TestTransaction
    void getCoOrganizers_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.getCoOrganizers(99999L));
    }

    /** Persist a co-organizer row in the given status for the invitee. */
    private void persistCoOrg(Long eventId, CoOrganizerStatus status) {
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = eventId;
        inv.userId = inviteeId;
        inv.status = status;
        inv.persist();
    }

    // safeGetUser degradation — userClient.getById(...) failures must not
    // propagate; the DTO is returned with a null user (P1).

    @Test
    @TestTransaction
    void getCoOrganizers_userLookupNotFound_degradesToNullUser() {
        Event e = createEvent(creatorId);
        persistCoOrg(e.id, CoOrganizerStatus.ACCEPTED);
        em.flush();
        when(userClient.getById(inviteeId)).thenThrow(new NotFoundException("hard-deleted"));

        List<CoOrganizerDTO> list = service.getCoOrganizers(e.id);
        assertEquals(1, list.size());
        assertNull(list.get(0).displayName(), "user-not-found must degrade to a null-enriched DTO");
    }

    @Test
    @TestTransaction
    void getCoOrganizers_userLookupInfraFailure_degradesToNullUser() {
        Event e = createEvent(creatorId);
        persistCoOrg(e.id, CoOrganizerStatus.ACCEPTED);
        em.flush();
        when(userClient.getById(inviteeId))
                .thenThrow(new RuntimeException("CB open: user-service unreachable"));

        List<CoOrganizerDTO> list = service.getCoOrganizers(e.id);
        assertEquals(1, list.size());
        assertNull(list.get(0).displayName(),
                "infra failure must degrade to a null-enriched DTO, not propagate");
    }

    // ---- getMyInvitations ----

    @Test
    @TestTransaction
    void getMyInvitations_returnsPendingByDefault() {
        Event e = createEvent(UUID.randomUUID());
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();

        List<CoOrganizerInvitationDTO> list = service.getMyInvitations("auth0|x", null, 0, 10);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getMyInvitations_filterByAccepted() {
        Event e = createEvent(UUID.randomUUID());
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.ACCEPTED;
        inv.persist();
        em.flush();

        List<CoOrganizerInvitationDTO> list = service.getMyInvitations("auth0|x",
                CoOrganizerStatus.ACCEPTED, 0, 10);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getMyInvitations_anonymousCaller_throws404() {
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.getMyInvitations("auth0|x", null, 0, 10));
    }

    @Test
    @TestTransaction
    void getMyInvitations_emptyForUser() {
        List<CoOrganizerInvitationDTO> list = service.getMyInvitations("auth0|x", null, 0, 10);
        assertTrue(list.isEmpty());
    }

    @Test
    @TestTransaction
    void getMyInvitations_nullTagsEvent_yieldsEmptyTagList() {
        // coorganizer/dto/EventDTO L105 tags==null arm — Event.tags defaults to
        // an empty ArrayList, so it must be explicitly nulled. findByIdsAsDTO
        // builds the coorganizer EventDTO from the invitation's event; the
        // null-tags arm yields an empty list. Driven through the @QuarkusTest
        // service so jacoco counts the executed DTO bytecode.
        Event e = createEvent(UUID.randomUUID());
        e.tags = null;
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();

        List<CoOrganizerInvitationDTO> list = service.getMyInvitations("auth0|x", null, 0, 10);
        assertEquals(1, list.size());
        assertEquals(List.of(), list.get(0).event().tags());
    }

    @Test
    @TestTransaction
    void getMyInvitations_summariesNullClient_safe() {
        // findByIdsAsDTO defends against a null bulk-summary response (P2):
        // the @Fallback default on the engagement client. The invitation
        // must still resolve with zeroed attendance counts, no NPE.
        Event e = createEvent(UUID.randomUUID());
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();
        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);

        List<CoOrganizerInvitationDTO> list = service.getMyInvitations("auth0|x", null, 0, 10);
        assertEquals(1, list.size());
    }

    // ---- isAcceptedFor ----

    @Test
    @TestTransaction
    void isAcceptedFor_acceptedInvitation_returnsTrue() {
        Event e = createEvent(UUID.randomUUID());
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.ACCEPTED;
        inv.persist();
        em.flush();
        assertTrue(service.isAcceptedFor(e.id, creatorId));
    }

    @Test
    @TestTransaction
    void isAcceptedFor_pendingInvitation_returnsFalse() {
        Event e = createEvent(UUID.randomUUID());
        EventCoOrganizer inv = new EventCoOrganizer();
        inv.eventId = e.id;
        inv.userId = creatorId;
        inv.status = CoOrganizerStatus.PENDING;
        inv.persist();
        em.flush();
        assertFalse(service.isAcceptedFor(e.id, creatorId));
    }

    // ---- orphan invitation (L180 ternary `: null`, findByIdsAsDTO L226/227) ----

    @Test
    @TestTransaction
    void getMyInvitations_orphanInvitation_filteredOut() {
        // A PENDING invitation whose eventId references no Event row (the
        // parent was hard-deleted). findByIdsAsDTO returns an empty map
        // (events.isEmpty() L226-227), so the L180 mapping yields null and the
        // Objects::nonNull filter drops the orphan → empty result.
        EventCoOrganizer orphan = new EventCoOrganizer();
        orphan.eventId = 99999777L; // no matching Event
        orphan.userId = creatorId;
        orphan.status = CoOrganizerStatus.PENDING;
        orphan.persist();
        em.flush();

        List<CoOrganizerInvitationDTO> list = service.getMyInvitations("auth0|x", null, 0, 10);
        assertTrue(list.isEmpty(),
                "An invitation pointing at a missing event must be filtered out");
    }

    // ---- isCreator null-operand legs (L264) ----

    @Test
    @TestTransaction
    void remove_eventCreatorIdNull_throws403() {
        // isCreator L264: event.creatorId == null short-circuits to false → a
        // non-admin caller is rejected with 403, exercising the creatorId-null
        // operand leg.
        Event e = createEvent(null); // creatorId == null
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.remove(e.id, "auth0|x", inviteeId, false));
    }
}
