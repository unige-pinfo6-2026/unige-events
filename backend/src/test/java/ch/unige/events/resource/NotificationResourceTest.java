package ch.unige.events.resource;

import ch.unige.events.entity.NotificationType;
import ch.unige.events.service.NotificationServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class NotificationResourceTest {

    @Inject
    NotificationServiceMock notificationServiceMock;

    @BeforeEach
    void setUp() {
        notificationServiceMock.reset();
    }

    // --- GET /notifications ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void getNotifications_authenticated_returnsList() {
        notificationServiceMock.seedNotification(NotificationType.EVENT_UPDATED, false);
        notificationServiceMock.seedNotification(NotificationType.EVENT_CANCELLED, true);

        given()
                .when().get("/notifications")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getNotifications_unreadFirst() {
        notificationServiceMock.seedNotification(NotificationType.EVENT_CANCELLED, true);
        notificationServiceMock.seedNotification(NotificationType.EVENT_UPDATED, false);

        given()
                .when().get("/notifications")
                .then()
                .statusCode(200)
                .body("[0].read", is(false))
                .body("[1].read", is(true));
    }

    @Test
    void getNotifications_unauthenticated_returns401() {
        given()
                .when().get("/notifications")
                .then()
                .statusCode(401);
    }

    // --- PUT /notifications/{id}/read ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void markRead_ownNotification_returns200AndRead() {
        var n = notificationServiceMock.seedNotification(NotificationType.EVENT_UPDATED, false);

        given()
                .when().put("/notifications/" + n.id + "/read")
                .then()
                .statusCode(200)
                .body("read", is(true))
                .body("id", equalTo(n.id.intValue()));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void markRead_unknownNotification_returns404() {
        notificationServiceMock.forceNotFound = true;

        given()
                .when().put("/notifications/99999/read")
                .then()
                .statusCode(404);
    }

    @Test
    void markRead_unauthenticated_returns401() {
        given()
                .when().put("/notifications/1/read")
                .then()
                .statusCode(401);
    }

    // --- PUT /notifications/read-all ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void markAllRead_authenticated_returns204() {
        notificationServiceMock.seedNotification(NotificationType.EVENT_UPDATED, false);
        notificationServiceMock.seedNotification(NotificationType.NEW_ATTENDEE, false);

        given()
                .when().put("/notifications/read-all")
                .then()
                .statusCode(204);
    }

    @Test
    void markAllRead_unauthenticated_returns401() {
        given()
                .when().put("/notifications/read-all")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void markAllRead_allNotificationsMarkedRead() {
        notificationServiceMock.seedNotification(NotificationType.EVENT_UPDATED, false);
        notificationServiceMock.seedNotification(NotificationType.EVENT_CANCELLED, false);

        given()
                .when().put("/notifications/read-all")
                .then()
                .statusCode(204);

        given()
                .when().get("/notifications")
                .then()
                .statusCode(200)
                .body("[0].read", is(true))
                .body("[1].read", is(true));
    }
}
