package ccrs.capabilities.llm.langchain4j;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.function.Supplier;

import ccrs.capabilities.DotenvConfigFallback;
import ccrs.core.contingency.CcrsStrategyProvider;
import ccrs.core.contingency.CcrsStrategyProviderContext;
import ccrs.core.contingency.LlmClient;
import ccrs.core.contingency.StrategyRegistry;
import ccrs.core.contingency.strategies.internal.prediction.PredictionLlmStrategy;

/**
 * ServiceLoader provider for LangChain4j-backed LLM prediction.
 */
public class Langchain4jPredictionStrategyProvider implements CcrsStrategyProvider {

    private static final Logger logger =
        Logger.getLogger(Langchain4jPredictionStrategyProvider.class.getName());

    private final Supplier<LlmClient> clientSupplier;

    /**
     * Creates the service-loaded provider using environment-backed configuration.
     */
    public Langchain4jPredictionStrategyProvider() {
        this(Langchain4jLlmClient::fromEnvironment);
    }

    Langchain4jPredictionStrategyProvider(Supplier<LlmClient> clientSupplier) {
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
    }

    @Override
    public void registerStrategies(StrategyRegistry registry) {
        registerStrategies(registry, null);
    }

    @Override
    public void registerStrategies(
            StrategyRegistry registry,
            CcrsStrategyProviderContext context) {
        if (registry.getStrategy(PredictionLlmStrategy.ID).isPresent()) {
            logger.info("[Langchain4jProvider] Prediction strategy already registered");
            return;
        }

        try {
            DotenvConfigFallback.enableIfAvailable();
            LlmClient llmClient = clientSupplier.get();
            if (!llmClient.isAvailable()) {
                logger.info("[Langchain4jProvider] LLM client not available");
                return;
            }

            registry.register(new PredictionLlmStrategy(
                llmClient,
                context != null
                    ? context.configuration().getPredictionLlmStrategyOptions()
                    : null));
            logger.info("[Langchain4jProvider] Registered PredictionLlmStrategy");
        } catch (Exception e) {
            logger.log(Level.WARNING,
                "[Langchain4jProvider] LLM prediction strategy not registered: " + e.getMessage());
        }
    }
}
