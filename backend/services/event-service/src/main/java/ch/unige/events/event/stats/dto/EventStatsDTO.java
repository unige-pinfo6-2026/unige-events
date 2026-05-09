package ch.unige.events.event.stats.dto;

public record EventStatsDTO(
        long attendingCount,
        long interestedCount,
        long viewCount
) {}
