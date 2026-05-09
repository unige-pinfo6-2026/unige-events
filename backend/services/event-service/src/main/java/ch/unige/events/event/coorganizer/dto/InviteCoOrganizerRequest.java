package ch.unige.events.event.coorganizer.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteCoOrganizerRequest(@NotNull UUID userId) {}
