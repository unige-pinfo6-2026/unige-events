package ch.unige.events.resource;

import ch.unige.events.service.FeaturedServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminEventResourceTest {

    @Inject
    FeaturedServiceMock featuredServiceMock;

    @BeforeEach
    void setUp() {
        featuredServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void featureEndpoint_returnsOkWithFeaturedTrue() {
        var event = featuredServiceMock.seedEvent("Test Event", false);

        given()
                .when().patch("/admin/events/" + event.id + "/feature")
                .then()
                .statusCode(200)
                .body("featured", is(true));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void unfeatureEndpoint_returnsOkWithFeaturedFalse() {
        var event = featuredServiceMock.seedEvent("Featured Event", true);

        given()
                .when().patch("/admin/events/" + event.id + "/unfeature")
                .then()
                .statusCode(200)
                .body("featured", is(false));
    }

    @Test
    void featureEndpoint_withoutAuth_returns401() {
        given()
                .when().patch("/admin/events/1/feature")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "user", roles = {"USER"})
    void featureEndpoint_withoutAdminRole_returns403() {
        given()
                .when().patch("/admin/events/1/feature")
                .then()
                .statusCode(403);
    }

    @Test
    void unfeatureEndpoint_withoutAuth_returns401() {
        given()
                .when().patch("/admin/events/1/unfeature")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "user", roles = {"USER"})
    void unfeatureEndpoint_withoutAdminRole_returns403() {
        given()
                .when().patch("/admin/events/1/unfeature")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void featureEndpoint_unknownEvent_returns404() {
        FeaturedServiceMock.forceNotFound = true;

        given()
                .when().patch("/admin/events/999999/feature")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void unfeatureEndpoint_unknownEvent_returns404() {
        FeaturedServiceMock.forceNotFound = true;

        given()
                .when().patch("/admin/events/999999/unfeature")
                .then()
                .statusCode(404);
    }
}
