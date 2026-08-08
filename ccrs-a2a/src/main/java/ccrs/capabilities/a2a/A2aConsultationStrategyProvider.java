package ccrs.capabilities.a2a;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.function.Supplier;

import ccrs.core.contingency.CcrsStrategyProvider;
import ccrs.core.contingency.CcrsStrategyProviderContext;
import ccrs.core.contingency.StrategyRegistry;
import ccrs.core.contingency.strategies.social.ConsultationStrategy;

/**
 * ServiceLoader provider for A2A-backed consultation.
 */
public class A2aConsultationStrategyProvider implements CcrsStrategyProvider {

    private static final Logger logger =
        Logger.getLogger(A2aConsultationStrategyProvider.class.getName());

    private final Supplier<ConsultationStrategy.ConsultationChannel> channelSupplier;

    /**
     * Creates the service-loaded provider using environment-backed configuration.
     */
    public A2aConsultationStrategyProvider() {
        this(() -> {
            A2aDotenvConfigFallback.enableIfAvailable();
            return new A2aConsultationChannel(A2aConfig.fromEnvironment().build());
        });
    }

    A2aConsultationStrategyProvider(
            Supplier<ConsultationStrategy.ConsultationChannel> channelSupplier) {
        this.channelSupplier = Objects.requireNonNull(channelSupplier, "channelSupplier");
    }

    @Override
    public void registerStrategies(StrategyRegistry registry) {
        registerStrategies(registry, null);
    }

    @Override
    public void registerStrategies(
            StrategyRegistry registry,
            CcrsStrategyProviderContext context) {
        if (registry.getStrategy(ConsultationStrategy.ID).isPresent()) {
            logger.info("[A2AProvider] Consultation strategy already registered");
            return;
        }

        try {
            ConsultationStrategy.ConsultationChannel channel = channelSupplier.get();

            if (!channel.isAvailable()) {
                logger.info("[A2AProvider] A2A consultation channel not available");
                return;
            }

            registry.register(new ConsultationStrategy(
                channel,
                context != null
                    ? context.configuration().getConsultationStrategyOptions()
                    : null));
            logger.info("[A2AProvider] Registered A2A ConsultationStrategy");
        } catch (Exception e) {
            logger.log(Level.WARNING,
                "[A2AProvider] A2A consultation strategy not registered: " + e.getMessage());
        }
    }
}
