package ch.unige.events.dto.stats;

public record EventStatsDTO(
        long attendingCount,
        long interestedCount,
        long viewCount
) {}
