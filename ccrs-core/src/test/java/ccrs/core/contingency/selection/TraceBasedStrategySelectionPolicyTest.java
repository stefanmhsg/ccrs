package ccrs.core.contingency.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.ContingencyCcrs;
import ccrs.core.contingency.ContingencyConfiguration;
import ccrs.core.contingency.StrategyRegistry;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class TraceBasedStrategySelectionPolicyTest {

    private static final double DELTA = 0.000_001;
    private static final Situation SITUATION = Situation.builder()
        .trigger("selection policy test")
        .build();

    @Test
    void policyBuildsModelFromTheProvidedTraceWindow() {
        ContingencyConfiguration config = configuration(2, 0.10, 0.80, 250)
            .learningHistoryLimit(7)
            .build();
        TraceBasedStrategySelectionModel model = model(
            List.of(trace("known", CcrsStrategy.Applicability.APPLICABLE,
                suggestion("known", 0.7), 100L)),
            config);

        assertEquals(1, model.traceCount());
        assertEquals(1, model.profileCount());
        assertEquals(2, model.minimumSamples());
        assertTrue(model.describeBuild().contains("1/7 requested traces"));
        assertTrue(model.describeOrder(List.of(strategy("known", 1), strategy("new", 1)))
            .contains("known(default, L1)"));
    }

    @Test
    void learnsOnlyFromApplicableEvaluationsWithResults() {
        StrategyResult suggested = suggestion("candidate", 0.8);
        List<CcrsTrace> traces = List.of(
            trace("candidate", CcrsStrategy.Applicability.APPLICABLE, suggested, 100L),
            trace("candidate", CcrsStrategy.Applicability.UNKNOWN, noHelp("candidate"), 300L),
            trace("candidate", CcrsStrategy.Applicability.NOT_APPLICABLE,
                noHelp("candidate"), 999L),
            trace("candidate", CcrsStrategy.Applicability.APPLICABLE, null, 999L));

        TraceBasedStrategySelectionModel.Profile profile = model(traces, configuration().build())
            .profileFor("candidate");

        double evaluationWeight = 1.0 + 0.85;
        assertEquals(2, profile.evaluationCount());
        assertEquals((100.0 + 300.0 * 0.85) / evaluationWeight,
            profile.averageEvaluationTimeMs(), DELTA);
        assertEquals(1.0 / evaluationWeight, profile.suggestionRate(), DELTA);
        assertEquals(0.8, profile.averageSuggestionConfidence(), DELTA);
        assertEquals((1.0 / evaluationWeight) * 0.8,
            profile.expectedConfidence(), DELTA);
    }

    @Test
    void weightsRecentConfidenceMoreStronglyThanOlderConfidence() {
        TraceBasedStrategySelectionModel.Profile recentHigh = model(List.of(
            traceWithSuggestion("candidate", 0.9, 100L),
            traceWithSuggestion("candidate", 0.1, 100L)), configuration().build())
            .profileFor("candidate");
        TraceBasedStrategySelectionModel.Profile recentLow = model(List.of(
            traceWithSuggestion("candidate", 0.1, 100L),
            traceWithSuggestion("candidate", 0.9, 100L)), configuration().build())
            .profileFor("candidate");

        assertEquals((0.9 + 0.1 * 0.85) / 1.85,
            recentHigh.averageSuggestionConfidence(), DELTA);
        assertEquals((0.1 + 0.9 * 0.85) / 1.85,
            recentLow.averageSuggestionConfidence(), DELTA);
        assertTrue(recentHigh.expectedConfidence() > recentLow.expectedConfidence());
    }

    @Test
    void mapsReportedOutcomesIntoLearnedConfidence() {
        assertEquals(0.6, profileWithOutcome(CcrsTrace.Outcome.UNKNOWN).learnedConfidence(), DELTA);
        assertEquals(0.6, profileWithOutcome(CcrsTrace.Outcome.PENDING).learnedConfidence(), DELTA);
        assertEquals(0.72, profileWithOutcome(CcrsTrace.Outcome.SUCCESS).learnedConfidence(), DELTA);
        assertEquals(0.57, profileWithOutcome(CcrsTrace.Outcome.PARTIAL).learnedConfidence(), DELTA);
        assertEquals(0.42, profileWithOutcome(CcrsTrace.Outcome.FAILED).learnedConfidence(), DELTA);
    }

    @Test
    void appliesOutcomeFeedbackOnlyToTheSelectedTopSuggestion() {
        StrategyResult lower = suggestion("lower", 0.4);
        StrategyResult top = suggestion("top", 0.8);
        CcrsTrace trace = CcrsTrace.builder(SITUATION)
            .addEvaluation("lower", 1, CcrsStrategy.Applicability.APPLICABLE, lower, 100L)
            .addEvaluation("top", 1, CcrsStrategy.Applicability.APPLICABLE, top, 100L)
            .selectedResults(List.of(top, lower))
            .build();
        trace.reportOutcome(CcrsTrace.Outcome.SUCCESS, "worked");

        TraceBasedStrategySelectionModel model = model(List.of(trace), configuration().build());

        assertEquals(0.4, model.profileFor("lower").learnedConfidence(), DELTA);
        assertEquals(0.86, model.profileFor("top").learnedConfidence(), DELTA);
    }

    @Test
    void ordersByEscalationLevelThenExpectedConfidenceAndKeepsStopLast() {
        CcrsStrategy weakAtLevelTwo = strategy("weak-l2", 2);
        CcrsStrategy levelOne = strategy("level-one", 1);
        CcrsStrategy stop = strategy("stop", 0);
        CcrsStrategy strongAtLevelTwo = strategy("strong-l2", 2);
        TraceBasedStrategySelectionModel model = model(List.of(
            traceWithSuggestion("weak-l2", 0.2, 100L),
            traceWithSuggestion("level-one", 0.1, 100L),
            traceWithSuggestion("stop", 1.0, 100L),
            traceWithSuggestion("strong-l2", 0.9, 100L)), configuration().build());

        List<String> ids = model.orderForEvaluation(List.of(
                weakAtLevelTwo, levelOne, stop, strongAtLevelTwo)).stream()
            .map(CcrsStrategy::getId)
            .toList();

        assertEquals(List.of("level-one", "strong-l2", "weak-l2", "stop"), ids);
    }

    @Test
    void usesEvaluationTimeOnlyAsAQualityTieBreaker() {
        CcrsStrategy slow = strategy("slow", 2);
        CcrsStrategy fast = strategy("fast", 2);
        TraceBasedStrategySelectionModel model = model(List.of(
            traceWithSuggestion("slow", 0.6, 500L),
            traceWithSuggestion("fast", 0.6, 50L)), configuration().build());

        List<String> ids = model.orderForEvaluation(List.of(slow, fast)).stream()
            .map(CcrsStrategy::getId)
            .toList();

        assertEquals(List.of("fast", "slow"), ids);
    }

    @Test
    void preservesDefaultOrderUntilBothStrategiesHaveEnoughSamples() {
        CcrsStrategy first = strategy("first", 2);
        CcrsStrategy second = strategy("second", 2);
        ContingencyConfiguration config = configuration(2, 0.10, 0.80, 250).build();
        TraceBasedStrategySelectionModel model = model(List.of(
            traceWithSuggestion("first", 0.1, 500L),
            traceWithSuggestion("second", 0.9, 50L)), config);

        assertEquals(List.of("first", "second"),
            model.orderForEvaluation(List.of(first, second)).stream()
                .map(CcrsStrategy::getId)
                .toList());
    }

    @Test
    void gateAllowsWorkWhenNoSuggestionOrLearnedProfileExists() {
        CcrsStrategy poor = strategy("poor", 2);
        TraceBasedStrategySelectionModel learnedModel = model(
            List.of(traceWithSuggestion("poor", 0.2, 500L)), configuration().build());
        StrategyGateDecision withoutSuggestion = learnedModel.evaluateGate(poor, List.of());

        CcrsStrategy unknown = strategy("unknown", 2);
        StrategyGateDecision withoutProfile = learnedModel.evaluateGate(
            unknown, List.of(suggestion("current", 0.8)));

        assertTrue(withoutSuggestion.shouldEvaluate());
        assertTrue(withoutSuggestion.reason().contains("no current recovery suggestion"));
        assertTrue(withoutProfile.shouldEvaluate());
        assertTrue(withoutProfile.reason().contains("no learned profile"));
        assertNull(withoutProfile.diagnostics());
    }

    @Test
    void gateAllowsWorkUntilTheMinimumSampleCountIsReached() {
        CcrsStrategy candidate = strategy("candidate", 2);
        TraceBasedStrategySelectionModel model = model(
            List.of(traceWithSuggestion("candidate", 0.1, 500L)),
            configuration(2, 0.10, 0.80, 250).build());

        StrategyGateDecision decision = model.evaluateGate(
            candidate, List.of(suggestion("current", 0.8)));

        assertTrue(decision.shouldEvaluate());
        assertTrue(decision.reason().contains("only 1/2 applicable samples"));
    }

    @Test
    void gateAllowsExpectedGainHighConfidenceAndCheapCandidates() {
        StrategyGateDecision expectedGain = model(
            List.of(traceWithSuggestion("gain", 0.75, 500L)),
            configuration(1, 0.10, 0.90, 50).build())
            .evaluateGate(strategy("gain", 2), List.of(suggestion("current", 0.5)));
        StrategyGateDecision highConfidence = model(
            List.of(traceWithSuggestion("high", 0.82, 500L)),
            configuration(1, 0.10, 0.80, 50).build())
            .evaluateGate(strategy("high", 2), List.of(suggestion("current", 0.8)));
        StrategyGateDecision cheap = model(
            List.of(traceWithSuggestion("cheap", 0.3, 100L)),
            configuration(1, 0.10, 0.80, 250).build())
            .evaluateGate(strategy("cheap", 2), List.of(suggestion("current", 0.5)));

        assertTrue(expectedGain.shouldEvaluate());
        assertTrue(expectedGain.reason().contains("expected gain"));
        assertTrue(highConfidence.shouldEvaluate());
        assertTrue(highConfidence.reason().contains("high-confidence floor"));
        assertTrue(cheap.shouldEvaluate());
        assertTrue(cheap.reason().contains("cheap threshold"));
    }

    @Test
    void gateSkipsAnExpensiveCandidateWithoutEnoughExpectedValue() {
        CcrsStrategy candidate = strategy("candidate", 2);
        TraceBasedStrategySelectionModel model = model(
            List.of(traceWithSuggestion("candidate", 0.3, 300L)),
            configuration(1, 0.10, 0.80, 250).build());
        List<StrategyResult> current = List.of(
            suggestion("lower", 0.2),
            noHelp("none"),
            suggestion("best", 0.5));

        StrategyGateDecision decision = model.evaluateGate(candidate, current);

        assertFalse(decision.shouldEvaluate());
        assertFalse(model.shouldEvaluate(candidate, current));
        assertEquals("candidate", decision.strategyId());
        assertEquals(0.5, decision.currentBestConfidence(), DELTA);
        assertTrue(decision.reason().contains("expected gain"));
        assertTrue(decision.reason().contains("exceeds cheap threshold"));
        assertTrue(decision.diagnostics().contains("expectedConfidence=0.300"));
    }

    @Test
    void realPolicyReordersAndPrunesStrategiesUsingTheConfiguredHistoryWindow() {
        List<String> evaluations = new ArrayList<>();
        StrategyRegistry registry = new StrategyRegistry();
        registry.register(new RecordingStrategy("weak", 2, 0.2, evaluations));
        registry.register(new RecordingStrategy("strong", 2, 0.9, evaluations));
        TrackingContext context = new TrackingContext();
        context.recordCcrsInvocation(traceWithSuggestion("weak", 0.2, 500L));
        context.recordCcrsInvocation(traceWithSuggestion("strong", 0.9, 500L));
        ContingencyConfiguration config = configuration(1, 0.10, 0.95, 250)
            .learningHistoryLimit(2)
            .build();
        ContingencyCcrs ccrs = new ContingencyCcrs(registry, config);

        List<StrategyResult> results = ccrs.evaluate(SITUATION, context);

        assertEquals(List.of("strong"), evaluations);
        assertEquals(1, results.size());
        assertEquals("strong", results.get(0).getStrategyId());
        assertEquals(2, context.lastRequestedHistoryLimit);
        assertFalse(context.getLastCcrsInvocation().orElseThrow().wasStrategyEvaluated("weak"));
    }

    private static TraceBasedStrategySelectionModel.Profile profileWithOutcome(
            CcrsTrace.Outcome outcome) {
        CcrsTrace trace = traceWithSuggestion("candidate", 0.6, 100L);
        trace.reportOutcome(outcome, "test");
        return model(List.of(trace), configuration().build()).profileFor("candidate");
    }

    private static TraceBasedStrategySelectionModel model(
            List<CcrsTrace> traces,
            ContingencyConfiguration config) {
        StrategySelectionPlan plan = new TraceBasedStrategySelectionPolicy().createPlan(
            new StrategySelectionRequest(
                SITUATION,
                new TrackingContext(),
                List.of(),
                config,
                traces));
        return assertInstanceOf(TraceBasedStrategySelectionModel.class, plan);
    }

    private static ContingencyConfiguration.Builder configuration() {
        return configuration(1, 0.10, 0.80, 250);
    }

    private static ContingencyConfiguration.Builder configuration(
            int minimumSamples,
            double minimumGain,
            double highConfidenceFloor,
            long cheapEvaluationTimeMs) {
        return ContingencyConfiguration.builder()
            .minimumLearningSamples(minimumSamples)
            .minimumExpectedConfidenceGain(minimumGain)
            .highConfidenceEvaluationFloor(highConfidenceFloor)
            .cheapEvaluationTimeMs(cheapEvaluationTimeMs);
    }

    private static CcrsTrace traceWithSuggestion(
            String strategyId,
            double confidence,
            long evaluationTimeMs) {
        return trace(
            strategyId,
            CcrsStrategy.Applicability.APPLICABLE,
            suggestion(strategyId, confidence),
            evaluationTimeMs);
    }

    private static CcrsTrace trace(
            String strategyId,
            CcrsStrategy.Applicability applicability,
            StrategyResult result,
            long evaluationTimeMs) {
        CcrsTrace.Builder builder = CcrsTrace.builder(SITUATION)
            .addEvaluation(strategyId, 2, applicability, result, evaluationTimeMs);
        if (result != null && result.isSuggestion()) {
            builder.selectedResults(List.of(result));
        }
        return builder.build();
    }

    private static StrategyResult suggestion(String strategyId, double confidence) {
        return StrategyResult.suggest(strategyId, "recover")
            .confidence(confidence)
            .rationale("test")
            .build();
    }

    private static StrategyResult noHelp(String strategyId) {
        return StrategyResult.noHelp(
            strategyId,
            StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT,
            "test");
    }

    private static CcrsStrategy strategy(String id, int level) {
        return new RecordingStrategy(id, level, 0.5, new ArrayList<>());
    }

    private static final class RecordingStrategy implements CcrsStrategy {
        private final String id;
        private final int level;
        private final double confidence;
        private final List<String> evaluations;

        private RecordingStrategy(
                String id,
                int level,
                double confidence,
                List<String> evaluations) {
            this.id = id;
            this.level = level;
            this.confidence = confidence;
            this.evaluations = evaluations;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return id; }
        @Override public Category getCategory() { return Category.INTERNAL; }
        @Override public int getEscalationLevel() { return level; }
        @Override public Applicability appliesTo(Situation situation, CcrsContext context) {
            return Applicability.APPLICABLE;
        }
        @Override public StrategyResult evaluate(Situation situation, CcrsContext context) {
            evaluations.add(id);
            return suggestion(id, confidence);
        }
    }

    private static final class TrackingContext implements CcrsContext {
        private final List<CcrsTrace> traces = new ArrayList<>();
        private int lastRequestedHistoryLimit = -1;

        @Override
        public List<RdfTriple> query(String subject, String predicate, String object) {
            return List.of();
        }

        @Override
        public boolean contains(RdfTriple triple) {
            return false;
        }

        @Override
        public Optional<CcrsTrace> getLastCcrsInvocation() {
            return traces.isEmpty() ? Optional.empty() : Optional.of(traces.get(0));
        }

        @Override
        public List<CcrsTrace> getCcrsHistory(int maxCount) {
            lastRequestedHistoryLimit = maxCount;
            return List.copyOf(traces.subList(0, Math.min(maxCount, traces.size())));
        }

        @Override
        public void recordCcrsInvocation(CcrsTrace trace) {
            traces.add(0, trace);
        }
    }
}
