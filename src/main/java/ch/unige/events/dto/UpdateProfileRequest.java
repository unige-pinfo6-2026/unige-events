package ch.unige.events.dto;

public record UpdateProfileRequest(
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    String interests,
    String avatarUrl,
    Boolean isProfilePublic
) {}