package ch.unige.events.attendance.dto;

import ch.unige.events.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(@NotNull AttendanceStatus status) {}
