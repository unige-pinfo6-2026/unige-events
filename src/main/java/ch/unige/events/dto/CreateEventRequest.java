package ch.unige.events.dto;

import ch.unige.events.entity.EventCategory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class CreateEventRequest {

    @NotBlank
    public String title;

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

    public String imageUrl;

    public Integer capacity;
}
