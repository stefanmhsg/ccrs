package ccrs.core.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.rdf.CcrsContext;

class StrategyRegistryConcurrencyTest {

    @Test
    void queryResultsAreDetachedImmutableSnapshots() {
        StrategyRegistry registry = new StrategyRegistry();
        registry.register(new TestStrategy("first"));

        Collection<CcrsStrategy> snapshot = registry.getAll();
        registry.register(new TestStrategy("second"));

        assertEquals(1, snapshot.size());
        assertEquals(2, registry.size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void concurrentRegistrationAndSnapshotQueriesRemainConsistent() throws Exception {
        int strategyCount = 200;
        StrategyRegistry registry = new StrategyRegistry();
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();

        try {
            for (int index = 0; index < strategyCount; index++) {
                int strategyIndex = index;
                tasks.add(executor.submit(() -> {
                    start.await();
                    registry.register(new TestStrategy("strategy-" + strategyIndex));
                    return null;
                }));
            }
            for (int reader = 0; reader < 8; reader++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 100; iteration++) {
                        Collection<CcrsStrategy> snapshot = registry.getAll();
                        assertEquals(snapshot.size(), snapshot.stream().map(CcrsStrategy::getId).distinct().count());
                        registry.getByCategory(CcrsStrategy.Category.INTERNAL);
                        registry.getOrderedForEvaluation(ContingencyConfiguration.defaults());
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(strategyCount, registry.size());
        assertEquals(strategyCount, registry.getAll().stream()
            .map(CcrsStrategy::getId)
            .distinct()
            .count());
    }

    private record TestStrategy(String id) implements CcrsStrategy {
        @Override public String getId() { return id; }
        @Override public String getName() { return id; }
        @Override public Category getCategory() { return Category.INTERNAL; }
        @Override public int getEscalationLevel() { return 1; }
        @Override public Applicability appliesTo(Situation situation, CcrsContext context) {
            return Applicability.APPLICABLE;
        }
        @Override public StrategyResult evaluate(Situation situation, CcrsContext context) {
            return StrategyResult.noHelp(
                id,
                StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT,
                "test");
        }
    }
}
