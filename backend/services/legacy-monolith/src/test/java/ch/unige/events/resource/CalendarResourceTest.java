package ch.unige.events.resource;

import ch.unige.events.service.CalendarServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class CalendarResourceTest {

    @Inject
    CalendarServiceMock calendarServiceMock;

    @BeforeEach
    void setUp() {
        calendarServiceMock.reset();
    }

    @Test
    void getCalendarFeed_validToken_returns200WithIcsContent() {
        UUID token = UUID.randomUUID();

        given()
                .when().get("/calendar/{token}.ics", token)
                .then()
                .statusCode(200)
                .contentType("text/calendar")
                .body(containsString("BEGIN:VCALENDAR"));
    }

    @Test
    void getCalendarFeed_unknownToken_returns404() {
        CalendarServiceMock.forceNotFound = true;

        given()
                .when().get("/calendar/{token}.ics", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }
}
