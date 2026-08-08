package ccrs.jacamo.jason.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.ContingencyCcrs;
import ccrs.jacamo.CcrsJacamoRuntime;

class EvaluateRuntimeConcurrencyTest {

    @Test
    void differentInternalActionInstancesInitializeOneSharedCcrsRuntime() throws Exception {
        int callerCount = 24;
        AtomicInteger creations = new AtomicInteger();
        Field cachedRuntime = evaluate.class.getDeclaredField("contingencyCcrs");
        cachedRuntime.setAccessible(true);
        Method getCcrs = evaluate.class.getDeclaredMethod("getCcrs");
        getCcrs.setAccessible(true);
        cachedRuntime.set(null, null);
        CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> {
            creations.incrementAndGet();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Runtime creation interrupted", e);
            }
            return new ContingencyCcrs();
        });
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();

        try {
            for (int caller = 0; caller < callerCount; caller++) {
                evaluate action = new evaluate();
                tasks.add(executor.submit(() -> {
                    start.await();
                    getCcrs.invoke(action);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }

            assertEquals(1, creations.get());
        } finally {
            executor.shutdownNow();
            cachedRuntime.set(null, null);
            CcrsJacamoRuntime.reset();
        }
    }
}
