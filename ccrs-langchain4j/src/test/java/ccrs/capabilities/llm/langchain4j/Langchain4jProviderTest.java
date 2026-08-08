package ccrs.capabilities.llm.langchain4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategyProvider;
import ccrs.core.contingency.LlmClient;
import ccrs.core.contingency.StrategyRegistry;
import ccrs.core.contingency.strategies.internal.prediction.PredictionLlmStrategy;
import dev.langchain4j.model.chat.ChatModel;

class Langchain4jProviderTest {

    @Test
    void serviceLoaderFindsTheLangchain4jProvider() {
        List<Class<? extends CcrsStrategyProvider>> providerTypes =
            ServiceLoader.load(CcrsStrategyProvider.class).stream()
            .map(ServiceLoader.Provider::type)
            .toList();

        assertTrue(providerTypes.contains(Langchain4jPredictionStrategyProvider.class));
    }

    @Test
    void publicClientApiAcceptsAFakeChatModelWithoutNetworkCalls() throws Exception {
        ChatModel fakeModel = new ChatModel() {
            @Override
            public String chat(String prompt) {
                return "fake response to: " + prompt;
            }
        };

        Langchain4jLlmClient client = Langchain4jLlmClient.fromModel(fakeModel, "test model");

        assertSame(fakeModel, client.getChatModel());
        assertEquals("fake response to: hello", client.complete("hello"));
        assertEquals("test model", client.getDescription());
        assertTrue(client.isAvailable());
    }

    @Test
    void missingConfigurationDoesNotRegisterOrEscapeTheProvider() {
        StrategyRegistry registry = new StrategyRegistry();
        Langchain4jPredictionStrategyProvider provider =
            new Langchain4jPredictionStrategyProvider(() -> {
                throw new IllegalStateException("No API key configured");
            });

        provider.registerStrategies(registry);

        assertTrue(registry.isEmpty());
        assertThrows(IllegalStateException.class, () -> Langchain4jLlmClient.builder().build());
        assertFalse(Langchain4jConfig.builder().build().isValid());
    }

    @Test
    void configuredProviderRegistersThePredictionStrategy() {
        StrategyRegistry registry = new StrategyRegistry();
        LlmClient fakeClient = new LlmClient() {
            @Override public String complete(String prompt) { return "{}"; }
            @Override public boolean isAvailable() { return true; }
            @Override public String getDescription() { return "fake"; }
        };

        new Langchain4jPredictionStrategyProvider(() -> fakeClient).registerStrategies(registry);

        assertTrue(registry.getStrategy(PredictionLlmStrategy.ID).isPresent());
    }
}
