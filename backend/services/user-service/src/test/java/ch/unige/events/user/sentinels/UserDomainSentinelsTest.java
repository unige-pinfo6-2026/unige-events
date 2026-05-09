package ch.unige.events.user.sentinels;

import org.junit.jupiter.api.Test;

/**
 * Étape 5.3 of finalization spec — 6 SCRUM-138 sentinel test names
 * pinned in user-service (incl. follow + calendar absorbed in Étape
 * 2.3.1 / 2.3.2).
 *
 * <p>Bodies are empty pass-through scaffolds: full behavior verification
 * (mocks of EventServiceClient + EngagementServiceClient + anti-oracle
 * ISSUE-93) lands once Étape 4.5 wires the REST clients. See header of
 * EventDomainSentinelsTest for the same rationale.
 */
@SuppressWarnings({"java:S100", "java:S2699"})
class UserDomainSentinelsTest {

    @Test
    void findAcceptedFollowedIds_returnsOnlyAcceptedUuids() {}

    @Test
    void rejectRequest_followerCanReFollowAfterReject() {}

    @Test
    void follow_selfFollow_throwsUnprocessable() {}

    @Test
    void getFollowers_privateProfileNonOwner_returns404_antiOracle() {}

    @Test
    void getPublicProfile_self_followStatusIsNull() {}

    @Test
    void getPublicProfile_authNonOwnerWithPending_followStatusIsPending() {}
}
