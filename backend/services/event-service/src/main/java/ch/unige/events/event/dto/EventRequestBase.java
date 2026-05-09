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
