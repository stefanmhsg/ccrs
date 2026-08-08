package ccrs.hypermedea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

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
        List<Class<? extends ProtocolBinding>> providerTypes = ServiceLoader.load(ProtocolBinding.class).stream()
            .map(ServiceLoader.Provider::type)
            .toList();

        assertTrue(providerTypes.contains(CcrsHttpBinding.class));
    }

    @Test
    @Order(3)
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
    @Order(4)
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
}
