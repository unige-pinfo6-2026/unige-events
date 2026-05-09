package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(@NotNull AttendanceStatus status) {}
