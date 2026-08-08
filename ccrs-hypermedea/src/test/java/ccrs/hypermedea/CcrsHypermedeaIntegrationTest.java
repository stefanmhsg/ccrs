package ccrs.hypermedea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hypermedea.ct.RepresentationHandlers;
import org.hypermedea.op.BaseResponse;
import org.hypermedea.op.Operation;
import org.hypermedea.op.ProtocolBinding;
import org.hypermedea.op.Response;
import org.hypermedea.op.ResponseCallback;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import ccrs.core.contingency.dto.Interaction;
import ccrs.jacamo.CcrsJacamoRuntime;
import jason.asSyntax.Literal;

@TestMethodOrder(OrderAnnotation.class)
class CcrsHypermedeaIntegrationTest {

    @Test
    @Order(1)
    void globalRegistryInstallsTheSharedInteractionHistoryProvider() {
        CcrsJacamoRuntime.reset();

        JasonInteractionLog sharedLog = CcrsGlobalRegistry.getSharedLog();

        assertSame(sharedLog, CcrsJacamoRuntime.getInteractionHistoryProvider());
    }

    @Test
    @Order(2)
    void serviceLoaderFindsTheCcrsHttpBinding() {
        boolean providerFound = ServiceLoader.load(ProtocolBinding.class).stream()
            .map(ServiceLoader.Provider::type)
            .anyMatch(CcrsHttpBinding.class::equals);

        assertTrue(providerFound);
    }

    @Test
    @Order(3)
    void hypermedeaDeserializesTurtleWithTheResolvedCcrsRuntime() throws IOException {
        String turtle = """
            @prefix ex: <https://example.test/> .
            ex:subject ex:predicate ex:object .
            """;

        Collection<Literal> representation = RepresentationHandlers.deserialize(
            new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
            "https://example.test/",
            "text/turtle"
        );

        assertEquals(1, representation.size());
    }

    @Test
    @Order(4)
    void responseWrapperSerializesConcurrentRepresentationHandlerAccess() throws Exception {
        TurtleResponse delegate = new TurtleResponse(
            new FakeOperation("https://example.test/resource")
        );
        Response serialized = CcrsHttpOperation.withSerializedPayloadAccess(delegate);
        int workerCount = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try {
            List<Future<Integer>> results = java.util.stream.IntStream.range(0, workerCount)
                .mapToObj(ignored -> executor.submit(() -> {
                    start.await();
                    return serialized.getPayload().size();
                }))
                .toList();

            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(1, result.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, delegate.maxConcurrentCalls());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(5)
    void bindingConstructsAnInstrumentedOperationOnlyWhileALogSinkIsInstalled() {
        CcrsHttpBinding binding = new CcrsHttpBinding();
        RecordingSink sink = new RecordingSink();
        Map<String, Object> getForm = Map.of(Operation.METHOD_NAME_FIELD, Operation.GET);

        Operation plain = binding.bind("https://example.test/plain", getForm);
        assertFalse(plain instanceof CcrsHttpOperation);

        CcrsGlobalRegistry.setSink(sink);
        try {
            Operation instrumented = binding.bind("https://example.test/instrumented", getForm);
            assertInstanceOf(CcrsHttpOperation.class, instrumented);
        } finally {
            CcrsGlobalRegistry.clear();
        }
    }

    @Test
    @Order(6)
    void interactionLogPartitionsCompletedInteractionsByAgent() {
        JasonInteractionLog log = new JasonInteractionLog();
        FakeOperation operation = new FakeOperation("https://example.test/orders/1");

        log.onRequest(operation, 10L, "alice");
        log.onResponse(operation, new FakeResponse(operation, Response.ResponseStatus.OK), 20L);

        Interaction interaction = log.getLastInteraction("alice").orElseThrow();
        assertEquals("GET", interaction.method());
        assertEquals("https://example.test/orders/1", interaction.requestUri());
        assertEquals(Interaction.Outcome.SUCCESS, interaction.outcome());
        assertTrue(log.getRecentInteractions("bob", 10).isEmpty());
    }

    private static final class RecordingSink implements InteractionLogSink {
        @Override public void onRequest(Operation operation, long timestamp) { }
        @Override public void onResponse(Operation operation, Response response, long timestamp) { }
        @Override public void onError(Operation operation, long timestamp) { }
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

    private record FakeResponse(Operation operation, ResponseStatus status) implements Response {
        @Override public Operation getOperation() { return operation; }
        @Override public ResponseStatus getStatus() { return status; }
        @Override public Collection<Literal> getPayload() { return List.of(); }
    }

    private static final class TurtleResponse extends BaseResponse {
        private final AtomicInteger activeCalls = new AtomicInteger();
        private final AtomicInteger maxConcurrentCalls = new AtomicInteger();

        private TurtleResponse(Operation operation) {
            super(operation);
        }

        @Override
        public ResponseStatus getStatus() {
            return ResponseStatus.OK;
        }

        @Override
        public Collection<Literal> getPayload() {
            int active = activeCalls.incrementAndGet();
            maxConcurrentCalls.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(10);
                String turtle = """
                    @prefix ex: <https://example.test/> .
                    ex:subject ex:predicate ex:object .
                    """;
                return RepresentationHandlers.deserialize(
                    new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
                    "https://example.test/",
                    "text/turtle"
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                activeCalls.decrementAndGet();
            }
        }

        private int maxConcurrentCalls() {
            return maxConcurrentCalls.get();
        }
    }
}
