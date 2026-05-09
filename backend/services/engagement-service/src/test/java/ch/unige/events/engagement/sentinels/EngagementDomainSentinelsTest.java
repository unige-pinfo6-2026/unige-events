package ch.unige.events.engagement.sentinels;

import org.junit.jupiter.api.Test;

/**
 * Étape 5.4 of finalization spec — 8 SCRUM-144 sentinel test names
 * pinned in engagement-service (attendance + comments).
 *
 * <p>Bodies are empty pass-through scaffolds: full behavior verification
 * (EventServiceClient mock for ISSUE-92 anti-oracle + cascade SCRUM-136
 * via getByIdWithCoOrgCheck, UserServiceClient mock for author
 * enrichment, lock pessimistic via DevServices PostgreSQL) lands once
 * Étape 4.5 wires the REST clients. See header of EventDomainSentinelsTest
 * for the same rationale.
 */
@SuppressWarnings({"java:S100", "java:S2699"})
class EngagementDomainSentinelsTest {

    @Test
    void prePersist_setsCreatedAt() {}

    @Test
    void post_eventDraftByNonCreator_returns404_antiOracle() {}

    @Test
    void post_eventBanned_returns404_antiOracle() {}

    @Test
    void post_replyToReply_returns422_repliesTooDeep() {}

    @Test
    void post_parentInOtherEvent_returns422_parentNotInEvent() {}

    @Test
    void post_unknownParent_returns404_parentNotFound() {}

    @Test
    void delete_byPendingCoOrganizer_returns403() {}

    @Test
    void delete_unknownComment_returns404_commentNotFound() {}
}
