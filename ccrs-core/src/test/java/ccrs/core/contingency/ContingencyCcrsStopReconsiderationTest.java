package ccrs.core.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.strategies.internal.StopStrategy;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.InMemoryCcrsTraceHistory;
import ccrs.core.rdf.RdfTriple;

class ContingencyCcrsStopReconsiderationTest {

    private static final Situation REQUEST = Situation.builder()
        .trigger("a condition outside the former categories")
        .build();

    @Test
    void completeNoGuidanceSequenceRequestsBypassThenReturnsSoleStop() {
        TestContext context = new TestContext();
        ContingencyCcrs ccrs = evaluator(new FixedResultStrategy("general", 1, noHelp("general")), true);

        assertTrue(ccrs.evaluate(REQUEST, context).isEmpty());
        assertTrue(ccrs.evaluate(REQUEST, context).isEmpty());
        assertTrue(ccrs.evaluate(REQUEST, context).isEmpty());

        CcrsTrace resetTrace = context.getLastCcrsInvocation().orElseThrow();
        assertTrue(resetTrace.didStrategyReturnNoHelp(
            StopStrategy.ID,
            StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED));

        List<StrategyResult> finalResults = ccrs.evaluate(REQUEST, context);

        assertEquals(1, finalResults.size());
        assertEquals(StopStrategy.ID, finalResults.get(0).getStrategyId());
        assertEquals("stop", finalResults.get(0).asSuggestion().getActionType());
        assertEquals(1.0, finalResults.get(0).asSuggestion().getConfidence(), 0.0);
    }

    @Test
    void typedResetRemainsRecordedWhenVerboseTracingIsDisabled() {
        TestContext context = new TestContext();
        ContingencyCcrs ccrs = evaluator(new FixedResultStrategy("general", 1, noHelp("general")), false);

        ccrs.evaluate(REQUEST, context);
        ccrs.evaluate(REQUEST, context);
        ccrs.evaluate(REQUEST, context);

        CcrsTrace resetTrace = context.getLastCcrsInvocation().orElseThrow();
        assertEquals(1, resetTrace.getEvaluations().size());
        assertTrue(resetTrace.didStrategyReturnNoHelp(
            StopStrategy.ID,
            StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED));

        List<StrategyResult> finalResults = ccrs.evaluate(REQUEST, context);
        assertEquals(StopStrategy.ID, finalResults.get(0).getStrategyId());
    }

    @Test
    void currentLowConfidenceSuggestionStillSkipsStop() {
        TestContext context = new TestContext();
        context.recordCcrsInvocation(traceWithSuggestion(0.2));
        context.recordCcrsInvocation(traceWithSuggestion(0.3));
        context.recordCcrsInvocation(traceWithSuggestion(0.4));
        ContingencyCcrs ccrs = evaluator(
            new FixedResultStrategy("general", 1, suggestion("general", 0.1)),
            true);

        List<StrategyResult> results = ccrs.evaluate(REQUEST, context);

        assertEquals(1, results.size());
        assertEquals("general", results.get(0).getStrategyId());
        assertFalse(context.getLastCcrsInvocation().orElseThrow().wasStrategyEvaluated(StopStrategy.ID));
    }

    @Test
    void unclassifiedRequestReachesGeneralStrategy() {
        TestContext context = new TestContext();
        ContingencyCcrs ccrs = evaluator(
            new FixedResultStrategy("general", 1, suggestion("general", 0.7)),
            true);

        List<StrategyResult> results = ccrs.evaluate(REQUEST, context);

        assertEquals("general", results.get(0).getStrategyId());
        assertEquals("a condition outside the former categories",
            context.getLastCcrsInvocation().orElseThrow().getSituation().getTrigger());
    }

    private static ContingencyCcrs evaluator(CcrsStrategy strategy, boolean traceEnabled) {
        StrategyRegistry registry = new StrategyRegistry();
        registry.register(strategy);
        registry.register(new StopStrategy());
        ContingencyConfiguration config = ContingencyConfiguration.builder()
            .learnedSelection(true)
            .trace(traceEnabled)
            .build();
        return new ContingencyCcrs(registry, config);
    }

    private static StrategyResult noHelp(String strategyId) {
        return StrategyResult.noHelp(
            strategyId,
            StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT,
            "no guidance");
    }

    private static StrategyResult suggestion(String strategyId, double confidence) {
        return StrategyResult.suggest(strategyId, "wait")
            .confidence(confidence)
            .build();
    }

    private static CcrsTrace traceWithSuggestion(double confidence) {
        StrategyResult result = suggestion("general", confidence);
        return CcrsTrace.builder(REQUEST)
            .addEvaluation("general", 1, CcrsStrategy.Applicability.APPLICABLE, result, 1L)
            .selectedResults(List.of(result))
            .build();
    }

    private static final class FixedResultStrategy implements CcrsStrategy {
        private final String id;
        private final int level;
        private final StrategyResult result;

        private FixedResultStrategy(String id, int level, StrategyResult result) {
            this.id = id;
            this.level = level;
            this.result = result;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return id; }
        @Override public Category getCategory() { return Category.INTERNAL; }
        @Override public int getEscalationLevel() { return level; }
        @Override public Applicability appliesTo(Situation situation, CcrsContext context) {
            return Applicability.APPLICABLE;
        }
        @Override public StrategyResult evaluate(Situation situation, CcrsContext context) { return result; }
    }

    private static final class TestContext implements CcrsContext {
        private final InMemoryCcrsTraceHistory history = new InMemoryCcrsTraceHistory(50);

        @Override public List<RdfTriple> query(String subject, String predicate, String object) {
            return List.of();
        }
        @Override public boolean contains(RdfTriple triple) { return false; }
        @Override public Optional<CcrsTrace> getLastCcrsInvocation() { return history.getLast(); }
        @Override public List<CcrsTrace> getCcrsHistory(int maxCount) { return history.getRecent(maxCount); }
        @Override public void recordCcrsInvocation(CcrsTrace trace) { history.record(trace); }
    }
}
