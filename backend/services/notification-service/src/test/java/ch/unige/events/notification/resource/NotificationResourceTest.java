package ch.unige.events.notification.resource;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.IdProjection;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * REST-layer tests for {@link NotificationResource}. The
 * {@link UserServiceClient} is replaced by a Mockito mock that resolves
 * the test JWT subject to a fixed UUID. Data rows are persisted with
 * {@link QuarkusTransaction#requiringNew} so they commit before each
 * REST-Assured request (which runs in its own transaction).
 */
@QuarkusTest
class NotificationResourceTest {

    private static final UUID CALLER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER  = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @InjectMock @RestClient UserServiceClient userClient;

    @BeforeEach
    void wireMock() {
        when(userClient.getInternalByAuth0Id(anyString()))
                .thenReturn(new IdProjection(CALLER));
        truncate();
    }

    @AfterEach
    void cleanup() {
        truncate();
    }

    private void truncate() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    private Long persist(UUID userId, NotificationType type, boolean read) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.message = "msg-" + type;
        n.read = read;
        n.createdAt = LocalDateTime.now();
        QuarkusTransaction.requiringNew().run(n::persist);
        return n.id;
    }

    @Test
    void getNotifications_unauthenticated_returns401() {
        given()
            .when().get("/users/me/notifications")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|nrt-list")
    void getNotifications_returnsListAndUnreadHeader() {
        persist(CALLER, NotificationType.EVENT_UPDATED, false);
        persist(CALLER, NotificationType.NEW_ATTENDEE, false);
        persist(CALLER, NotificationType.EVENT_CANCELLED, true);

        given()
            .when().get("/users/me/notifications")
            .then()
            .statusCode(200)
            .header("X-Unread-Count", equalTo("2"))
            .body("$", hasSize(3));
    }

    @Test
    @TestSecurity(user = "auth0|nrt-empty")
    void getNotifications_empty_returnsZeroHeader() {
        given()
            .when().get("/users/me/notifications")
            .then()
            .statusCode(200)
            .header("X-Unread-Count", equalTo("0"))
            .body("$", hasSize(0));
    }

    @Test
    @TestSecurity(user = "auth0|nrt-scoped")
    void getNotifications_doesNotLeakOtherUserRows() {
        persist(CALLER, NotificationType.EVENT_UPDATED, false);
        persist(OTHER,  NotificationType.EVENT_UPDATED, false);

        given()
            .when().get("/users/me/notifications")
            .then()
            .statusCode(200)
            .header("X-Unread-Count", equalTo("1"))
            .body("$", hasSize(1))
            .body("[0].userId", equalTo(CALLER.toString()));
    }

    @Test
    @TestSecurity(user = "auth0|nrt-pagination")
    void getNotifications_paginatedRespectsSize() {
        for (int i = 0; i < 25; i++) {
            persist(CALLER, NotificationType.EVENT_UPDATED, false);
        }

        given()
            .queryParam("page", 1)
            .queryParam("size", 10)
            .when().get("/users/me/notifications")
            .then()
            .statusCode(200)
            .body("$", hasSize(10));
    }

    @Test
    @TestSecurity(user = "auth0|nrt-mark-happy")
    void patchMarkRead_happyPath_returns204AndPersists() {
        Long id = persist(CALLER, NotificationType.EVENT_UPDATED, false);

        given()
            .when().patch("/users/me/notifications/{id}/read", id)
            .then()
            .statusCode(204);

        Notification fresh = QuarkusTransaction.requiringNew().call(() -> Notification.findById(id));
        org.junit.jupiter.api.Assertions.assertTrue(fresh.read);
        org.junit.jupiter.api.Assertions.assertNotNull(fresh.readAt);
    }

    @Test
    @TestSecurity(user = "auth0|nrt-mark-idempotent")
    void patchMarkRead_alreadyRead_returns204() {
        Long id = persist(CALLER, NotificationType.EVENT_UPDATED, true);

        given()
            .when().patch("/users/me/notifications/{id}/read", id)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|nrt-mark-otheruser")
    void patchMarkRead_otherUserRow_returns404_antiOracle() {
        Long foreignId = persist(OTHER, NotificationType.EVENT_UPDATED, false);

        given()
            .when().patch("/users/me/notifications/{id}/read", foreignId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|nrt-mark-unknown")
    void patchMarkRead_unknownId_returns404() {
        given()
            .when().patch("/users/me/notifications/{id}/read", 99_999_999L)
            .then()
            .statusCode(404);
    }

    @Test
    void patchMarkRead_unauthenticated_returns401() {
        given()
            .when().patch("/users/me/notifications/1/read")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|nrt-readall-happy")
    void patchReadAll_returns200WithUpdatedCount() {
        persist(CALLER, NotificationType.EVENT_UPDATED, false);
        persist(CALLER, NotificationType.NEW_ATTENDEE, false);
        persist(CALLER, NotificationType.EVENT_CANCELLED, true);

        given()
            .when().patch("/users/me/notifications/read-all")
            .then()
            .statusCode(200)
            .body("updated", equalTo(2));
    }

    @Test
    @TestSecurity(user = "auth0|nrt-readall-noop")
    void patchReadAll_alreadyAllRead_returnsZero() {
        persist(CALLER, NotificationType.EVENT_UPDATED, true);
        persist(CALLER, NotificationType.NEW_ATTENDEE, true);

        given()
            .when().patch("/users/me/notifications/read-all")
            .then()
            .statusCode(200)
            .body("updated", equalTo(0))
            .body("updated", notNullValue());
    }

    @Test
    void patchReadAll_unauthenticated_returns401() {
        given()
            .when().patch("/users/me/notifications/read-all")
            .then()
            .statusCode(401);
    }
}
