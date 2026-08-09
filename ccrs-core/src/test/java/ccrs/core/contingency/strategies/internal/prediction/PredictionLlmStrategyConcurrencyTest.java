package ccrs.core.contingency.strategies.internal.prediction;

import ccrs.core.contingency.dto.Interaction;
import ccrs.core.contingency.dto.LlmActionResponse;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictionLlmStrategyConcurrencyTest {

    @Test
    void sharedStrategyAllowsOverlappingCallsWithoutResponseCrossTalk() throws Exception {
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        PredictionLlmStrategy strategy = new PredictionLlmStrategy(
            prompt -> {
                int nowActive = active.incrementAndGet();
                maximumActive.accumulateAndGet(nowActive, Math::max);
                bothEntered.countDown();
                if (!bothEntered.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("LLM calls did not overlap");
                }
                release.await(5, TimeUnit.SECONDS);
                active.decrementAndGet();
                return prompt;
            },
            context -> (String) context.get("currentResource"),
            raw -> LlmActionResponse.valid("wait", raw, "response for " + raw));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<StrategyResult> agentA = executor.submit(() -> strategy.evaluate(
                Situation.builder().trigger("blocked").currentResource("agent-A").build(),
                new TestContext("agent-A")));
            Future<StrategyResult> agentB = executor.submit(() -> strategy.evaluate(
                Situation.builder().trigger("blocked").currentResource("agent-B").build(),
                new TestContext("agent-B")));

            bothEntered.await(5, TimeUnit.SECONDS);
            release.countDown();

            assertEquals("agent-A", agentA.get(5, TimeUnit.SECONDS).asSuggestion().getActionTarget());
            assertEquals("agent-B", agentB.get(5, TimeUnit.SECONDS).asSuggestion().getActionTarget());
            assertEquals(2, maximumActive.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private record TestContext(String agentId) implements CcrsContext {
        @Override public List<RdfTriple> query(String subject, String predicate, String object) { return List.of(); }
        @Override public boolean contains(RdfTriple triple) { return false; }
        @Override public List<Interaction> getRecentInteractions(int maxCount) { return List.of(); }
        @Override public Optional<String> getCurrentResource() { return Optional.empty(); }
        @Override public String getAgentId() { return agentId; }
        @Override public Optional<CcrsTrace> getLastCcrsInvocation() { return Optional.empty(); }
        @Override public List<CcrsTrace> getCcrsHistory(int maxCount) { return List.of(); }
        @Override public void recordCcrsInvocation(CcrsTrace trace) { }
        @Override public boolean hasHistory() { return false; }
        @Override public boolean hasLlmAccess() { return true; }
    }
}
