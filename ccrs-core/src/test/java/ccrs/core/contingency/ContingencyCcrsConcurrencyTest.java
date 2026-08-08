package ccrs.core.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class ContingencyCcrsConcurrencyTest {

    @Test
    void evaluationUsesOneConfigurationSnapshotDuringConcurrentReconfiguration() throws Exception {
        CountDownLatch evaluationStarted = new CountDownLatch(1);
        CountDownLatch continueEvaluation = new CountDownLatch(1);
        StrategyRegistry registry = new StrategyRegistry();
        registry.registerAll(
            new SuggestingStrategy("first", 0.9, evaluationStarted, continueEvaluation),
            new SuggestingStrategy("second", 0.8, null, null));
        ContingencyCcrs ccrs = new ContingencyCcrs(
            registry,
            ContingencyConfiguration.builder()
                .learnedSelection(false)
                .maxSuggestions(1)
                .build());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<CcrsTrace> evaluation = executor.submit(() -> ccrs.evaluateWithTrace(
                Situation.builder().trigger("test").build(),
                new EmptyContext()));
            assertTrue(
                evaluationStarted.await(10, TimeUnit.SECONDS),
                "Evaluation did not reach the blocking strategy");

            ccrs.setConfig(ContingencyConfiguration.builder()
                .learnedSelection(false)
                .maxSuggestions(2)
                .build());
            continueEvaluation.countDown();

            CcrsTrace trace = evaluation.get(10, TimeUnit.SECONDS);
            assertEquals(1, trace.getSelectedResults().size());
            assertEquals("first", trace.getSelectedResults().getFirst().getStrategyId());
            assertEquals(2, ccrs.getConfig().getMaxSuggestions());
        } finally {
            continueEvaluation.countDown();
            executor.shutdownNow();
        }
    }

    private static final class SuggestingStrategy implements CcrsStrategy {
        private final String id;
        private final double confidence;
        private final CountDownLatch started;
        private final CountDownLatch proceed;

        private SuggestingStrategy(
                String id,
                double confidence,
                CountDownLatch started,
                CountDownLatch proceed) {
            this.id = id;
            this.confidence = confidence;
            this.started = started;
            this.proceed = proceed;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return id; }
        @Override public Category getCategory() { return Category.INTERNAL; }
        @Override public int getEscalationLevel() { return 1; }
        @Override public Applicability appliesTo(Situation situation, CcrsContext context) {
            return Applicability.APPLICABLE;
        }

        @Override
        public StrategyResult evaluate(Situation situation, CcrsContext context) {
            if (started != null) {
                started.countDown();
            }
            if (proceed != null) {
                try {
                    if (!proceed.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to continue evaluation");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Evaluation interrupted", e);
                }
            }
            return StrategyResult.suggest(id, "test")
                .confidence(confidence)
                .rationale("test")
                .build();
        }
    }

    private static final class EmptyContext implements CcrsContext {
        @Override public List<RdfTriple> query(String subject, String predicate, String object) {
            return List.of();
        }
        @Override public boolean contains(RdfTriple triple) { return false; }
        @Override public Optional<CcrsTrace> getLastCcrsInvocation() { return Optional.empty(); }
        @Override public List<CcrsTrace> getCcrsHistory(int maxCount) { return List.of(); }
        @Override public void recordCcrsInvocation(CcrsTrace trace) { }
    }
}
