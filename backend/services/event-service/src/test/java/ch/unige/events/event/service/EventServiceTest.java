package ch.unige.events.event.service;

import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.dto.CreateEventRequest;
import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.dto.RecurrenceRequest;
import ch.unige.events.event.dto.UpdateEventRequest;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.RecurrenceFrequency;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit-test coverage for {@link EventService}. Drives the
 * service against a real DevServices Postgres (drop-and-create) with
 * REST clients mocked.
 */
@QuarkusTest
@TestSecurity(user = "auth0|test-event-svc")
@SuppressWarnings({"java:S5961", "java:S100"})
class EventServiceTest {

    @Inject EventService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient UserServiceClient userClient;
    @InjectMock FileStorageService fileStorageService;

    private static final String BANNERS_FOLDER = "events/banners";

    private final UUID creatorId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void stub() {
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(2L, 1L));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any()))
                .thenReturn(Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private CreateEventRequest req(String title) {
        LocalDateTime start = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2);
        return new CreateEventRequest(
                title, "desc", "loc", start, start.plusHours(2),
                EventCategory.ACADEMIC, null, null,
                null, null, null, null, null,
                null, null, null);
    }

    private CreateEventRequest withStatus(CreateEventRequest r, EventStatus status) {
        return new CreateEventRequest(
                r.title(), r.description(), r.location(), r.startDate(), r.endDate(),
                r.category(), r.faculty(), r.bannerUrl(),
                r.capacity(), r.allDay(), r.websiteUrl(), r.contactEmail(), r.registrationDeadline(),
                r.tags(), status, r.recurrence());
    }

    private CreateEventRequest withTags(CreateEventRequest r, List<String> tags) {
        return new CreateEventRequest(
                r.title(), r.description(), r.location(), r.startDate(), r.endDate(),
                r.category(), r.faculty(), r.bannerUrl(),
                r.capacity(), r.allDay(), r.websiteUrl(), r.contactEmail(), r.registrationDeadline(),
                tags, r.status(), r.recurrence());
    }

    private CreateEventRequest withRecurrence(CreateEventRequest r, RecurrenceRequest recurrence) {
        return new CreateEventRequest(
                r.title(), r.description(), r.location(), r.startDate(), r.endDate(),
                r.category(), r.faculty(), r.bannerUrl(),
                r.capacity(), r.allDay(), r.websiteUrl(), r.contactEmail(), r.registrationDeadline(),
                r.tags(), r.status(), recurrence);
    }

    private UpdateEventRequest updateReq(String title, LocalDateTime start, LocalDateTime end) {
        return new UpdateEventRequest(
                title, "desc", "loc", start, end,
                EventCategory.ACADEMIC, null, null,
                null, null, null, null, null,
                null, null);
    }

    private UpdateEventRequest updateReqWith(String title, LocalDateTime start, LocalDateTime end,
                                             List<String> tags, EventStatus status) {
        return new UpdateEventRequest(
                title, "desc", "loc", start, end,
                EventCategory.ACADEMIC, null, null,
                null, null, null, null, null,
                tags, status);
    }

    private Event persistEvent(String title, EventStatus status, UUID creator) {
        Event e = new Event();
        e.title = title;
        e.description = "desc";
        e.location = "loc";
        e.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = creator;
        e.status = status;
        e.persist();
        return e;
    }

    // ---- create ----

    @Test
    @TestTransaction
    void create_minimalRequest_persistsDraftAndReturnsDTO() {
        EventDTO dto = service.create("auth0|x", req("My event"));
        assertNotNull(dto.id());
        assertEquals(EventStatus.DRAFT, dto.status());
        assertEquals(creatorId, dto.creatorId());
    }

    @Test
    @TestTransaction
    void create_explicitDraft_status() {
        CreateEventRequest r = withStatus(req("explicit"), EventStatus.DRAFT);
        EventDTO dto = service.create("auth0|x", r);
        assertEquals(EventStatus.DRAFT, dto.status());
    }

    @Test
    @TestTransaction
    void create_explicitPublished_status() {
        CreateEventRequest r = withStatus(req("pub"), EventStatus.PUBLISHED);
        EventDTO dto = service.create("auth0|x", r);
        assertEquals(EventStatus.PUBLISHED, dto.status());
    }

    @Test
    @TestTransaction
    void create_expiredStatus_rejected() {
        CreateEventRequest r = withStatus(req("bad"), EventStatus.EXPIRED);
        assertThrows(BadRequestException.class, () -> service.create("auth0|x", r));
    }

    @Test
    @TestTransaction
    void create_cancelledStatus_rejected() {
        CreateEventRequest r = withStatus(req("bad"), EventStatus.CANCELLED);
        assertThrows(BadRequestException.class, () -> service.create("auth0|x", r));
    }

    @Test
    @TestTransaction
    void create_bannedStatus_rejected() {
        CreateEventRequest r = withStatus(req("bad"), EventStatus.BANNED);
        assertThrows(BadRequestException.class, () -> service.create("auth0|x", r));
    }

    @Test
    @TestTransaction
    void create_anonymousCaller_throws404() {
        JwtTestContext.clear();
        CreateEventRequest r = req("noOne");
        assertThrows(NotFoundException.class, () -> service.create("auth0|anon", r));
    }

    @Test
    @TestTransaction
    void create_normalizesTags() {
        CreateEventRequest r = withTags(req("tagged"), Arrays.asList("Foo", "  bar ", "FOO", "", null));
        EventDTO dto = service.create("auth0|x", r);
        Event e = Event.findById(dto.id());
        assertEquals(List.of("foo", "bar"), e.tags);
    }

    // ---- createRecurring ----

    @Test
    @TestTransaction
    void createRecurring_biweekly_endDate_generatesOccurrences() {
        CreateEventRequest base = req("biweekly");
        CreateEventRequest r = withRecurrence(base, new RecurrenceRequest(
                RecurrenceFrequency.BIWEEKLY,
                base.startDate().toLocalDate().plusWeeks(8), null));
        EventDTO parent = service.create("auth0|x", r);
        em.flush();
        long children = Event.count("parentEventId = ?1", parent.id());
        assertTrue(children >= 1);
    }

    @Test
    @TestTransaction
    void createRecurring_monthly() {
        CreateEventRequest r = withRecurrence(req("monthly"),
                new RecurrenceRequest(RecurrenceFrequency.MONTHLY, null, 3));
        EventDTO parent = service.create("auth0|x", r);
        em.flush();
        assertEquals(2L, Event.count("parentEventId = ?1", parent.id()));
    }

    // ---- getAll ----

    @Test
    @TestTransaction
    void getAll_filtersByStatus() {
        Event d = persistEvent("d1", EventStatus.DRAFT, creatorId);
        Event p = persistEvent("p1", EventStatus.PUBLISHED, creatorId);
        em.flush();

        List<EventDTO> all = service.getAll(0, 100, null, null, null, null, null, null, null);
        // hides EXPIRED + BANNED — both new events visible (count >= 2 since
        // other in-class tests may leave behind data when run together).
        assertTrue(all.stream().anyMatch(e -> e.id().equals(d.id)));
        assertTrue(all.stream().anyMatch(e -> e.id().equals(p.id)));
    }

    @Test
    @TestTransaction
    void getAll_byCategoryAndOrganizer() {
        Event e = persistEvent("c1", EventStatus.PUBLISHED, creatorId);
        em.flush();

        List<EventDTO> filtered = service.getAll(0, 20, EventStatus.PUBLISHED,
                EventCategory.ACADEMIC, creatorId, null, null, null, null);
        assertFalse(filtered.isEmpty());
    }

    @Test
    @TestTransaction
    void getAll_facultyNoneFilter() {
        Event e = persistEvent("nf", EventStatus.PUBLISHED, creatorId);
        e.faculty = null;
        em.flush();
        List<EventDTO> filtered = service.getAll(0, 20, null, null, null, null, null, true, null);
        assertFalse(filtered.isEmpty());
    }

    @Test
    @TestTransaction
    void getAll_facultyFilter() {
        Event e = persistEvent("sci", EventStatus.PUBLISHED, creatorId);
        e.faculty = Faculty.SCIENCES;
        em.flush();
        List<EventDTO> filtered = service.getAll(0, 100, null, null, null, null, Faculty.SCIENCES, false, null);
        assertTrue(filtered.stream().anyMatch(d -> d.id().equals(e.id)));
    }

    @Test
    @TestTransaction
    void getAll_endDateFromFilter() {
        persistEvent("future", EventStatus.PUBLISHED, creatorId);
        em.flush();
        List<EventDTO> filtered = service.getAll(0, 20, null, null, null,
                LocalDateTime.of(2025, 1, 1, 12, 0), null, null, null);
        assertFalse(filtered.isEmpty());
    }

    @Test
    @TestTransaction
    void getAll_featuredFilter() {
        Event e = persistEvent("f", EventStatus.PUBLISHED, creatorId);
        e.featured = true;
        e.featuredAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        em.flush();

        List<EventDTO> filtered = service.getAll(0, 100, null, null, null, null, null, null, true);
        assertTrue(filtered.stream().anyMatch(d -> d.id().equals(e.id)));
    }

    // ---- getAll (followedIds overload — SCRUM-168) ----

    @Test
    @TestTransaction
    void getAll_followedIds_empty_shortCircuitsToEmptyList() {
        persistEvent("e1", EventStatus.PUBLISHED, creatorId);
        em.flush();

        List<EventDTO> result = service.getAll(0, 100, null, null, null, null, null, null, null, List.of());
        assertTrue(result.isEmpty(), "Empty followedIds must short-circuit to []");
    }

    @Test
    @TestTransaction
    void getAll_followedIds_nonEmpty_filtersToFollowedCreators() {
        Event mine  = persistEvent("mine",  EventStatus.PUBLISHED, creatorId);
        Event other = persistEvent("other", EventStatus.PUBLISHED, otherId);
        em.flush();

        List<EventDTO> result = service.getAll(0, 100, null, null, null, null, null, null, null, List.of(creatorId));
        assertTrue(result.stream().anyMatch(e -> e.id().equals(mine.id)),
                "Event from followed creator must be present");
        assertFalse(result.stream().anyMatch(e -> e.id().equals(other.id)),
                "Event from non-followed creator must be absent");
    }

    @Test
    @TestTransaction
    void getAll_followedIds_null_behaviorUnchanged() {
        Event e = persistEvent("nullcheck", EventStatus.PUBLISHED, creatorId);
        em.flush();

        List<EventDTO> result = service.getAll(0, 100, null, null, null, null, null, null, null, null);
        assertTrue(result.stream().anyMatch(d -> d.id().equals(e.id)),
                "null followedIds must not filter anything");
    }

    @Test
    @TestTransaction
    void getAll_followedIds_combinedWithStatusFilter() {
        Event pub  = persistEvent("pub",  EventStatus.PUBLISHED, creatorId);
        Event draf = persistEvent("draf", EventStatus.DRAFT,     creatorId);
        em.flush();

        List<EventDTO> result = service.getAll(0, 100, EventStatus.PUBLISHED, null, null, null, null, null, null, List.of(creatorId));
        assertTrue(result.stream().anyMatch(e -> e.id().equals(pub.id)),
                "PUBLISHED event from followed creator must appear");
        assertFalse(result.stream().anyMatch(e -> e.id().equals(draf.id)),
                "DRAFT event must be excluded by status filter");
    }

    // ---- getById ----

    @Test
    @TestTransaction
    void getById_publishedAnonymous_returnsDTO() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, otherId);
        em.flush();
        JwtTestContext.clear();

        EventDTO dto = service.getById(e.id, null, false);
        assertEquals(e.id, dto.id());
    }

    @Test
    @TestTransaction
    void getById_draftByCreator_returnsDTO() {
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        em.flush();
        EventDTO dto = service.getById(e.id, "auth0|x", false);
        assertEquals(e.id, dto.id());
    }

    @Test
    @TestTransaction
    void getById_draftByNonCreator_throws404() {
        Event e = persistEvent("d", EventStatus.DRAFT, otherId);
        em.flush();
        assertThrows(NotFoundException.class,
                () -> service.getById(e.id, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void getById_bannedEvent_throws404() {
        Event e = persistEvent("b", EventStatus.BANNED, creatorId);
        em.flush();
        assertThrows(NotFoundException.class,
                () -> service.getById(e.id, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void getById_admin_seesDraft() {
        Event e = persistEvent("d", EventStatus.DRAFT, otherId);
        em.flush();
        EventDTO dto = service.getById(e.id, "auth0|x", true);
        assertEquals(e.id, dto.id());
    }

    @Test
    @TestTransaction
    void getById_unknownId_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.getById(99999L, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void getById_checkCoOrgOf_setsField() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, otherId);
        em.flush();
        EventDTO dto = service.getById(e.id, "auth0|x", false, creatorId);
        assertNotNull(dto.coOrganizerOf());
        assertFalse(dto.coOrganizerOf());
    }

    @Test
    @TestTransaction
    void getById_checkCoOrgOf_acceptedReturnsTrue() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, otherId);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = creatorId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();

        EventDTO dto = service.getById(e.id, "auth0|x", false, creatorId);
        assertTrue(dto.coOrganizerOf());
    }

    // ---- findByIds ----

    @Test
    @TestTransaction
    void findByIds_emptyIds_returnsEmpty() {
        assertTrue(service.findByIds(List.of(), null).isEmpty());
        assertTrue(service.findByIds(null, null).isEmpty());
    }

    @Test
    @TestTransaction
    void findByIds_returnsMatching() {
        Event e1 = persistEvent("i1", EventStatus.PUBLISHED, creatorId);
        Event e2 = persistEvent("i2", EventStatus.DRAFT, creatorId);
        em.flush();

        List<EventDTO> all = service.findByIds(List.of(e1.id, e2.id), null);
        assertEquals(2, all.size());

        List<EventDTO> only = service.findByIds(List.of(e1.id, e2.id), EventStatus.PUBLISHED);
        assertEquals(1, only.size());
    }

    @Test
    @TestTransaction
    void findByIds_noMatchingRows_returnsEmpty() {
        // Non-empty ids that match nothing → the post-filter events.isEmpty()
        // fallback short-circuits before the bulk-summary call.
        List<EventDTO> all = service.findByIds(List.of(99999998L, 99999999L), null);
        assertTrue(all.isEmpty());
    }

    @Test
    @TestTransaction
    void findByIds_summariesNullClient_safe() {
        // P2: the bulk-summary client may return null (its @Fallback default).
        // findByIds must coalesce to an empty map and enrich with zeroed
        // counts rather than NPE.
        Event e1 = persistEvent("inull", EventStatus.PUBLISHED, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);

        List<EventDTO> all = service.findByIds(List.of(e1.id), null);
        assertEquals(1, all.size());
    }

    // ---- getOrganizerUuids ----

    @Test
    @TestTransaction
    void getOrganizerUuids_withCreatorAndAccepted() {
        Event e = persistEvent("o", EventStatus.PUBLISHED, creatorId);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = otherId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();

        List<UUID> ids = service.getOrganizerUuids(e.id);
        assertTrue(ids.contains(creatorId));
        assertTrue(ids.contains(otherId));
    }

    @Test
    @TestTransaction
    void getOrganizerUuids_bannedEvent_throws404() {
        Event e = persistEvent("b", EventStatus.BANNED, creatorId);
        em.flush();
        assertThrows(NotFoundException.class,
                () -> service.getOrganizerUuids(e.id));
    }

    @Test
    @TestTransaction
    void getOrganizerUuids_unknown_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.getOrganizerUuids(99999L));
    }

    // ---- update ----

    @Test
    @TestTransaction
    void update_byCreator_updatesFields() {
        Event e = persistEvent("o", EventStatus.DRAFT, creatorId);
        em.flush();
        UpdateEventRequest u = updateReqWith("renamed", e.startDate, e.endDate, List.of("Tag1"), null);
        EventDTO dto = service.update(e.id, "auth0|x", u);
        assertEquals("renamed", dto.title());
        assertEquals(List.of("tag1"), dto.tags());
    }

    @Test
    @TestTransaction
    void update_withValidStatus_appliesStatus() {
        // request.status() != null and is neither EXPIRED nor BANNED → the
        // status assignment branch runs (event.status = request.status()).
        Event e = persistEvent("o", EventStatus.DRAFT, creatorId);
        em.flush();
        UpdateEventRequest u = updateReqWith("repub", e.startDate, e.endDate, null, EventStatus.PUBLISHED);
        EventDTO dto = service.update(e.id, "auth0|x", u);
        assertEquals(EventStatus.PUBLISHED, dto.status());
    }

    @Test
    @TestTransaction
    void update_byNonCreator_throws403() {
        Event e = persistEvent("o", EventStatus.DRAFT, otherId);
        em.flush();
        UpdateEventRequest u = updateReq("x", e.startDate, e.endDate);
        assertThrows(ForbiddenException.class, () -> service.update(e.id, "auth0|x", u));
    }

    @Test
    @TestTransaction
    void update_cancelled_throws409() {
        Event e = persistEvent("c", EventStatus.CANCELLED, creatorId);
        em.flush();
        UpdateEventRequest u = updateReq("x", e.startDate, e.endDate);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.update(e.id, "auth0|x", u));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void update_banned_throws409() {
        Event e = persistEvent("b", EventStatus.BANNED, creatorId);
        em.flush();
        UpdateEventRequest u = updateReq("x", e.startDate, e.endDate);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.update(e.id, "auth0|x", u));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void update_setExpiredStatus_rejected() {
        Event e = persistEvent("o", EventStatus.DRAFT, creatorId);
        em.flush();
        UpdateEventRequest u = updateReqWith("x", e.startDate, e.endDate, null, EventStatus.EXPIRED);
        assertThrows(BadRequestException.class, () -> service.update(e.id, "auth0|x", u));
    }

    @Test
    @TestTransaction
    void update_setBannedStatus_rejected() {
        Event e = persistEvent("o", EventStatus.DRAFT, creatorId);
        em.flush();
        UpdateEventRequest u = updateReqWith("x", e.startDate, e.endDate, null, EventStatus.BANNED);
        assertThrows(BadRequestException.class, () -> service.update(e.id, "auth0|x", u));
    }

    @Test
    @TestTransaction
    void update_unknown_throws404() {
        UpdateEventRequest u = updateReq("x", LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1), LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2));
        assertThrows(NotFoundException.class,
                () -> service.update(99999L, "auth0|x", u));
    }

    // ---- delete ----

    @Test
    @TestTransaction
    void delete_byCreatorOnCancelled_succeeds() {
        Event e = persistEvent("c", EventStatus.CANCELLED, creatorId);
        em.flush();
        Long id = e.id;
        service.delete(id, "auth0|x");
        em.flush();
        em.clear();
        assertNull(Event.findById(id));
    }

    @Test
    @TestTransaction
    void delete_byNonCreator_throws403() {
        Event e = persistEvent("c", EventStatus.CANCELLED, otherId);
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.delete(e.id, "auth0|x"));
    }

    @Test
    @TestTransaction
    void delete_notCancelled_throws409() {
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.delete(e.id, "auth0|x"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void delete_unknown_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.delete(99999L, "auth0|x"));
    }

    // ---- cancel ----

    @Test
    @TestTransaction
    void cancel_published_setsCancelled() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, creatorId);
        em.flush();
        EventDTO dto = service.cancel(e.id, "auth0|x");
        assertEquals(EventStatus.CANCELLED, dto.status());
    }

    @Test
    @TestTransaction
    void cancel_alreadyCancelled_throws409() {
        Event e = persistEvent("c", EventStatus.CANCELLED, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.cancel(e.id, "auth0|x"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void cancel_banned_throws409() {
        Event e = persistEvent("b", EventStatus.BANNED, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.cancel(e.id, "auth0|x"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void cancel_expired_throws409() {
        Event e = persistEvent("ex", EventStatus.EXPIRED, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.cancel(e.id, "auth0|x"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void cancel_byNonCreator_throws403() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, otherId);
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.cancel(e.id, "auth0|x"));
    }

    // ---- restore ----

    @Test
    @TestTransaction
    void restore_cancelled_returnsToDraft() {
        Event e = persistEvent("c", EventStatus.CANCELLED, creatorId);
        em.flush();
        EventDTO dto = service.restore(e.id, "auth0|x");
        assertEquals(EventStatus.DRAFT, dto.status());
    }

    @Test
    @TestTransaction
    void restore_notCancelled_throws409() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.restore(e.id, "auth0|x"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void restore_byNonCreator_throws403() {
        Event e = persistEvent("c", EventStatus.CANCELLED, otherId);
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.restore(e.id, "auth0|x"));
    }

    // ---- publish ----

    @Test
    @TestTransaction
    void publish_validDraft_publishes() {
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        em.flush();
        EventDTO dto = service.publish(e.id, "auth0|x", false);
        assertEquals(EventStatus.PUBLISHED, dto.status());
    }

    @Test
    @TestTransaction
    void publish_alreadyPublished_throws409() {
        Event e = persistEvent("p", EventStatus.PUBLISHED, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.publish(e.id, "auth0|x", false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_byNonCreator_throws403() {
        Event e = persistEvent("d", EventStatus.DRAFT, otherId);
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.publish(e.id, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void publish_admin_canForce() {
        Event e = persistEvent("d", EventStatus.DRAFT, otherId);
        em.flush();
        EventDTO dto = service.publish(e.id, "auth0|x", true);
        assertEquals(EventStatus.PUBLISHED, dto.status());
    }

    @Test
    @TestTransaction
    void publish_invalidContent_throws422() {
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        e.title = null;
        e.location = null;
        e.category = null;
        e.startDate = null;
        e.endDate = null;
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.publish(e.id, "auth0|x", false));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_endBeforeStart_throws422() {
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        e.endDate = e.startDate.minusDays(1);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.publish(e.id, "auth0|x", false));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_unknown_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.publish(99999L, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void publish_acceptedCoOrganizer_succeeds() {
        Event e = persistEvent("d", EventStatus.DRAFT, otherId);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = creatorId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();

        EventDTO dto = service.publish(e.id, "auth0|x", false);
        assertEquals(EventStatus.PUBLISHED, dto.status());
    }

    // ---- uploadImage ----

    @Test
    @TestTransaction
    void uploadImage_byCreator_setsBannerUrl() {
        Event e = persistEvent("img", EventStatus.PUBLISHED, creatorId);
        em.flush();
        FileUpload upload = mock(FileUpload.class);
        when(fileStorageService.saveImage(eq(upload), eq(BANNERS_FOLDER),
                eq(FileStorageService.MAX_BANNER_BYTES), any()))
                .thenReturn("https://cdn/banner.png");

        EventDTO dto = service.uploadImage(e.id, "auth0|x", upload, false);

        assertEquals("https://cdn/banner.png", dto.bannerUrl());
        // Guard the storage call shape: correct folder + banner size cap.
        verify(fileStorageService).saveImage(eq(upload), eq(BANNERS_FOLDER),
                eq(FileStorageService.MAX_BANNER_BYTES), any());
    }

    @Test
    @TestTransaction
    void uploadImage_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.uploadImage(99999L, "auth0|x", null, false));
    }

    @Test
    @TestTransaction
    void uploadImage_byNonCreator_throws403() {
        Event e = persistEvent("img", EventStatus.PUBLISHED, otherId);
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.uploadImage(e.id, "auth0|x", null, false));
    }

    @Test
    @TestTransaction
    void uploadImage_admin_canForce() {
        Event e = persistEvent("img", EventStatus.PUBLISHED, otherId);
        em.flush();
        FileUpload upload = mock(FileUpload.class);
        when(fileStorageService.saveImage(eq(upload), eq(BANNERS_FOLDER),
                eq(FileStorageService.MAX_BANNER_BYTES), any()))
                .thenReturn("https://cdn/admin.png");

        EventDTO dto = service.uploadImage(e.id, "auth0|x", upload, true);

        assertEquals("https://cdn/admin.png", dto.bannerUrl());
    }

    // ---- normalizeTags ----

    @Test
    void normalizeTags_filterAndDedupe() {
        List<String> out = EventService.normalizeTags(List.of("Foo", "  bar ", "FOO", " "));
        assertEquals(List.of("foo", "bar"), out);
    }

    @Test
    void normalizeTags_nullOrEmpty_returnsEmpty() {
        assertTrue(EventService.normalizeTags(null).isEmpty());
        assertTrue(EventService.normalizeTags(List.of()).isEmpty());
    }

    // ---- buildRecurrenceRule ----

    @Test
    void buildRecurrenceRule_basicWeekly() {
        String rule = EventService.buildRecurrenceRule(
                new RecurrenceRequest(RecurrenceFrequency.WEEKLY, null, 4));
        assertEquals("FREQ=WEEKLY;COUNT=4", rule);
    }

    @Test
    void buildRecurrenceRule_withUntilDate() {
        java.time.LocalDate end = java.time.LocalDate.of(2099, 6, 1);
        String rule = EventService.buildRecurrenceRule(
                new RecurrenceRequest(RecurrenceFrequency.WEEKLY, end, null));
        assertEquals("FREQ=WEEKLY;UNTIL=20990601", rule);
    }

    @Test
    void buildRecurrenceRule_withBothEndAndCount() {
        java.time.LocalDate end = java.time.LocalDate.of(2099, 6, 1);
        String rule = EventService.buildRecurrenceRule(
                new RecurrenceRequest(RecurrenceFrequency.MONTHLY, end, 5));
        assertEquals("FREQ=MONTHLY;UNTIL=20990601;COUNT=5", rule);
    }

    // ---- getOccurrences additional cases ----

    @Test
    @TestTransaction
    void getOccurrences_admin_seesDraftOccurrences() {
        Event parent = persistEvent("p", EventStatus.PUBLISHED, otherId);
        Event child = new Event();
        child.title = "child";
        child.description = "d";
        child.location = "l";
        child.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(10);
        child.endDate = child.startDate.plusHours(2);
        child.category = EventCategory.ACADEMIC;
        child.creatorId = otherId;
        child.status = EventStatus.DRAFT;
        child.parentEventId = parent.id;
        child.persist();
        em.flush();

        List<EventDTO> occ = service.getOccurrences(parent.id, "auth0|x", true, 0, 20);
        assertEquals(1, occ.size());
    }

    @Test
    @TestTransaction
    void getOccurrences_bannedOccurrence_filteredOut() {
        Event parent = persistEvent("p", EventStatus.PUBLISHED, creatorId);
        Event child = new Event();
        child.title = "child";
        child.description = "d";
        child.location = "l";
        child.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(10);
        child.endDate = child.startDate.plusHours(2);
        child.category = EventCategory.ACADEMIC;
        child.creatorId = creatorId;
        child.status = EventStatus.BANNED;
        child.parentEventId = parent.id;
        child.persist();
        em.flush();

        List<EventDTO> occ = service.getOccurrences(parent.id, "auth0|x", false, 0, 20);
        assertTrue(occ.isEmpty());
    }

    // ---- summary==null degradation ternaries (engagement @Fallback null) ----
    // EventService L319/320 (getById), L445/446 (update), L539/540 (cancel),
    // L563/564 (restore), L705/706 (publish), L725/726 (uploadImage). Stub the
    // single-event summary client to return null; attending/waitlisted must
    // coalesce to 0 rather than NPE.

    @Test
    @TestTransaction
    void getById_summaryNullClient_zeroCounts() {
        Event e = persistEvent("snull", EventStatus.PUBLISHED, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);

        EventDTO dto = service.getById(e.id, "auth0|x", false);
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void update_summaryNullClient_zeroCounts() {
        Event e = persistEvent("snull", EventStatus.DRAFT, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);

        UpdateEventRequest u = updateReq("renamed", e.startDate, e.endDate);
        EventDTO dto = service.update(e.id, "auth0|x", u);
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void cancel_summaryNullClient_zeroCounts() {
        Event e = persistEvent("snull", EventStatus.PUBLISHED, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);

        EventDTO dto = service.cancel(e.id, "auth0|x");
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void restore_summaryNullClient_zeroCounts() {
        Event e = persistEvent("snull", EventStatus.CANCELLED, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);

        EventDTO dto = service.restore(e.id, "auth0|x");
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void publish_summaryNullClient_zeroCounts() {
        Event e = persistEvent("snull", EventStatus.DRAFT, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);

        EventDTO dto = service.publish(e.id, "auth0|x", false);
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void uploadImage_summaryNullClient_zeroCounts() {
        Event e = persistEvent("snull", EventStatus.PUBLISHED, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);
        FileUpload upload = mock(FileUpload.class);
        when(fileStorageService.saveImage(eq(upload), eq(BANNERS_FOLDER),
                eq(FileStorageService.MAX_BANNER_BYTES), any()))
                .thenReturn("https://cdn/banner.png");

        EventDTO dto = service.uploadImage(e.id, "auth0|x", upload, false);
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void getAll_bulkSummariesNullClient_zeroCounts() {
        // toEventDTOs L758/759 — bulk summary client returns null → coalesce
        // to an empty map and enrich with zeroed counts (no NPE).
        Event e = persistEvent("bulknull", EventStatus.PUBLISHED, creatorId);
        em.flush();
        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);

        List<EventDTO> all = service.getAll(0, 100, EventStatus.PUBLISHED, null, creatorId, null, null, null, null);
        assertTrue(all.stream().anyMatch(d -> d.id().equals(e.id)));
    }

    // ---- DTO tags==null arm (dto/EventDTO L151) ----
    // Event.tags defaults to an empty ArrayList; explicitly nulling it before
    // build() exercises the `tags != null ? ... : List.of()` false arm. The
    // DTO test class is plain JUnit (not @QuarkusTest), so the arm must be
    // driven through a @QuarkusTest service path to be counted by jacoco.

    @Test
    @TestTransaction
    void getById_nullTagsEvent_yieldsEmptyTagList() {
        Event e = persistEvent("notags", EventStatus.PUBLISHED, creatorId);
        e.tags = null;
        em.flush();

        EventDTO dto = service.getById(e.id, "auth0|x", false);
        assertEquals(List.of(), dto.tags());
    }

    // ---- ordinary-input branches ----

    // NOTE: EventService L163 (`request.startDate() != null` FALSE leg of the
    // recurrence end-before-start guard) is left as an accepted-ceiling branch:
    // a null startDate is rejected by @NotNull at the resource layer, and a
    // null reaching createRecurring NPEs in RecurrenceGenerator.generate before
    // any honest assertion. Not coverable without an artificial reflection test.

    @Test
    @TestTransaction
    void getOccurrences_nonAdminDraftOccurrence_evaluatesCreatorCascade() {
        // isOccurrenceVisible L210: a DRAFT occurrence with isAdmin=false →
        // falls through to `isAdmin || isCreatorOrAcceptedCoOrganizer(...)`.
        // Caller is the creator → occurrence stays visible.
        Event parent = persistEvent("p", EventStatus.PUBLISHED, creatorId);
        Event child = new Event();
        child.title = "child";
        child.description = "d";
        child.location = "l";
        child.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(10);
        child.endDate = child.startDate.plusHours(2);
        child.category = EventCategory.ACADEMIC;
        child.creatorId = creatorId;
        child.status = EventStatus.DRAFT;
        child.parentEventId = parent.id;
        child.persist();
        em.flush();

        List<EventDTO> occ = service.getOccurrences(parent.id, "auth0|x", false, 0, 20);
        assertEquals(1, occ.size());
    }

    @Test
    @TestTransaction
    void createRecurring_parentTagsNull_occurrenceGetsEmptyTags() {
        // persistOccurrence L265: parent.tags == null → occurrence.tags is a
        // fresh empty ArrayList rather than a copy. Drive a recurring create
        // whose parent carries null tags.
        CreateEventRequest base = withTags(req("recNullTags"), null);
        CreateEventRequest r = withRecurrence(base,
                new RecurrenceRequest(RecurrenceFrequency.WEEKLY, null, 2));
        EventDTO parent = service.create("auth0|x", r);
        em.flush();
        long children = Event.count("parentEventId = ?1", parent.id());
        assertTrue(children >= 1);
        Event firstChild = Event.<Event>find("parentEventId = ?1", parent.id()).firstResult();
        assertTrue(firstChild.tags.isEmpty());
    }

    @Test
    @TestTransaction
    void getOrganizerUuids_creatorIdNull_skipsCreatorAdd() {
        // L396: event.creatorId == null → the `ids.add(event.creatorId)` is
        // skipped. The accepted co-organizer is still returned.
        Event e = persistEvent("noCreator", EventStatus.PUBLISHED, null);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = otherId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();

        List<UUID> ids = service.getOrganizerUuids(e.id);
        assertTrue(ids.contains(otherId));
        assertFalse(ids.contains(null));
    }

    @Test
    @TestTransaction
    void duplicate_sourceTitleNull_usesBaseCopyPrefix() {
        // L626: source.title == null → baseTitleRaw = "Copie de " (no NPE),
        // and L661 source.tags handling. Set tags=null too to hit that arm.
        Event src = persistEvent("willBeNull", EventStatus.DRAFT, creatorId);
        src.title = null;
        src.tags = null;
        em.flush();

        EventDTO clone = service.duplicate(src.id, "auth0|x", false);
        assertEquals("Copie de ", clone.title());
        assertEquals(EventStatus.DRAFT, clone.status());
        assertEquals(List.of(), clone.tags());
    }

    @Test
    @TestTransaction
    void publish_cancelledEvent_throws409() {
        // L688-689: status != DRAFT and != PUBLISHED (CANCELLED) → conflict
        // with the "current status is …" message.
        Event e = persistEvent("c", EventStatus.CANCELLED, creatorId);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.publish(e.id, "auth0|x", false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_blankFieldsAndPastStart_throws422() {
        // collectPublishValidationErrors L732/735/741/746: blank (non-null)
        // title + blank location + a non-null past startDate + endDate not
        // after start → multiple validation errors, surfaced as 422.
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        e.title = "   ";
        e.location = "   ";
        e.startDate = LocalDateTime.of(2000, 1, 1, 0, 0).minusDays(2);
        e.endDate = e.startDate.minusHours(1);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.publish(e.id, "auth0|x", false));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void delete_eventCreatorIdNull_throws403() {
        // isCreator L797: event.creatorId == null short-circuits to false →
        // a non-null caller is rejected with 403.
        Event e = persistEvent("noCreator", EventStatus.CANCELLED, null);
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.delete(e.id, "auth0|x"));
    }

    @Test
    void isCreatorOrAcceptedCoOrganizer_nullOperands_returnFalse() {
        // L811 public null-safe guard — both null-operand legs return false.
        // No persistence needed: the guard short-circuits before any DB call.
        Event transientEvent = new Event();
        transientEvent.creatorId = creatorId;
        assertFalse(service.isCreatorOrAcceptedCoOrganizer(null, creatorId));
        assertFalse(service.isCreatorOrAcceptedCoOrganizer(transientEvent, null));
    }

    @Test
    @TestTransaction
    void getOccurrences_nonAdminNonCreatorDraftOccurrence_filteredOut() {
        // isOccurrenceVisible L210: a DRAFT occurrence with isAdmin=false where
        // the caller is neither creator nor accepted co-organizer →
        // `isAdmin || isCreatorOrAcceptedCoOrganizer(...)` is false/false → the
        // occurrence is filtered out. Parent is PUBLISHED (so getById passes)
        // but the DRAFT child belongs to `otherId`, while the caller is
        // `creatorId`.
        Event parent = persistEvent("p", EventStatus.PUBLISHED, otherId);
        Event child = new Event();
        child.title = "child";
        child.description = "d";
        child.location = "l";
        child.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(10);
        child.endDate = child.startDate.plusHours(2);
        child.category = EventCategory.ACADEMIC;
        child.creatorId = otherId;
        child.status = EventStatus.DRAFT;
        child.parentEventId = parent.id;
        child.persist();
        em.flush();

        List<EventDTO> occ = service.getOccurrences(parent.id, "auth0|x", false, 0, 20);
        assertTrue(occ.isEmpty());
    }

    @Test
    @TestTransaction
    void createRecurring_parentTagsNonNull_occurrenceCopiesTags() {
        // persistOccurrence L265: parent.tags != null → occurrence.tags is a
        // fresh copy of the parent's normalized tags (the non-null arm).
        CreateEventRequest base = withTags(req("recWithTags"), List.of("alpha", "beta"));
        CreateEventRequest r = withRecurrence(base,
                new RecurrenceRequest(RecurrenceFrequency.WEEKLY, null, 2));
        EventDTO parent = service.create("auth0|x", r);
        em.flush();
        Event firstChild = Event.<Event>find("parentEventId = ?1", parent.id()).firstResult();
        assertEquals(List.of("alpha", "beta"), firstChild.tags);
    }

    @Test
    @TestTransaction
    void publish_endDatePresentStartNull_throws422() {
        // collectPublishValidationErrors L744/746: endDate != null (L744 false)
        // and startDate == null → the `event.startDate != null` operand at L746
        // short-circuits to false (its false leg) so the end-after-start error
        // is NOT added; the missing start error (L741) still surfaces a 422.
        Event e = persistEvent("d", EventStatus.DRAFT, creatorId);
        e.startDate = null;
        e.endDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(3);
        em.flush();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.publish(e.id, "auth0|x", false));
        assertEquals(422, ex.getResponse().getStatus());
    }
}
