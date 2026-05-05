package ch.unige.events.exception.mapper;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.service.EventServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Locale;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * Pentest 2026-04-17 finding 4.20 — error responses must not leak the JAX-RS
 * handler/parameter names (validator) or the role/creator authorization model
 * (authz). End-to-end coverage exercising real HTTP responses.
 */
@QuarkusTest
class ErrorMaskingTest {

    @Inject
    EventServiceMock eventServiceMock;

    @BeforeEach
    void reset() {
        eventServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void validatorError_fieldIsLastSegmentOnly() {
        // CreateEventRequest with a blank title fails @NotBlank — before 9.C
        // the field came back as something like "create.request.title", leaking
        // the handler name (`create`) and DTO parameter (`request`).
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
                .statusCode(400)
                .body("error", equalTo("validation_error"))
                // Every field exposed to the client is a public DTO name —
                // bare identifier, no dotted path.
                .body("details.field", everyItem(matchesPattern("[A-Za-z][A-Za-z0-9]*")))
                .body("details.field", everyItem(not(containsString("."))))
                // And specifically the DTO field name `title` is exposed —
                // proving the segment chosen is the public-facing one.
                .body("details.field", hasItem("title"));
    }

    @Test
    @TestSecurity(user = "auth0|intruder")
    void authzError_responseDoesNotMentionCreatorOrAdmin() {
        // EventServiceMock raises a leaky ForbiddenException — the mapper must
        // mask it before it reaches the wire.
        var event = eventServiceMock.seedEvent("auth0|alice", "Original");

        var body = given()
                .contentType(ContentType.JSON)
                .body(buildUpdate())
                .when().put("/events/{id}", event.id)
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"))
                .body("message", equalTo("You are not allowed to perform this action."))
                .extract().body().asString();

        // Defence in depth — a free-text grep on the entire body so future
        // mapper regressions can't sneak detail back in.
        String lower = body.toLowerCase(Locale.ROOT);
        org.junit.jupiter.api.Assertions.assertFalse(
                lower.contains("creator"),
                "403 body must not mention 'creator' — found in: " + body);
        org.junit.jupiter.api.Assertions.assertFalse(
                lower.contains("admin"),
                "403 body must not mention 'admin' — found in: " + body);
        org.junit.jupiter.api.Assertions.assertFalse(
                lower.contains("co-organizer") || lower.contains("organizer"),
                "403 body must not mention organizer roles — found in: " + body);
    }

    private static ch.unige.events.dto.event.UpdateEventRequest buildUpdate() {
        ch.unige.events.dto.event.UpdateEventRequest req =
                new ch.unige.events.dto.event.UpdateEventRequest();
        req.title = "Updated";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        return req;
    }
}
