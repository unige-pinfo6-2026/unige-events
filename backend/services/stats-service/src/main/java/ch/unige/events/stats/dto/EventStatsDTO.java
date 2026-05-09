package ch.unige.events.stats.dto;

public record EventStatsDTO(
        long attendingCount,
        long interestedCount,
        long viewCount
) {}
