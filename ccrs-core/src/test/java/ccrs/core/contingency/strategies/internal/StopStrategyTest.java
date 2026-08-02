package ccrs.core.contingency.strategies.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.ContingencyConfiguration;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.StopStrategyOptions;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class StopStrategyTest {

    private static final Situation REQUEST = Situation.builder()
        .trigger("runtime guidance requested")
        .failedAction("DELETE")
        .targetResource("https://example.test/item/42")
        .httpError(500, "Internal error")
        .build();

    @Test
    void exposesStableStrategyMetadata() {
        StopStrategy strategy = new StopStrategy();

        assertEquals("stop", strategy.getId());
        assertEquals("Stop (Last Resort)", strategy.getName());
        assertEquals(CcrsStrategy.Category.INTERNAL, strategy.getCategory());
        assertEquals(0, strategy.getEscalationLevel());
        assertEquals(
            "Advisory stop after degraded guidance and ungated reconsideration",
            strategy.getDescription());
        assertTrue(strategy.isEnabled());
    }

    @Test
    void consecutiveNoGuidanceInvocationsRequestTypedReconsideration() {
        StopStrategy strategy = new StopStrategy();
        TestContext context = new TestContext(List.of(noGuidance(), noGuidance()));

        assertApplicable(strategy, context);
        StrategyResult result = strategy.evaluate(REQUEST, context);

        assertNoHelp(result, StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED);
        assertTrue(result.asNoHelp().getExplanation().contains("trigger=no_suggestions"));
        assertTrue(result.asNoHelp().getExplanation().contains("consecutiveNoSuggestionCount=2/2"));
    }

    @Test
    void noGuidanceTriggerIsStrictlyConsecutive() {
        StopStrategy strategy = new StopStrategy();
        TestContext context = new TestContext(List.of(
            noGuidance(),
            weakGuidance(0.2),
            noGuidance(),
            noGuidance()));

        assertNotApplicable(strategy, context);
    }

    @Test
    void weakGuidanceTriggerIgnoresInterveningNoGuidance() {
        StopStrategy strategy = new StopStrategy();
        TestContext context = new TestContext(List.of(
            weakGuidance(0.49),
            noGuidance(),
            weakGuidance(0.20),
            weakGuidance(0.42)));

        assertApplicable(strategy, context);
        StrategyResult result = strategy.evaluate(REQUEST, context);

        assertNoHelp(result, StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED);
        assertTrue(result.asNoHelp().getExplanation().contains("trigger=low_confidence"));
        assertTrue(result.asNoHelp().getExplanation().contains("recentLowConfidenceCount=3/3"));
    }

    @Test
    void bothTriggersAreReportedWhenBothPatternsExist() {
        StopStrategy strategy = new StopStrategy();
        TestContext context = new TestContext(List.of(
            noGuidance(),
            noGuidance(),
            weakGuidance(0.1),
            weakGuidance(0.2),
            weakGuidance(0.3)));

        StrategyResult result = strategy.evaluate(REQUEST, context);

        assertNoHelp(result, StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED);
        assertTrue(result.asNoHelp().getExplanation().contains("trigger=both"));
    }

    @Test
    void confidenceAtThresholdEndsTheDegradationEpisode() {
        StopStrategy strategy = new StopStrategy();
        TestContext context = new TestContext(List.of(
            weakGuidance(0.2),
            weakGuidance(0.5),
            weakGuidance(0.1),
            weakGuidance(0.1)));

        assertNotApplicable(strategy, context);
    }

    @Test
    void reportedSuccessEndsEpisodeEvenForWeakGuidance() {
        CcrsTrace successfulWeakGuidance = weakGuidance(0.2);
        successfulWeakGuidance.reportOutcome(CcrsTrace.Outcome.SUCCESS, "worked");
        TestContext context = new TestContext(List.of(
            weakGuidance(0.2),
            successfulWeakGuidance,
            weakGuidance(0.2),
            weakGuidance(0.2)));

        assertNotApplicable(new StopStrategy(), context);
    }

    @Test
    void nonSuccessfulOutcomesDoNotOverrideWeakConfidence() {
        for (CcrsTrace.Outcome outcome : List.of(
                CcrsTrace.Outcome.PENDING,
                CcrsTrace.Outcome.UNKNOWN,
                CcrsTrace.Outcome.PARTIAL,
                CcrsTrace.Outcome.FAILED)) {
            CcrsTrace trace = weakGuidance(0.2);
            trace.reportOutcome(outcome, "test");
            assertApplicable(new StopStrategy(), new TestContext(List.of(
                trace,
                weakGuidance(0.2),
                weakGuidance(0.2))));
        }
    }

    @Test
    void stopSuggestionDoesNotCountAsHealthyGuidance() {
        TestContext context = new TestContext(List.of(
            stopSuggestion(),
            noGuidance()));

        assertApplicable(new StopStrategy(), context);
    }

    @Test
    void completedBypassProducesSoleAdvisoryStopSuggestion() {
        TestContext context = new TestContext(List.of(
            resetRequest(),
            noGuidance(),
            noGuidance()));

        StrategyResult.Suggestion suggestion = new StopStrategy().evaluate(REQUEST, context).asSuggestion();

        assertEquals(StopStrategy.ID, suggestion.getStrategyId());
        assertEquals("stop", suggestion.getActionType());
        assertEquals(null, suggestion.getActionTarget());
        assertEquals("no_suggestions", suggestion.getActionParam("trigger"));
        assertEquals(3, suggestion.<Integer>getActionParam("consecutiveNoSuggestionCount"));
        assertEquals(2, suggestion.<Integer>getActionParam("noSuggestionInvocationThreshold"));
        assertEquals(1, suggestion.<Integer>getActionParam("completedSelectionBypassCount"));
        assertEquals(1, suggestion.<Integer>getActionParam("selectionResetCountBeforeStop"));
        assertEquals(30, suggestion.<Integer>getActionParam("traceHistoryLookbackLimit"));
        assertEquals("DELETE", suggestion.getActionParam("failedAction"));
        assertEquals("500", suggestion.getActionParam("httpStatus"));
        assertEquals(1.0, suggestion.getConfidence(), 0.0);
        assertTrue(suggestion.getRationale().contains("consider stopping"));
        assertTrue(suggestion.getRationale().contains("agent retains the final decision"));
        assertTrue(suggestion.getRationale().contains("without learned ordering or gating"));
    }

    @Test
    void configuredMultipleBypassesDelayStop() {
        StopStrategy strategy = new StopStrategy(StopStrategyOptions.builder()
            .selectionResetCountBeforeStop(2)
            .build());

        StrategyResult afterOne = strategy.evaluate(REQUEST, new TestContext(List.of(
            resetRequest(),
            noGuidance(),
            noGuidance())));
        StrategyResult afterTwo = strategy.evaluate(REQUEST, new TestContext(List.of(
            resetRequest(),
            noGuidance(),
            resetRequest(),
            noGuidance())));

        assertNoHelp(afterOne, StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED);
        assertTrue(afterTwo.isSuggestion(), afterTwo::toDetailedReport);
    }

    @Test
    void ignoredStopCanBeSuggestedAgainWithinSameEpisode() {
        TestContext context = new TestContext(List.of(
            stopSuggestion(),
            resetRequest(),
            noGuidance(),
            noGuidance()));

        assertTrue(new StopStrategy().evaluate(REQUEST, context).isSuggestion());
    }

    @Test
    void optionBoundsSnapshotsAndCentralConfigurationArePreserved() {
        StopStrategyOptions options = StopStrategyOptions.builder()
            .noSuggestionInvocationThreshold(4)
            .lowConfidenceInvocationThreshold(2)
            .lowConfidenceThreshold(2.0)
            .selectionResetCountBeforeStop(5)
            .traceHistoryLookbackLimit(1)
            .build();
        StopStrategyOptions snapshot = options.toBuilder().build();
        ContingencyConfiguration configuration = ContingencyConfiguration.builder()
            .stop(options)
            .build();

        assertEquals(4, snapshot.getNoSuggestionInvocationThreshold());
        assertEquals(2, snapshot.getLowConfidenceInvocationThreshold());
        assertEquals(1.0, snapshot.getLowConfidenceThreshold(), 0.0);
        assertEquals(5, snapshot.getSelectionResetCountBeforeStop());
        assertEquals(6, snapshot.getTraceHistoryLookbackLimit());
        assertEquals(snapshot.getTraceHistoryLookbackLimit(),
            configuration.getStopStrategyOptions().getTraceHistoryLookbackLimit());

        StopStrategyOptions normalized = StopStrategyOptions.builder()
            .noSuggestionInvocationThreshold(0)
            .lowConfidenceInvocationThreshold(0)
            .lowConfidenceThreshold(Double.NaN)
            .selectionResetCountBeforeStop(0)
            .traceHistoryLookbackLimit(0)
            .build();
        assertEquals(1, normalized.getNoSuggestionInvocationThreshold());
        assertEquals(1, normalized.getLowConfidenceInvocationThreshold());
        assertEquals(0.5, normalized.getLowConfidenceThreshold(), 0.0);
        assertEquals(1, normalized.getSelectionResetCountBeforeStop());
        assertEquals(2, normalized.getTraceHistoryLookbackLimit());
    }

    @Test
    void requestsConfiguredTraceHistoryBoundAndNullOptionsUseDefaults() {
        TestContext context = new TestContext(List.of(noGuidance(), noGuidance()));
        StopStrategy strategy = new StopStrategy(StopStrategyOptions.builder()
            .traceHistoryLookbackLimit(7)
            .build());

        assertApplicable(strategy, context);
        assertEquals(7, context.requestedTraceLimit);
        assertNotApplicable(new StopStrategy(null), new TestContext(List.of()));
    }

    private static CcrsTrace noGuidance() {
        return CcrsTrace.builder(REQUEST).build();
    }

    private static CcrsTrace weakGuidance(double confidence) {
        StrategyResult suggestion = StrategyResult.suggest("retry", "retry")
            .confidence(confidence)
            .build();
        return CcrsTrace.builder(REQUEST)
            .addEvaluation("retry", 1, CcrsStrategy.Applicability.APPLICABLE, suggestion, 1L)
            .selectedResults(List.of(suggestion))
            .build();
    }

    private static CcrsTrace resetRequest() {
        StrategyResult result = StrategyResult.noHelp(
            StopStrategy.ID,
            StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED,
            "test reset");
        return CcrsTrace.builder(REQUEST)
            .addEvaluation(StopStrategy.ID, 0, CcrsStrategy.Applicability.APPLICABLE, result, 1L)
            .build();
    }

    private static CcrsTrace stopSuggestion() {
        StrategyResult suggestion = StrategyResult.suggest(StopStrategy.ID, "stop")
            .confidence(1.0)
            .build();
        return CcrsTrace.builder(REQUEST)
            .addEvaluation(StopStrategy.ID, 0, CcrsStrategy.Applicability.APPLICABLE, suggestion, 1L)
            .selectedResults(List.of(suggestion))
            .build();
    }

    private static void assertApplicable(StopStrategy strategy, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(REQUEST, context));
    }

    private static void assertNotApplicable(StopStrategy strategy, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.NOT_APPLICABLE, strategy.appliesTo(REQUEST, context));
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
