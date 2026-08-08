package ccrs.hypermedea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hypermedea.op.Operation;
import org.hypermedea.op.Response;
import org.hypermedea.op.ResponseCallback;
import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.Interaction;
import jason.asSyntax.Literal;

class JasonInteractionLogConcurrencyTest {

    @Test
    void concurrentReadersAndWritersObserveBoundedConsistentAgentHistory() throws Exception {
        int maxSize = 32;
        int writerCount = 8;
        int writesPerWriter = 75;
        int readerCount = 4;
        JasonInteractionLog log = new JasonInteractionLog(maxSize);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();

        try {
            for (int writer = 0; writer < writerCount; writer++) {
                int writerId = writer;
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int sequence = 0; sequence < writesPerWriter; sequence++) {
                        FakeOperation operation = new FakeOperation(
                            "https://example.test/alice/" + writerId + "/" + sequence);
                        log.onRequest(operation, sequence, "alice");
                        log.onResponse(operation, new FakeResponse(operation), sequence + 1L);
                    }
                    return null;
                }));
            }
            for (int reader = 0; reader < readerCount; reader++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < writesPerWriter; iteration++) {
                        List<Interaction> snapshot = log.getRecentInteractions("alice", maxSize);
                        assertTrue(snapshot.size() <= maxSize);
                        snapshot.forEach(interaction ->
                            assertTrue(interaction.requestUri().startsWith("https://example.test/alice/")));
                        log.getLastInteraction("alice");
                        log.formatAgentHistory("alice");
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

        List<Interaction> finalSnapshot = log.getRecentInteractions("alice", maxSize + 10);
        assertEquals(maxSize, finalSnapshot.size());
        assertEquals(maxSize, finalSnapshot.stream().map(Interaction::requestUri).distinct().count());
    }

    @Test
    void historiesRemainPartitionedAndMissingHistoryDoesNotRevealOtherAgents() {
        JasonInteractionLog log = new JasonInteractionLog();
        complete(log, "alice", "https://example.test/alice/one");
        complete(log, "bob", "https://example.test/bob/one");

        assertEquals(List.of("https://example.test/alice/one"), requestUris(log, "alice"));
        assertEquals(List.of("https://example.test/bob/one"), requestUris(log, "bob"));

        String missing = log.formatAgentHistory("charlie");
        assertTrue(missing.contains("requested='charlie'"));
        assertFalse(missing.contains("alice"));
        assertFalse(missing.contains("bob"));
    }

    @Test
    void racingResponseAndErrorCompleteAnInflightOperationOnlyOnce() throws Exception {
        JasonInteractionLog log = new JasonInteractionLog();
        FakeOperation operation = new FakeOperation("https://example.test/race");
        log.onRequest(operation, 1L, "alice");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> response = executor.submit(() -> {
                start.await();
                log.onResponse(operation, new FakeResponse(operation), 2L);
                return null;
            });
            Future<?> error = executor.submit(() -> {
                start.await();
                log.onError(operation, 2L);
                return null;
            });
            start.countDown();
            response.get(10, TimeUnit.SECONDS);
            error.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, log.getRecentInteractions("alice", 10).size());
    }

    @Test
    void blankAgentNamesUseTheDocumentedUnknownPartition() {
        JasonInteractionLog log = new JasonInteractionLog();
        complete(log, " ", "https://example.test/unknown");

        assertEquals(List.of("https://example.test/unknown"), requestUris(log, "unknown"));
        assertTrue(log.getRecentInteractions(" ", 0).isEmpty());
    }

    private static void complete(JasonInteractionLog log, String agentName, String uri) {
        FakeOperation operation = new FakeOperation(uri);
        log.onRequest(operation, 1L, agentName);
        log.onResponse(operation, new FakeResponse(operation), 2L);
    }

    private static List<String> requestUris(JasonInteractionLog log, String agentName) {
        return log.getRecentInteractions(agentName, 10).stream()
            .map(Interaction::requestUri)
            .toList();
    }

    private static final class FakeOperation implements Operation {
        private final String targetUri;
        private Collection<Literal> payload = List.of();

        private FakeOperation(String targetUri) {
            this.targetUri = targetUri;
        }

        @Override public String getTargetURI() { return targetUri; }
        @Override public Map<String, Object> getForm() { return Map.of("method", "GET"); }
        @Override public Collection<Literal> getPayload() { return payload; }
        @Override public void setPayload(Literal value) { payload = List.of(value); }
        @Override public void setPayload(Collection<Literal> values) { payload = List.copyOf(values); }
        @Override public boolean isSafe() { return true; }
        @Override public boolean isIdempotent() { return true; }
        @Override public boolean isAsync() { return false; }
        @Override public void sendRequest() throws IOException { }
        @Override public Response getResponse() { return null; }
        @Override public void registerResponseCallback(ResponseCallback callback) { }
        @Override public void unregisterResponseCallback(ResponseCallback callback) { }
    }

    private record FakeResponse(Operation operation) implements Response {
        @Override public Operation getOperation() { return operation; }
        @Override public ResponseStatus getStatus() { return ResponseStatus.OK; }
        @Override public Collection<Literal> getPayload() { return List.of(); }
    }
}
