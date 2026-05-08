package ch.unige.events.dto.attendance;

import ch.unige.events.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(@NotNull AttendanceStatus status) {}
