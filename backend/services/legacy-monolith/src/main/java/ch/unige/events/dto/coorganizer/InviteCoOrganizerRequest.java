package ch.unige.events.dto.coorganizer;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteCoOrganizerRequest(@NotNull UUID userId) {}
