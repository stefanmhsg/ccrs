package ccrs.capabilities.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategyProvider;
import ccrs.core.contingency.StrategyRegistry;
import ccrs.core.contingency.strategies.social.ConsultationStrategy;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;

class A2aCapabilityTest {

    @Test
    void serviceLoaderFindsTheA2aProvider() {
        List<Class<? extends CcrsStrategyProvider>> providerTypes =
            ServiceLoader.load(CcrsStrategyProvider.class).stream()
            .map(ServiceLoader.Provider::type)
            .toList();

        assertTrue(providerTypes.contains(A2aConsultationStrategyProvider.class));
    }

    @Test
    void providerUsesAFakeChannelAndSkipsAnUnavailableChannel() {
        StrategyRegistry availableRegistry = new StrategyRegistry();
        StrategyRegistry unavailableRegistry = new StrategyRegistry();

        new A2aConsultationStrategyProvider(() -> new FakeChannel(true))
            .registerStrategies(availableRegistry);
        new A2aConsultationStrategyProvider(() -> new FakeChannel(false))
            .registerStrategies(unavailableRegistry);

        assertTrue(availableRegistry.getStrategy(ConsultationStrategy.ID).isPresent());
        assertTrue(unavailableRegistry.isEmpty());
    }

    @Test
    void defaultConfigurationNeedsNoStaticEndpointOrSecret() {
        A2aConfig config = A2aConfig.builder().build();

        assertFalse(config.isLogEvents());
        assertTrue(new A2aConsultationChannel(config).isAvailable());
    }

    @Test
    void targetDiscoveryKeepsOrderAndRemovesDuplicates() {
        A2aConsultationChannel channel = new A2aConsultationChannel(A2aConfig.builder().build());
        Map<String, Object> context = Map.of(
            "agentUri", "https://example.test/agents/one",
            "agentCardUri", "https://example.test/cards/one.json",
            "consultationTargets", List.of(
                Map.of("agentUri", "https://example.test/agents/one",
                    "agentCardUri", "https://example.test/cards/one.json"),
                Map.of("agentUri", "https://example.test/agents/two")));

        List<Map<String, Object>> targets = channel.resolveCandidateTargets(context);

        assertEquals(2, targets.size());
        assertEquals("https://example.test/agents/one", targets.get(0).get("agentUri"));
        assertEquals("https://example.test/agents/two", targets.get(1).get("agentUri"));
    }

    @Test
    void turtleDiscoveryMapsAnAgentResourceToItsCard() {
        A2aConsultationChannel channel = new A2aConsultationChannel(A2aConfig.builder().build());
        String agentUri = "https://example.test/agents/helper";
        String turtle = "<" + agentUri + "> <https://example.org/a2a#agentCard> "
            + "<https://example.test/cards/helper.json> .";

        assertEquals("https://example.test/cards/helper.json",
            channel.parseAgentCardUri(agentUri, turtle).orElseThrow());
    }

    @Test
    void requestAndResponseMappingPreserveTheDocumentedMinimalTextShape() {
        A2aConsultationChannel channel = new A2aConsultationChannel(A2aConfig.builder().build());

        Message request = channel.buildRequest("unlock-door", "https://example.test/cards/helper.json");
        Message response = new Message(
            Message.Role.AGENT,
            List.of(new TextPart("<https://example.test/key> <https://example.test/value> \"red\" .")),
            "response-1",
            null,
            null,
            List.of(),
            Map.of("contentType", "text/turtle"),
            List.of());

        assertEquals("unlock-door", channel.extractText(request));
        assertEquals("<https://example.test/key> <https://example.test/value> \"red\" .",
            channel.extractText(response));
        assertEquals("unlock-door", request.getMetadata().get("requestedSkill"));
        assertEquals("https://example.test/cards/helper.json", request.getMetadata().get("agentCardUri"));
        assertEquals(0.82, channel.parseConfidenceValue("0.82"), 0.000_001);
        assertEquals(1.0, channel.parseConfidenceValue(1), 0.000_001);
    }

    @Test
    void queryWithoutDiscoveredTargetsFailsBeforeAnyNetworkCall() {
        A2aConsultationChannel channel = new A2aConsultationChannel(A2aConfig.builder().build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> channel.query("help", Map.of()));

        assertTrue(error.getMessage().contains("No A2A consultation target"));
    }

    private record FakeChannel(boolean available)
            implements ConsultationStrategy.ConsultationChannel {
        @Override public boolean isAvailable() { return available; }
        @Override public ConsultationStrategy.ConsultationResponse query(
                String question, Map<String, Object> context) {
            return ConsultationStrategy.ConsultationResponse.success("wait", null, "wait");
        }
        @Override public String getChannelType() { return "fake-a2a"; }
    }
}
