package ch.unige.events.report.service;

import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import jakarta.enterprise.event.Event;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests (no @QuarkusTest, no CDI proxy) — used to exercise
 * the {@code Event<EventBannedEvent>.fire(...)} branches that cannot be
 * verified through the CDI proxy in {@link ReportServiceTest}. We
 * instantiate the service directly and inject mocked collaborators via
 * reflection.
 *
 * <p>This complements {@link ReportServiceTest} by adding coverage for
 * the Kafka-event firing paths in {@code handle(...)}.
 */
@QuarkusTest
class ReportServiceUnitTest {

    private final UUID reporterId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    private static EventDTO event(Long id, EventStatus status, UUID creator) {
        return new EventDTO(id, "T-" + id, "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, creator,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    private static Report buildReport(Long id, Long eventId, UUID reporterId, ReportStatus status) {
        Report r = new Report();
        r.id = id;
        r.eventId = eventId;
        r.reporterId = reporterId;
        r.reason = ReportReason.SPAM;
        r.status = status;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    private static ReportService buildService(Event<EventBannedEvent> bannedEvent,
                                              EventServiceClient eventClient,
                                              UserServiceClient userClient,
                                              UUID callerUuid) throws Exception {
        ReportService svc = new ReportService();
        setField(svc, "bannedEvent", bannedEvent);
        setField(svc, "eventClient", eventClient);
        setField(svc, "userClient", userClient);
        CallerIdentity callerIdentity = mock(CallerIdentity.class);
        when(callerIdentity.requireUuid()).thenReturn(callerUuid);
        setField(svc, "callerIdentity", callerIdentity);
        return svc;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ReportService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_reviewed_firesEventBannedEvent_withModerationNote() throws Exception {
        Long eventId = 5001L;
        Report target = buildReport(50L, eventId, reporterId, ReportStatus.PENDING);
        Report sibling = buildReport(51L, eventId, UUID.randomUUID(), ReportStatus.PENDING);

        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);

        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(50L)).thenReturn(Optional.of(target));
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of(sibling));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        svc.handle(50L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, "confirmed spam"));

        ArgumentCaptor<EventBannedEvent> cap = ArgumentCaptor.forClass(EventBannedEvent.class);
        verify(bannedEvent, atLeastOnce()).fire(cap.capture());
        EventBannedEvent fired = cap.getValue();
        assertEquals(eventId.longValue(), fired.eventId());
        assertEquals("confirmed spam", fired.reason());
        assertEquals(adminId, fired.bannedBy());
        // Sibling cascaded.
        assertEquals(ReportStatus.REVIEWED, sibling.status);
        assertEquals(adminId, sibling.reviewedById);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_reviewed_nullModerationNote_usesDefaultReason() throws Exception {
        Long eventId = 5002L;
        Report target = buildReport(60L, eventId, reporterId, ReportStatus.PENDING);

        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);

        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(60L)).thenReturn(Optional.of(target));
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        svc.handle(60L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, null));

        ArgumentCaptor<EventBannedEvent> cap = ArgumentCaptor.forClass(EventBannedEvent.class);
        verify(bannedEvent).fire(cap.capture());
        assertEquals("admin-ban", cap.getValue().reason());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_dismissed_doesNotFireEventBannedEvent() throws Exception {
        Long eventId = 5003L;
        Report target = buildReport(70L, eventId, reporterId, ReportStatus.PENDING);

        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);

        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(70L)).thenReturn(Optional.of(target));

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        svc.handle(70L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, "false alarm"));

        verify(bannedEvent, never()).fire(any(EventBannedEvent.class));
    }

    /** Branches in the static helpers (badRequest / conflict / unprocessable). */
    @Test
    void staticHelpers_buildExpectedResponses() {
        WebApplicationException bad = ReportService.badRequest("e", "m");
        assertEquals(400, bad.getResponse().getStatus());
        WebApplicationException conf = ReportService.conflict("e", "m");
        assertEquals(409, conf.getResponse().getStatus());
        WebApplicationException unp = ReportService.unprocessable("e", "m");
        assertEquals(422, unp.getResponse().getStatus());
    }

    @Test
    void handle_reviewed_unknownReportThrowsBeforeFire() throws Exception {
        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(999L)).thenReturn(Optional.empty());

        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> svc.handle(999L, "auth0|admin",
                        new HandleReportRequest(ReportStatus.REVIEWED, null)));
        verify(bannedEvent, never()).fire(any(EventBannedEvent.class));
    }
}
