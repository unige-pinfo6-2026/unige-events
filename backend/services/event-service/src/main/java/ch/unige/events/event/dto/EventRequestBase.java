package ch.unige.events.event.dto;

import ch.unige.events.event.entity.EventCategory;
import ch.unige.events.event.entity.Faculty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code startDate} and {@code endDate} fields are deserialized as
 * {@link LocalDateTime} (no zone) and validated by {@code @Future}
 * against the JVM default timezone. Downstream {@code EventSearchService}
 * converts them to {@code Europe/Zurich} for time-range filtering.
 *
 * <p>The container TZ is therefore load-bearing: if it drifts from
 * {@code Europe/Zurich}, the {@code @Future} validation passes for
 * timestamps that are already past in the canonical zone. Helm pins
 * {@code TZ=Europe/Zurich} in every Deployment env (see
 * {@code k8s/chart/templates/<svc>-service/deployment.yaml} — DevOps
 * handoff item to enforce uniformly). If you bump the container TZ,
 * also update {@code EventSearchService.SEARCH_ZONE}.
 */
public abstract class EventRequestBase {

    @NotBlank
    @Size(max = 120)
    public String title;

    @Size(max = 2000)
    public String description;

    @NotBlank
    public String location;

    @NotNull
    @Future
    public LocalDateTime startDate;

    @NotNull
    public LocalDateTime endDate;

    @NotNull
    public EventCategory category;

    public Faculty faculty;

    public String bannerUrl;

    @Positive
    public Integer capacity;

    public Boolean allDay;

    @URL
    @Size(max = 500)
    public String websiteUrl;

    @Email
    @Size(max = 255)
    public String contactEmail;

    public LocalDateTime registrationDeadline;

    @Size(max = 20)
    public List<@NotBlank @Size(max = 16) String> tags = new ArrayList<>();
}
