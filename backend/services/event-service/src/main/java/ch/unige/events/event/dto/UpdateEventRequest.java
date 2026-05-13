package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable payload accepted on {@code PUT /events/{id}}.
 *
 * <p>Carries the same TZ-sensitive {@code startDate}/{@code endDate} contract
 * as {@link CreateEventRequest} (cf. its JavaDoc for the rationale) — the
 * fields are duplicated here intentionally because a Java record cannot
 * inherit from a class.
 */
public record UpdateEventRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotBlank String location,
        @NotNull @Future LocalDateTime startDate,
        @NotNull LocalDateTime endDate,
        @NotNull EventCategory category,
        Faculty faculty,
        String bannerUrl,
        @Positive Integer capacity,
        Boolean allDay,
        @URL @Size(max = 500) String websiteUrl,
        @Email @Size(max = 255) String contactEmail,
        LocalDateTime registrationDeadline,
        @Size(max = 20) List<@NotBlank @Size(max = 16) String> tags,
        EventStatus status
) {
    public UpdateEventRequest {
        // Defensive immutable copy. unmodifiableList(new ArrayList(...)) is used
        // instead of List.copyOf so a JSON payload like ["a", null] (which Jackson
        // does deserialize as a list containing a null) is preserved verbatim
        // for downstream EventService.normalizeTags to filter — List.copyOf
        // throws NPE on null elements.
        tags = (tags == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
    }
}
