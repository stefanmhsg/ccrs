package ccrs.capabilities.a2a;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aCardCacheConcurrencyTest {

    @Test
    void differentCardUrisResolveConcurrentlyWithoutCrossTalk() throws Exception {
        CountDownLatch arrivals = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        A2aConsultationChannel channel = new A2aConsultationChannel(
            A2aConfig.builder().build(),
            null,
            uri -> {
                arrivals.countDown();
                if (!arrivals.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("card resolutions did not overlap");
                }
                release.await(5, TimeUnit.SECONDS);
                return card(uri);
            });
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AgentCard> first = executor.submit(() -> channel.resolveAgentCard("card-A"));
            Future<AgentCard> second = executor.submit(() -> channel.resolveAgentCard("card-B"));

            assertTrue(arrivals.await(5, TimeUnit.SECONDS));
            release.countDown();

            assertEquals("card-A", first.get(5, TimeUnit.SECONDS).name());
            assertEquals("card-B", second.get(5, TimeUnit.SECONDS).name());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static AgentCard card(String id) {
        return new AgentCard(
            id, "test card", "https://example.test/" + id, null, "1", null,
            new AgentCapabilities(false, false, false, List.of()),
            List.of("text"), List.of("text"), List.of(), false, Map.of(), List.of(),
            null, List.of(), "REST", "0.3", List.of());
    }
}
