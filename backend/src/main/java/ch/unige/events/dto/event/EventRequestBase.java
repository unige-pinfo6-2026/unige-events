package ch.unige.events.dto.event;

import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.Faculty;
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
 * Champs communs à CreateEventRequest et UpdateEventRequest.
 * Les annotations de validation sont définies ici — Hibernate Validator
 * les propage aux sous-classes avec le nom de champ correct comme propertyPath.
 * Note : UpdateEventRequest hérite de ces contraintes mais elles ne sont déclenchées
 * que si le paramètre est annoté @Valid dans la Resource (décision SCRUM-83).
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
    public List<@NotBlank @Size(max = 64) String> tags = new ArrayList<>();
}
