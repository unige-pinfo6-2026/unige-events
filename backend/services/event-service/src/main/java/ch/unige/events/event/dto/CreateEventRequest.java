package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import jakarta.validation.Valid;
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
 * Immutable payload accepted on {@code POST /events}.
 *
 * <p>The {@code startDate} and {@code endDate} fields are deserialized as
 * {@link LocalDateTime} (no zone) and validated by {@code @Future}
 * against the JVM default timezone. Downstream {@code EventSearchService}
 * converts them to {@code Europe/Zurich} for time-range filtering.
 *
 * <p>The container TZ is therefore load-bearing: if it drifts from
 * {@code Europe/Zurich}, the {@code @Future} validation passes for
 * timestamps that are already past in the canonical zone. Helm pins
 * {@code TZ=Europe/Zurich} in every Deployment env (see
 * {@code k8s/chart/templates/<svc>-service/deployment.yaml}). If you
 * bump the container TZ, also update {@code EventSearchService.SEARCH_ZONE}.
 */
public record CreateEventRequest(
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
        EventStatus status,
        @Valid RecurrenceRequest recurrence
) {
    public CreateEventRequest {
        // Defensive immutable copy. unmodifiableList(new ArrayList(...)) is used
        // instead of List.copyOf so a JSON payload like ["a", null] (which Jackson
        // does deserialize as a list containing a null) is preserved verbatim
        // for downstream EventService.normalizeTags to filter — List.copyOf
        // throws NPE on null elements.
        tags = (tags == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
    }
}
