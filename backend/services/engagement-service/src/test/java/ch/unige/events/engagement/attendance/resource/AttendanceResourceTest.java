package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REST-layer integration tests for {@link AttendanceResource} via
 * REST-Assured. The {@code @TestSecurity} annotation drives the
 * {@code SecurityIdentity}; the JWT {@code uuid} claim is staged via
 * {@link JwtTestContext} so {@code Auth0IdResolver.resolveUserUuid} reads
 * the configured user UUID.
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
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, creatorId,
                status, capacity, false, false, null,
                0L, capacity != null ? capacity.longValue() : null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
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
    void get_attendees_byCreator_returns200() {
        EventDTO ev = new EventDTO(1005L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, userId,
                EventStatus.PUBLISHED, 5, false, false, null,
                0L, 5L, 0L, 0L, 0L, null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
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
}
