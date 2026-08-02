package ccrs.core.contingency.strategies.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.StopStrategyOptions;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class StopStrategyTest {

    @Test
    void exposesStableStrategyMetadata() {
        StopStrategy strategy = new StopStrategy();

        assertEquals("stop", strategy.getId());
        assertEquals("Stop (Last Resort)", strategy.getName());
        assertEquals(CcrsStrategy.Category.INTERNAL, strategy.getCategory());
        assertEquals(0, strategy.getEscalationLevel());
        assertEquals(
            "Last resort - graceful goal abandonment when recovery is impossible",
            strategy.getDescription());
        assertTrue(strategy.isEnabled());
    }

    @Test
    void defaultPolicyRequiresTwoMatchingNonStopAttemptTraces() {
        Situation situation = Situation.failure("failed").build();
        StopStrategy strategy = new StopStrategy();

        assertNotApplicable(strategy, situation, new TestContext(List.of()));
        assertNotApplicable(strategy, situation, new TestContext(List.of(attempt(situation, "retry"))));
        assertApplicable(strategy, situation, new TestContext(List.of(
            attempt(situation, "retry"),
            attempt(situation, "backtrack"))));
    }

    @Test
    void countsOnlyMatchingSituationTypesAndExcludesStopEvaluations() {
        Situation failure = Situation.failure("failed").build();
        Situation stuck = Situation.stuck("blocked").build();
        TestContext context = new TestContext(List.of(
            attempt(failure, "retry"),
            attempt(stuck, "backtrack"),
            attempt(failure, StopStrategy.ID)));

        assertNotApplicable(new StopStrategy(), failure, context);
    }

    @Test
    void evaluationDefensivelyRejectsUnmetExhaustionThreshold() {
        Situation situation = Situation.failure("failed").build();

        StrategyResult result = new StopStrategy().evaluate(
            situation,
            new TestContext(List.of(attempt(situation, "retry"))));

        assertNoHelp(result, StrategyResult.NoHelpReason.NOT_APPLICABLE);
        assertEquals("Only 1 strategies attempted, threshold is 2", result.asNoHelp().getExplanation());
    }

    @Test
    void exhaustedSuggestionContainsCompleteFailureDiagnostics() {
        Situation situation = Situation.failure("request failed")
            .failedAction("DELETE")
            .targetResource("https://example.test/item/42")
            .httpError(500, "Internal error")
            .build();
        TestContext context = new TestContext(List.of(
            attempt(situation, "retry"),
            attempt(situation, "backtrack")));

        StrategyResult.Suggestion suggestion = new StopStrategy().evaluate(situation, context).asSuggestion();

        assertEquals(StopStrategy.ID, suggestion.getStrategyId());
        assertEquals("stop", suggestion.getActionType());
        assertEquals(null, suggestion.getActionTarget());
        assertEquals("exhausted", suggestion.getActionParam("reason"));
        assertEquals(2, suggestion.<Integer>getActionParam("attemptedCount"));
        assertEquals("FAILURE", suggestion.getActionParam("situationType"));
        assertEquals(
            "Failed action: DELETE on https://example.test/item/42. HTTP 500: Internal error",
            suggestion.getActionParam("finalError"));
        assertEquals(1.0, suggestion.getConfidence(), 0.0);
        assertTrue(suggestion.getRationale().contains("All 2 recovery strategies exhausted."));
    }

    @Test
    void immediateStopClassifiesKnownTerminalHttpStatuses() {
        StopStrategy strategy = immediateStop();

        assertEquals("resource_gone", reason(strategy, failureWithStatus(410)));
        assertEquals("access_denied", reason(strategy, failureWithStatus(401)));
        assertEquals("access_denied", reason(strategy, failureWithStatus(403)));
        assertEquals("unrecoverable", reason(strategy, failureWithStatus(422)));
    }

    @Test
    void immediateStopUsesTriggerOrUnknownErrorWhenDetailsAreAbsent() {
        StopStrategy strategy = immediateStop();
        StrategyResult.Suggestion triggered = strategy.evaluate(
            Situation.stuck("No available link").build(), null).asSuggestion();
        StrategyResult.Suggestion unknown = strategy.evaluate(
            Situation.builder(Situation.Type.UNCERTAINTY).build(), null).asSuggestion();

        assertEquals("Trigger: No available link", triggered.getActionParam("finalError"));
        assertEquals("Unknown error", unknown.getActionParam("finalError"));
        assertTrue(triggered.getRationale().startsWith("No recovery options available."));
    }

    @Test
    void respectsConfiguredTraceLookbackLimit() {
        Situation situation = Situation.failure("failed").build();
        TestContext context = new TestContext(List.of(
            attempt(situation, "retry"),
            attempt(situation, "backtrack")));
        StopStrategy strategy = new StopStrategy(StopStrategyOptions.builder()
            .exhaustionThreshold(2)
            .stopLookbackLimit(1)
            .build());

        assertNotApplicable(strategy, situation, context);
        assertEquals(1, context.requestedTraceLimit);
    }

    @Test
    void nullOptionsUseDefaultsAndInvalidBoundsAreNormalized() {
        StopStrategyOptions options = StopStrategyOptions.builder()
            .exhaustionThreshold(-1)
            .stopLookbackLimit(0)
            .build();

        assertEquals(0, options.getExhaustionThreshold());
        assertEquals(1, options.getStopLookbackLimit());
        assertNotApplicable(new StopStrategy(null), Situation.failure("failed").build(), null);
    }

    private static StopStrategy immediateStop() {
        return new StopStrategy(StopStrategyOptions.builder()
            .requireExhaustion(false)
            .build());
    }

    private static String reason(StopStrategy strategy, Situation situation) {
        return strategy.evaluate(situation, null).asSuggestion().getActionParam("reason");
    }

    private static Situation failureWithStatus(int status) {
        return Situation.failure("failed").httpError(status, "error").build();
    }

    private static CcrsTrace attempt(Situation situation, String strategyId) {
        return CcrsTrace.builder(situation)
            .addEvaluation(
                strategyId,
                1,
                CcrsStrategy.Applicability.APPLICABLE,
                StrategyResult.noHelp(strategyId, StrategyResult.NoHelpReason.PRECONDITION_MISSING, "test"),
                1L)
            .build();
    }

    private static void assertApplicable(StopStrategy strategy, Situation situation, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNotApplicable(StopStrategy strategy, Situation situation, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.NOT_APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNoHelp(StrategyResult result, StrategyResult.NoHelpReason reason) {
        assertFalse(result.isSuggestion(), result::toDetailedReport);
        assertEquals(reason, result.asNoHelp().getReason());
    }

    private static final class TestContext implements CcrsContext {
        private final List<CcrsTrace> traces;
        private int requestedTraceLimit = -1;

        private TestContext(List<CcrsTrace> traces) {
            this.traces = traces;
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
        public Optional<CcrsTrace> getLastCcrsInvocation() {
            return traces.stream().findFirst();
        }

        @Override
        public List<CcrsTrace> getCcrsHistory(int maxCount) {
            requestedTraceLimit = maxCount;
            return traces.subList(0, Math.min(maxCount, traces.size()));
        }

        @Override
        public void recordCcrsInvocation(CcrsTrace trace) {
        }
    }
}
