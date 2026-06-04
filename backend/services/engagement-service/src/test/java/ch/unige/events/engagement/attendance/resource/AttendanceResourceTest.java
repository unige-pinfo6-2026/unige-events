package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;

import org.hamcrest.Matchers;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * REST-layer integration tests for {@link AttendanceResource} via
 * REST-Assured. The {@code @TestSecurity} annotation drives the
 * {@code SecurityIdentity}; {@link JwtTestContext} stages the test caller
 * UUID used by TestCallerIdentity.
 */
@QuarkusTest
@TestSecurity(user = "auth0|test-attendance-resource")
class AttendanceResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
    }

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    private static EventDTO event(Long id, EventStatus status, Integer capacity, UUID creatorId) {
        return new EventDTO(id, "T", "d", "l",
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1),
                null, null, null, creatorId,
                status, capacity, false, false, null,
                0L, capacity != null ? capacity.longValue() : null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PanacheQuery queryWithFirst(Optional<? extends Attendance> first) {
        PanacheQuery q = mock(PanacheQuery.class);
        org.mockito.Mockito.doReturn(first).when(q).firstResultOptional();
        return q;
    }

    @Test
    void post_attend_publishedEvent_returns200() {
        when(eventClient.getById(1001L)).thenReturn(event(1001L, EventStatus.PUBLISHED, 10, userId));

        PanacheMock.mock(Attendance.class);
        @SuppressWarnings("rawtypes")
        PanacheQuery q = queryWithFirst(Optional.empty());
        when(Attendance.find(anyString(), any(Object[].class))).thenReturn(q);
        when(Attendance.count(anyString(), any(Object[].class))).thenReturn(0L);

        given()
            .contentType("application/json")
            .body("{\"status\":\"ATTENDING\"}")
            .when().post("/events/1001/attend")
            .then().statusCode(200);
    }

    @Test
    void post_attend_unknownEvent_returns404() {
        when(eventClient.getById(1002L)).thenReturn(null);
        given()
            .contentType("application/json")
            .body("{\"status\":\"ATTENDING\"}")
            .when().post("/events/1002/attend")
            .then().statusCode(404);
    }

    @Test
    void post_attend_invalidStatus_returns400() {
        given()
            .contentType("application/json")
            .body("{\"status\":\"WAITLISTED\"}")
            .when().post("/events/1003/attend")
            .then().statusCode(400);
    }

    @Test
    void delete_attend_unknown_returns404() {
        PanacheMock.mock(Attendance.class);
        @SuppressWarnings("rawtypes")
        PanacheQuery q = queryWithFirst(Optional.empty());
        when(Attendance.find(anyString(), any(Object[].class))).thenReturn(q);

        given()
            .when().delete("/events/1004/attend")
            .then().statusCode(404);
    }

    @Test
    void delete_attend_existing_returns204() {
        // Happy DELETE path → the resource returns 204 No Content. A WAITLISTED
        // row keeps the service off the promotion query branch; the row is spied
        // so the bound delete() is a no-op on the transient test instance.
        Attendance raw = new Attendance();
        raw.id = 4100L;
        raw.userId = userId;
        raw.eventId = 1010L;
        raw.status = AttendanceStatus.WAITLISTED;
        raw.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        Attendance attendance = spy(raw);
        doNothing().when(attendance).delete();

        PanacheMock.mock(Attendance.class);
        @SuppressWarnings("rawtypes")
        PanacheQuery q = queryWithFirst(Optional.of(attendance));
        when(Attendance.find(anyString(), any(Object[].class))).thenReturn(q);
        when(eventClient.getById(1010L)).thenReturn(event(1010L, EventStatus.PUBLISHED, 5, userId));

        given()
            .when().delete("/events/1010/attend")
            .then().statusCode(204);
    }

    @Test
    void get_attendees_byCreator_returns200() {
        EventDTO ev = new EventDTO(1005L, "T", "d", "l",
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1),
                null, null, null, userId,
                EventStatus.PUBLISHED, 5, false, false, null,
                0L, 5L, 0L, 0L, 0L, null, null, null,
                List.of(), LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, true);
        when(eventClient.getByIdWithCoOrgCheck(1005L, userId)).thenReturn(ev);

        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(1005L, 0, 20)).thenReturn(List.of());

        given()
            .when().get("/events/1005/attendees")
            .then().statusCode(200);
    }

    @Test
    void get_attendees_unknownEvent_returns404() {
        when(eventClient.getByIdWithCoOrgCheck(any(Long.class), any(UUID.class))).thenReturn(null);
        given()
            .when().get("/events/1006/attendees")
            .then().statusCode(404);
    }

    /**
     * SCRUM-S7 — non-organizer view privacy contract:
     * <ul>
     *   <li>Public-profile row → real {@code displayName} + non-null {@code userId}.</li>
     *   <li>Private-profile row → {@code displayName=null}, {@code avatarUrl=null},
     *       {@code userId=null}.</li>
     * </ul>
     * Replaces the previous {@code get_attendees_byNonOrganizer_returns403} test.
     */
    @Test
    void get_attendees_byNonOrganizer_anonymizesPrivateRowsOnly() {
        UUID creatorId = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();
        UUID privateUserId = UUID.randomUUID();
        EventDTO ev = new EventDTO(1007L, "T", "d", "l",
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1),
                null, null, null, creatorId,
                EventStatus.PUBLISHED, 5, false, false, null,
                0L, 5L, 0L, 0L, 0L, null, null, null,
                List.of(), LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, false);
        when(eventClient.getByIdWithCoOrgCheck(1007L, userId)).thenReturn(ev);
        when(eventClient.getOrganizerUuids(1007L)).thenReturn(List.of(creatorId));
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of(
                new AttendeeProjection(publicUserId, "Public-User", "/a.png", true),
                new AttendeeProjection(privateUserId, "Private-User", "/b.png", false)
        ));

        Attendance pub = new Attendance();
        pub.id = 5000L;
        pub.userId = publicUserId;
        pub.eventId = 1007L;
        pub.status = AttendanceStatus.ATTENDING;
        pub.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        Attendance priv = new Attendance();
        priv.id = 5001L;
        priv.userId = privateUserId;
        priv.eventId = 1007L;
        priv.status = AttendanceStatus.ATTENDING;
        priv.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(1007L, 0, 20)).thenReturn(List.of(pub, priv));

        given()
            .when().get("/events/1007/attendees")
            .then().statusCode(200)
                .body("size()", Matchers.is(2))
                .body("find { it.id == 5000 }.displayName", Matchers.equalTo("Public-User"))
                .body("find { it.id == 5000 }.userId", Matchers.equalTo(publicUserId.toString()))
                .body("find { it.id == 5001 }.displayName", Matchers.nullValue())
                .body("find { it.id == 5001 }.avatarUrl", Matchers.nullValue())
                .body("find { it.id == 5001 }.userId", Matchers.nullValue());
    }
}
