package ch.unige.events.dto;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.entity.EventCategory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CreateEventRequestTest {

    @Inject
    Validator validator;

    private CreateEventRequest validRequest() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Spring Conference";
        req.location = "Geneva";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        return req;
    }

    @Test
    void validRequest_noViolations() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void blankTitle_hasViolation() {
        CreateEventRequest req = validRequest();
        req.title = "";

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
    }

    @Test
    void blankLocation_hasViolation() {
        CreateEventRequest req = validRequest();
        req.location = "";

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "location".equals(v.getPropertyPath().toString())));
    }

    @Test
    void pastStartDate_hasViolation() {
        CreateEventRequest req = validRequest();
        req.startDate = LocalDateTime.now().minusDays(1);

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "startDate".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nullStartDate_hasViolation() {
        CreateEventRequest req = validRequest();
        req.startDate = null;

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "startDate".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nullEndDate_hasViolation() {
        CreateEventRequest req = validRequest();
        req.endDate = null;

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "endDate".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nullCategory_hasViolation() {
        CreateEventRequest req = validRequest();
        req.category = null;

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "category".equals(v.getPropertyPath().toString())));
    }

    @Test
    void negativeCapacity_hasViolation() {
        CreateEventRequest req = validRequest();
        req.capacity = -1;

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "capacity".equals(v.getPropertyPath().toString())));
    }

    @Test
    void zeroCapacity_hasViolation() {
        CreateEventRequest req = validRequest();
        req.capacity = 0;

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "capacity".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nullCapacity_noViolation() {
        CreateEventRequest req = validRequest();
        req.capacity = null;

        assertTrue(validator.validate(req).isEmpty());
    }

    // --- SCRUM-126 — validations sur les 4 nouveaux champs ---

    @Test
    void invalidWebsiteUrl_hasViolation() {
        CreateEventRequest req = validRequest();
        req.websiteUrl = "not-a-url";

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> "websiteUrl".equals(v.getPropertyPath().toString())));
    }

    @Test
    void validWebsiteUrl_noViolation() {
        CreateEventRequest req = validRequest();
        req.websiteUrl = "https://example.com/path?q=1";

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void invalidContactEmail_hasViolation() {
        CreateEventRequest req = validRequest();
        req.contactEmail = "foo@";

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> "contactEmail".equals(v.getPropertyPath().toString())));
    }

    @Test
    void validContactEmail_noViolation() {
        CreateEventRequest req = validRequest();
        req.contactEmail = "organizer@unige.ch";

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void tooManyTags_hasViolation() {
        CreateEventRequest req = validRequest();
        List<String> tags = new ArrayList<>();
        IntStream.range(0, 25).forEach(i -> tags.add("tag" + i));
        req.tags = tags;

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("tags")));
    }

    @Test
    void blankTagElement_hasViolation() {
        CreateEventRequest req = validRequest();
        req.tags = new ArrayList<>(List.of("valid", "  "));

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("tags")));
    }

    @Test
    void tooLongTagElement_hasViolation() {
        CreateEventRequest req = validRequest();
        req.tags = new ArrayList<>(List.of("a".repeat(17)));

        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("tags")));
    }

    @Test
    void allNewFieldsEmpty_noViolation() {
        CreateEventRequest req = validRequest();
        req.websiteUrl = null;
        req.contactEmail = null;
        req.registrationDeadline = null;
        req.tags = new ArrayList<>();

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void registrationDeadlineInPast_noViolation() {
        CreateEventRequest req = validRequest();
        req.registrationDeadline = LocalDateTime.now().minusDays(10);

        assertTrue(validator.validate(req).isEmpty());
    }
}
