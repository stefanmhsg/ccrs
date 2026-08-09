package ccrs.jacamo;

import java.util.Objects;
import java.util.function.Supplier;

import ccrs.core.contingency.ContingencyCcrs;
import ccrs.core.contingency.ContingencyConfiguration;
import ccrs.core.contingency.ContingencyCcrsFactory;
import ccrs.jacamo.jason.contingency.InteractionHistoryProvider;

/**
 * Runtime wiring points for the JaCaMo adapter.
 *
 * <p>This class keeps JaCaMo/Jason integration independent from concrete
 * HTTP artifact implementations and optional capability modules. Applications
 * or optional modules can install providers here before or during MAS startup.</p>
 *
 * <p>The installed values are process-wide application configuration, not
 * per-agent or per-tenant state. Volatile publication makes concurrent reads
 * safe, but applications should configure this class during startup and avoid
 * competing writes. Isolate mutually untrusted tenants in separate JVMs.</p>
 */
public final class CcrsJacamoRuntime {

    private static volatile InteractionHistoryProvider interactionHistoryProvider =
        InteractionHistoryProvider.empty();

    private static volatile ContingencyConfiguration contingencyConfiguration =
        ContingencyConfiguration.defaults();

    private static volatile Supplier<ContingencyCcrs> contingencyCcrsSupplier =
        CcrsJacamoRuntime::createDefaultContingencyCcrs;

    private static final Object contingencyLock = new Object();
    private static volatile ContingencyCcrs cachedContingencyCcrs;

    private CcrsJacamoRuntime() {
    }

    public static InteractionHistoryProvider getInteractionHistoryProvider() {
        return interactionHistoryProvider;
    }

    public static void setInteractionHistoryProvider(InteractionHistoryProvider provider) {
        interactionHistoryProvider = provider != null
            ? provider
            : InteractionHistoryProvider.empty();
    }

    public static ContingencyConfiguration getContingencyConfiguration() {
        return contingencyConfiguration;
    }

    public static void setContingencyConfiguration(ContingencyConfiguration configuration) {
        synchronized (contingencyLock) {
            contingencyConfiguration = configuration != null
                ? configuration
                : ContingencyConfiguration.defaults();
            cachedContingencyCcrs = null;
        }
    }

    public static ContingencyCcrs createContingencyCcrs() {
        return contingencyCcrsSupplier.get();
    }

    /**
     * Returns the evaluator shared by all agents in the current JVM configuration generation.
     *
     * @return the shared evaluator
     */
    public static ContingencyCcrs getOrCreateContingencyCcrs() {
        ContingencyCcrs current = cachedContingencyCcrs;
        if (current != null) {
            return current;
        }
        synchronized (contingencyLock) {
            if (cachedContingencyCcrs == null) {
                cachedContingencyCcrs = contingencyCcrsSupplier.get();
            }
            return cachedContingencyCcrs;
        }
    }

    public static void setContingencyCcrsSupplier(Supplier<ContingencyCcrs> supplier) {
        synchronized (contingencyLock) {
            contingencyCcrsSupplier = Objects.requireNonNull(supplier, "supplier");
            cachedContingencyCcrs = null;
        }
    }

    public static void reset() {
        interactionHistoryProvider = InteractionHistoryProvider.empty();
        synchronized (contingencyLock) {
            contingencyConfiguration = ContingencyConfiguration.defaults();
            contingencyCcrsSupplier = CcrsJacamoRuntime::createDefaultContingencyCcrs;
            cachedContingencyCcrs = null;
        }
    }

    private static ContingencyCcrs createDefaultContingencyCcrs() {
        return ContingencyCcrsFactory.withDefaultsAndDiscoveredProviders(
            contingencyConfiguration);
    }
}
