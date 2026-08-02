package ccrs.core.contingency.strategies.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Interaction;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.BacktrackStrategyOptions;
import ccrs.core.opportunistic.OpportunisticResult;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class BacktrackStrategyTest {

    private static final String LINK = "https://example.test/link";

    @Test
    void exposesStableStrategyMetadata() {
        BacktrackStrategy strategy = new BacktrackStrategy();

        assertEquals("backtrack", strategy.getId());
        assertEquals("Backtrack", strategy.getName());
        assertEquals(CcrsStrategy.Category.INTERNAL, strategy.getCategory());
        assertEquals(2, strategy.getEscalationLevel());
        assertEquals("Backtrack (L2)", strategy.getDescription());
        assertTrue(strategy.isEnabled());
    }

    @Test
    void appliesOnlyToFailureOrStuckWithLocationAndHistory() {
        BacktrackStrategy strategy = new BacktrackStrategy();
        TestContext history = new TestContext(List.of());
        Situation failure = Situation.failure("failed")
            .currentResource("https://example.test/current")
            .build();
        Situation stuck = Situation.stuck("stuck")
            .currentResource("https://example.test/current")
            .build();
        Situation proactive = Situation.builder(Situation.Type.PROACTIVE)
            .currentResource("https://example.test/current")
            .build();

        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(failure, history));
        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(stuck, history));
        assertEquals(CcrsStrategy.Applicability.NOT_APPLICABLE, strategy.appliesTo(proactive, history));
        assertEquals(
            CcrsStrategy.Applicability.NOT_APPLICABLE,
            strategy.appliesTo(Situation.failure("failed").build(), history));
        assertEquals(
            CcrsStrategy.Applicability.NOT_APPLICABLE,
            strategy.appliesTo(failure, new TestContext(List.of(), null, false)));
        assertEquals(
            CcrsStrategy.Applicability.APPLICABLE,
            strategy.appliesTo(
                Situation.failure("failed").build(),
                new TestContext(List.of(), "https://example.test/current", true)));
    }

    @Test
    void returnsNoHelpWhenCurrentResourceIsUnknown() {
        StrategyResult result = new BacktrackStrategy().evaluate(
            Situation.failure("failed").build(),
            new TestContext(List.of()));

        assertNoHelp(result, StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT);
    }

    @Test
    void usesCurrentResourceFromContextWhenSituationOmitsIt() {
        String checkpoint = "https://example.test/checkpoint";
        String blocked = "https://example.test/blocked";
        String unexplored = "https://example.test/unexplored";
        List<Interaction> history = List.of(
            failure(blocked, 2),
            success(checkpoint, 1, blocked, unexplored));

        StrategyResult result = new BacktrackStrategy().evaluate(
            Situation.failure("failed").build(),
            new TestContext(history, blocked, true));

        assertTrue(result.isSuggestion(), result::toDetailedReport);
        assertEquals(checkpoint, result.asSuggestion().getActionTarget());
        assertEquals(blocked, result.asSuggestion().getActionParam("fromResource"));
    }

    @Test
    void returnsNoHelpWhenEveryCheckpointAlternativeIsExhausted() {
        String checkpoint = "https://example.test/checkpoint";
        String blocked = "https://example.test/blocked";
        List<Interaction> history = List.of(
            failure(blocked, 2),
            success(checkpoint, 1, blocked));

        StrategyResult result = new BacktrackStrategy().evaluate(
            Situation.failure("failed").currentResource(blocked).build(),
            new TestContext(history));

        assertNoHelp(result, StrategyResult.NoHelpReason.PRECONDITION_MISSING);
    }

    @Test
    void returnsNoHelpWhenValidCheckpointIsDisconnected() {
        String checkpoint = "https://example.test/checkpoint";
        String blocked = "https://example.test/blocked";
        List<Interaction> history = List.of(
            success(checkpoint, 1, "https://example.test/unexplored"));

        StrategyResult result = new BacktrackStrategy().evaluate(
            Situation.failure("failed").currentResource(blocked).build(),
            new TestContext(history));

        assertNoHelp(result, StrategyResult.NoHelpReason.PRECONDITION_MISSING);
        assertTrue(result.asNoHelp().getExplanation().contains("reachable"));
    }

    @Test
    void reachesCheckpointThroughExhaustedTransitResource() {
        String checkpoint = "https://example.test/checkpoint";
        String transit = "https://example.test/transit";
        String blocked = "https://example.test/blocked";
        String revisitSource = "https://example.test/revisit-source";
        String unexplored = "https://example.test/unexplored";

        List<Interaction> recentFirstHistory = List.of(
            success(transit, 5),
            success(revisitSource, 4, transit),
            failure(blocked, 3),
            success(transit, 2, blocked),
            success(checkpoint, 1, transit, unexplored));

        Situation situation = Situation.failure("blocked")
            .currentResource(blocked)
            .build();

        StrategyResult result = new BacktrackStrategy().evaluate(
            situation,
            new TestContext(recentFirstHistory));

        assertTrue(result.isSuggestion(), result::toDetailedReport);

        StrategyResult.Suggestion suggestion = result.asSuggestion();
        List<String> backtrackPath = suggestion.getActionParam("backtrackPath");

        assertEquals(checkpoint, suggestion.getActionTarget());
        assertEquals(List.of(transit, checkpoint), backtrackPath);
        assertEquals(2, suggestion.<Integer>getActionParam("backtrackDistance"));
        assertEquals("navigate", suggestion.getActionType());
        assertEquals("backtrack:" + checkpoint, suggestion.getStrategyId());
        assertEquals("backtrack_to_checkpoint", suggestion.getActionParam("reason"));
        assertEquals(blocked, suggestion.getActionParam("fromResource"));
        assertEquals("HISTORY", suggestion.getActionParam("checkpointSource"));
        assertEquals(1, suggestion.<Integer>getActionParam("unexploredCount"));
        assertEquals(2, suggestion.<Integer>getActionParam("temporalDistance"));
        assertTrue(suggestion.getConfidence() >= 0.1);
        assertTrue(suggestion.getConfidence() <= 0.9);

        Map<String, List<String>> alternatives = suggestion.getActionParam("alternativesByCheckpoint");
        Map<String, List<String>> exhausted = suggestion.getActionParam("exhaustedByCheckpoint");
        Map<String, Integer> distances = suggestion.getActionParam("backtrackDistances");
        assertEquals(List.of(unexplored), alternatives.get(checkpoint));
        assertEquals(List.of(), exhausted.get(checkpoint));
        assertEquals(2, distances.get(checkpoint));

        List<OpportunisticResult> guidance = suggestion.getOpportunisticGuidance();
        assertEquals(3, guidance.size());
        assertEquals("backtrack_step", guidance.get(0).type);
        assertEquals(transit, guidance.get(0).target);
        assertEquals(Optional.of("1"), guidance.get(0).getMetadata("step"));
        assertEquals("backtrack_step", guidance.get(1).type);
        assertEquals(checkpoint, guidance.get(1).target);
        assertEquals("unexplored_option", guidance.get(2).type);
        assertEquals(unexplored, guidance.get(2).target);
        assertEquals(suggestion.getConfidence(), guidance.get(2).utility);
    }

    @Test
    void prefersCloserCheckpointEvenWhenFartherCheckpointHasMoreOptions() {
        String farther = "https://example.test/farther";
        String transit = "https://example.test/transit";
        String closer = "https://example.test/closer";
        String blocked = "https://example.test/blocked";
        List<Interaction> history = List.of(
            failure(blocked, 4),
            success(closer, 3, blocked, "https://example.test/close-option"),
            success(transit, 2, closer),
            success(
                farther,
                1,
                transit,
                "https://example.test/far-option-1",
                "https://example.test/far-option-2",
                "https://example.test/far-option-3"));

        StrategyResult.Suggestion suggestion = evaluateSuggestion(blocked, history);
        Map<String, Integer> distances = suggestion.getActionParam("backtrackDistances");

        assertEquals(closer, suggestion.getActionTarget());
        assertEquals(1, distances.get(closer));
        assertEquals(3, distances.get(farther));
    }

    @Test
    void prefersMoreUnexploredOptionsWhenDistanceIsEqual() {
        String olderWithMoreOptions = "https://example.test/older-more-options";
        String newerWithOneOption = "https://example.test/newer-one-option";
        String blocked = "https://example.test/blocked";
        List<Interaction> history = List.of(
            failure(blocked, 3),
            success(
                newerWithOneOption,
                2,
                blocked,
                "https://example.test/newer-option"),
            success(
                olderWithMoreOptions,
                1,
                blocked,
                "https://example.test/older-option-1",
                "https://example.test/older-option-2"));

        StrategyResult.Suggestion suggestion = evaluateSuggestion(blocked, history);

        assertEquals(olderWithMoreOptions, suggestion.getActionTarget());
        assertEquals(2, suggestion.<Integer>getActionParam("unexploredCount"));
    }

    @Test
    void usesRecencyToBreakEqualDistanceAndOptionCount() {
        String older = "https://example.test/older";
        String newer = "https://example.test/newer";
        String blocked = "https://example.test/blocked";
        List<Interaction> history = List.of(
            failure(blocked, 3),
            success(newer, 2, blocked, "https://example.test/newer-option"),
            success(older, 1, blocked, "https://example.test/older-option"));

        StrategyResult.Suggestion suggestion = evaluateSuggestion(blocked, history);

        assertEquals(newer, suggestion.getActionTarget());
    }

    @Test
    void respectsConfiguredRecentInteractionLimit() {
        String checkpoint = "https://example.test/checkpoint";
        String blocked = "https://example.test/blocked";
        List<Interaction> history = List.of(
            failure(blocked, 2),
            success(checkpoint, 1, blocked, "https://example.test/unexplored"));
        TestContext context = new TestContext(history);
        BacktrackStrategy strategy = new BacktrackStrategy(
            BacktrackStrategyOptions.builder().maxRecentInteractions(1).build());

        StrategyResult result = strategy.evaluate(
            Situation.failure("failed").currentResource(blocked).build(),
            context);

        assertNoHelp(result, StrategyResult.NoHelpReason.PRECONDITION_MISSING);
        assertEquals(1, context.requestedInteractionLimit);
    }

    private static StrategyResult.Suggestion evaluateSuggestion(
            String currentResource,
            List<Interaction> history) {
        StrategyResult result = new BacktrackStrategy().evaluate(
            Situation.failure("failed").currentResource(currentResource).build(),
            new TestContext(history));
        assertTrue(result.isSuggestion(), result::toDetailedReport);
        return result.asSuggestion();
    }

    private static void assertNoHelp(
            StrategyResult result,
            StrategyResult.NoHelpReason expectedReason) {
        assertFalse(result.isSuggestion(), result::toDetailedReport);
        assertEquals(expectedReason, result.asNoHelp().getReason());
    }

    private static Interaction success(String uri, long timestamp, String... advertisedTargets) {
        List<RdfTriple> state = List.of(advertisedTargets).stream()
            .map(target -> new RdfTriple(uri, LINK, target))
            .toList();
        return interaction(uri, Interaction.Outcome.SUCCESS, state, timestamp);
    }

    private static Interaction failure(String uri, long timestamp) {
        return interaction(uri, Interaction.Outcome.SERVER_FAILURE, List.of(), timestamp);
    }

    private static Interaction interaction(
            String uri,
            Interaction.Outcome outcome,
            List<RdfTriple> state,
            long timestamp) {
        return new Interaction(
            "GET",
            uri,
            Map.of(),
            null,
            outcome,
            state,
            timestamp,
            timestamp,
            "test");
    }

    private static final class TestContext implements CcrsContext {
        private final List<Interaction> recentFirstHistory;
        private final String currentResource;
        private final boolean historyAvailable;
        private int requestedInteractionLimit = -1;

        private TestContext(List<Interaction> recentFirstHistory) {
            this(recentFirstHistory, null, true);
        }

        private TestContext(
                List<Interaction> recentFirstHistory,
                String currentResource,
                boolean historyAvailable) {
            this.recentFirstHistory = recentFirstHistory;
            this.currentResource = currentResource;
            this.historyAvailable = historyAvailable;
        }

        @Override
        public List<RdfTriple> query(String subject, String predicate, String object) {
            return Collections.emptyList();
        }

        @Override
        public boolean contains(RdfTriple triple) {
            return false;
        }

        @Override
        public List<Interaction> getRecentInteractions(int maxCount) {
            requestedInteractionLimit = maxCount;
            return recentFirstHistory.subList(0, Math.min(maxCount, recentFirstHistory.size()));
        }

        @Override
        public Optional<String> getCurrentResource() {
            return Optional.ofNullable(currentResource);
        }

        @Override
        public Optional<CcrsTrace> getLastCcrsInvocation() {
            return Optional.empty();
        }

        @Override
        public List<CcrsTrace> getCcrsHistory(int maxCount) {
            return Collections.emptyList();
        }

        @Override
        public void recordCcrsInvocation(CcrsTrace trace) {
        }

        @Override
        public boolean hasHistory() {
            return historyAvailable;
        }
    }
}
