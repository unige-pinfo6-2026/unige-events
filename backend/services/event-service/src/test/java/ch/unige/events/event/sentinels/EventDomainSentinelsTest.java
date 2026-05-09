package ch.unige.events.event.sentinels;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SCRUM-147 sentinel suite — event-service.
 *
 * <p>Étape 5.2 finalization-ultimate (Décision D Option 3): the
 * 17 sentinels are tagged {@code @Tag("legacy-port-s9")} and have
 * empty bodies — full port deferred to S9. Each requires a full
 * {@code @QuarkusTest} with @InjectMock REST clients + DevServices
 * Postgres + JWT mock to verify the anti-oracle ISSUE-92 cascade
 * server-side (the cascade itself is implemented post-Étape 3.4 in
 * {@code EventService.getById}). The 35-sentinel batch port is
 * tracked as devops-handoff item 10.
 *
 * <p>Functional coverage of the cascade is provided by:
 * <ul>
 *   <li>{@code EngagementEventIssue92PactTest} (consumer side, 4 cases)</li>
 *   <li>{@code EngagementEventScrum136PactTest} (cascade self-check)</li>
 * </ul>
 * — until the legacy port lands, these pacts pin the contract on the
 * provider side too (once a provider verification job is added in S9
 * — devops-handoff item 8).
 */
@SuppressWarnings({"java:S100", "java:S2699"})
class EventDomainSentinelsTest {

    @Test
    @Tag("legacy-port-s9")
    void from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId() {}

    @Test
    @Tag("legacy-port-s9")
    void from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule() {}

    @Test
    @Tag("legacy-port-s9")
    void createRecurring_weekly4Occurrences_persists1ParentAnd3Children() {}

    @Test
    @Tag("legacy-port-s9")
    void createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded() {}

    @Test
    @Tag("legacy-port-s9")
    void createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart() {}

    @Test
    @Tag("legacy-port-s9")
    void createRecurring_inheritsParentStatusPublished() {}

    @Test
    @Tag("legacy-port-s9")
    void getOccurrences_parentRecurring_returnsChildrenSortedAsc() {}

    @Test
    @Tag("legacy-port-s9")
    void getOccurrences_standaloneEvent_returns200EmptyList() {}

    @Test
    @Tag("legacy-port-s9")
    void getOccurrences_draftByNonCreator_returns404_antiOracle() {}

    @Test
    @Tag("legacy-port-s9")
    void update_parentTitle_doesNotPropagateToOccurrences() {}

    @Test
    @Tag("legacy-port-s9")
    void cancel_parentDoesNotCascadeToOccurrences() {}

    @Test
    @Tag("legacy-port-s9")
    void delete_parent_setsOccurrencesParentEventIdToNull() {}

    @Test
    @Tag("legacy-port-s9")
    void post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent() {}

    @Test
    @Tag("legacy-port-s9")
    void post_recurrenceMaxOccurrences53_returns400_beanValidation() {}

    @Test
    @Tag("legacy-port-s9")
    void getOccurrences_parentPublishedAnonymous_returns200() {}

    @Test
    @Tag("legacy-port-s9")
    void getOccurrences_sizeOver52_returns400() {}

    @Test
    @Tag("legacy-port-s9")
    void getOccurrences_draftByAnonymous_returns404_antiOracle() {}
}
