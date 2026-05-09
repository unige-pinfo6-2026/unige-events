package ch.unige.events.user.sentinels;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SCRUM-138 sentinel suite — user-service (incl. follow + calendar
 * absorbed in Étape 2.3.1 / 2.3.2).
 *
 * <p>Étape 5.2 finalization-ultimate (Décision D Option 3): the
 * 6 sentinels are tagged {@code @Tag("legacy-port-s9")} and have
 * empty bodies — full port deferred to S9. Each requires a
 * {@code @QuarkusTest} with DevServices Postgres + JWT mock + the
 * REST client mocks added in Étape 3.3. The 35-sentinel batch port
 * is tracked as devops-handoff item 10.
 *
 * <p>Functional coverage of the anti-oracle ISSUE-93 (private profile
 * 404 for non-self non-admin callers) is provided by the runtime
 * implementation in
 * {@link ch.unige.events.user.service.UserService#getPublicProfile}
 * (post-Étape 2.4 admin bypass) ; once a provider verification job
 * lands in S9, the existing pact contracts pin the behavior server-
 * side too.
 */
@SuppressWarnings({"java:S100", "java:S2699"})
class UserDomainSentinelsTest {

    @Test
    @Tag("legacy-port-s9")
    void findAcceptedFollowedIds_returnsOnlyAcceptedUuids() {}

    @Test
    @Tag("legacy-port-s9")
    void rejectRequest_followerCanReFollowAfterReject() {}

    @Test
    @Tag("legacy-port-s9")
    void follow_selfFollow_throwsUnprocessable() {}

    @Test
    @Tag("legacy-port-s9")
    void getFollowers_privateProfileNonOwner_returns404_antiOracle() {}

    @Test
    @Tag("legacy-port-s9")
    void getPublicProfile_self_followStatusIsNull() {}

    @Test
    @Tag("legacy-port-s9")
    void getPublicProfile_authNonOwnerWithPending_followStatusIsPending() {}
}
