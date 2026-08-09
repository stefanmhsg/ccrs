package ccrs.jacamo;

import ccrs.core.contingency.ContingencyCcrs;
import ccrs.core.contingency.ContingencyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class CcrsJacamoRuntimeConcurrencyTest {

    @AfterEach
    void resetRuntime() {
        CcrsJacamoRuntime.reset();
    }

    @Test
    void concurrentAgentsInitializeOneSharedEvaluatorPerGeneration() throws Exception {
        int callerCount = 24;
        AtomicInteger creations = new AtomicInteger();
        CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> {
            creations.incrementAndGet();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new ContingencyCcrs();
        });
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ContingencyCcrs>> tasks = new ArrayList<>();

        try {
            for (int caller = 0; caller < callerCount; caller++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    return CcrsJacamoRuntime.getOrCreateContingencyCcrs();
                }));
            }
            start.countDown();
            ContingencyCcrs selected = tasks.getFirst().get(10, TimeUnit.SECONDS);
            for (Future<ContingencyCcrs> task : tasks) {
                assertSame(selected, task.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, creations.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void configurationAndSupplierChangesApplyToEveryAgentsNextLookup() {
        ContingencyCcrs generationOne = new ContingencyCcrs();
        ContingencyCcrs generationTwo = new ContingencyCcrs();
        CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> generationOne);

        ContingencyCcrs agentAInFlight = CcrsJacamoRuntime.getOrCreateContingencyCcrs();
        assertSame(agentAInFlight, CcrsJacamoRuntime.getOrCreateContingencyCcrs());

        CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> generationTwo);
        ContingencyCcrs agentANext = CcrsJacamoRuntime.getOrCreateContingencyCcrs();
        ContingencyCcrs agentBNext = CcrsJacamoRuntime.getOrCreateContingencyCcrs();

        assertSame(generationOne, agentAInFlight);
        assertSame(generationTwo, agentANext);
        assertSame(agentANext, agentBNext);
        assertNotSame(agentAInFlight, agentANext);

        CcrsJacamoRuntime.setContingencyCcrsSupplier(ContingencyCcrs::new);
        ContingencyCcrs beforeConfigurationChange =
            CcrsJacamoRuntime.getOrCreateContingencyCcrs();
        CcrsJacamoRuntime.setContingencyConfiguration(ContingencyConfiguration.defaults());
        assertNotSame(
            beforeConfigurationChange,
            CcrsJacamoRuntime.getOrCreateContingencyCcrs());
    }

    @Test
    void resetInvalidatesTheSelectedEvaluator() {
        ContingencyCcrs configured = new ContingencyCcrs();
        CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> configured);
        assertSame(configured, CcrsJacamoRuntime.getOrCreateContingencyCcrs());

        CcrsJacamoRuntime.reset();

        assertNotSame(configured, CcrsJacamoRuntime.getOrCreateContingencyCcrs());
    }
}
