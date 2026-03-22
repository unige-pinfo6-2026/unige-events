package ch.unige.events.resource;

import ch.unige.events.dto.CreateEventRequest;
import ch.unige.events.entity.EventCategory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class EventResourceTest {

    @Test
    void getAll_returnsOk() {
        given()
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", instanceOf(java.util.List.class));
    }

    @Test
    void create_withValidRequest_returns201() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Conférence Test";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("title", is("Conférence Test"))
                .body("creatorId", nullValue());
    }

    @Test
    void create_withBlankTitle_returns400() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(400);
    }

    @Test
    void create_withNullCategory_returns400() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Test";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = null;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(400);
    }
}
