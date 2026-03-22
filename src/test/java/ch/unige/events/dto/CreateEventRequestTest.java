package ch.unige.events.dto;

import ch.unige.events.entity.EventCategory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

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
}
