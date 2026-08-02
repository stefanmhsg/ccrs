package ccrs.core.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.selection.StrategyGateDecision;
import ccrs.core.contingency.selection.StrategySelectionPlan;
import ccrs.core.contingency.selection.StrategySelectionPolicy;
import ccrs.core.contingency.selection.StrategySelectionRequest;
import ccrs.core.contingency.strategies.internal.StopStrategy;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.InMemoryCcrsTraceHistory;
import ccrs.core.rdf.RdfTriple;

class ContingencyCcrsLearnedSelectionBypassTest {

    private static final Situation REQUEST = Situation.builder().trigger("test").build();

    @Test
    void resetBypassesLearnedOrderAndGateOnceThenLearningResumesWithRetainedHistory() {
        List<String> evaluations = new ArrayList<>();
        RecordingStrategy first = new RecordingStrategy("first", 1, evaluations);
        RecordingStrategy second = new RecordingStrategy("second", 1, evaluations);
        RecordingStrategy disabled = new RecordingStrategy("disabled", 1, evaluations);
        RecordingStrategy aboveMaxLevel = new RecordingStrategy("above-max", 4, evaluations);
        StrategyRegistry registry = new StrategyRegistry();
        registry.registerAll(first, second, disabled, aboveMaxLevel);
        RecordingLearnedPolicy policy = new RecordingLearnedPolicy();
        ContingencyConfiguration config = ContingencyConfiguration.builder()
            .disable("disabled")
            .maxLevel(1)
            .learnedSelection(true)
            .build();
        ContingencyCcrs ccrs = new ContingencyCcrs(registry, config, policy);
        TestContext context = new TestContext();
        context.recordCcrsInvocation(resetRequest());

        ccrs.evaluate(REQUEST, context);

        assertEquals(List.of("first", "second"), evaluations);
        assertEquals(0, policy.createPlanCount);
        assertFalse(evaluations.contains("disabled"));
        assertFalse(evaluations.contains("above-max"));

        evaluations.clear();
        ccrs.evaluate(REQUEST, context);

        assertEquals(List.of("second"), evaluations);
        assertEquals(1, policy.createPlanCount);
        assertEquals(List.of(2), policy.historySizes);
        assertTrue(context.getCcrsHistory(10).stream()
            .anyMatch(trace -> trace.didStrategyReturnNoHelp(
                StopStrategy.ID,
                StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED)));
    }

    @Test
    void bypassStillHonorsBestPerLevelOrchestration() {
        List<String> evaluations = new ArrayList<>();
        StrategyRegistry registry = new StrategyRegistry();
        registry.registerAll(
            new RecordingStrategy("first", 1, evaluations),
            new RecordingStrategy("second", 1, evaluations));
        ContingencyConfiguration config = ContingencyConfiguration.builder()
            .policy(ContingencyConfiguration.EscalationPolicy.BEST_PER_LEVEL)
            .learnedSelection(true)
            .build();
        ContingencyCcrs ccrs = new ContingencyCcrs(registry, config, new RecordingLearnedPolicy());
        TestContext context = new TestContext();
        context.recordCcrsInvocation(resetRequest());

        ccrs.evaluate(REQUEST, context);

        assertEquals(List.of("first"), evaluations);
    }

    private static CcrsTrace resetRequest() {
        StrategyResult reset = StrategyResult.noHelp(
            StopStrategy.ID,
            StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED,
            "test");
        return CcrsTrace.builder(REQUEST)
            .addEvaluation(StopStrategy.ID, 0, CcrsStrategy.Applicability.APPLICABLE, reset, 1L)
            .build();
    }

    private static final class RecordingStrategy implements CcrsStrategy {
        private final String id;
        private final int level;
        private final List<String> evaluations;

        private RecordingStrategy(String id, int level, List<String> evaluations) {
            this.id = id;
            this.level = level;
            this.evaluations = evaluations;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return id; }
        @Override public Category getCategory() { return Category.INTERNAL; }
        @Override public int getEscalationLevel() { return level; }
        @Override public Applicability appliesTo(Situation situation, CcrsContext context) {
            return Applicability.APPLICABLE;
        }
        @Override
        public StrategyResult evaluate(Situation situation, CcrsContext context) {
            evaluations.add(id);
            return StrategyResult.noHelp(
                id,
                StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT,
                "test");
        }
    }

    private static final class RecordingLearnedPolicy implements StrategySelectionPolicy {
        private int createPlanCount;
        private final List<Integer> historySizes = new ArrayList<>();

        @Override
        public StrategySelectionPlan createPlan(StrategySelectionRequest request) {
            createPlanCount++;
            historySizes.add(request.recentTraces().size());
            return new StrategySelectionPlan() {
                @Override
                public List<CcrsStrategy> orderForEvaluation(List<CcrsStrategy> defaultOrder) {
                    List<CcrsStrategy> reversed = new ArrayList<>(defaultOrder);
                    Collections.reverse(reversed);
                    return reversed;
                }

                @Override
                public StrategyGateDecision evaluateGate(
                        CcrsStrategy candidate,
                        List<StrategyResult> currentSuggestions) {
                    return "first".equals(candidate.getId())
                        ? StrategyGateDecision.skip(candidate.getId(), 0.0, "learned skip")
                        : StrategyGateDecision.allow(candidate.getId(), 0.0, "learned allow");
                }

                @Override
                public String describeOrder(List<CcrsStrategy> orderedStrategies) {
                    return orderedStrategies.toString();
                }
            };
        }
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
