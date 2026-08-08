package ccrs.core.contingency;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.util.logging.Logger;

/**
 * Registry for contingency strategies.
 * Manages strategy registration, lookup, and filtering.
 *
 * <p>All operations are thread-safe. Query methods return snapshots, so a
 * caller can iterate a result while another thread changes the registry.</p>
 */
public class StrategyRegistry {
    
    private static final Logger logger = Logger.getLogger(StrategyRegistry.class.getName());

    private final Map<String, CcrsStrategy> strategies = new LinkedHashMap<>();
    
    /**
     * Register a strategy.
     * 
     * @param strategy The strategy to register
     * @throws IllegalArgumentException if strategy with same ID already exists
     */
    public synchronized void register(CcrsStrategy strategy) {
        if (strategies.containsKey(strategy.getId())) {
            throw new IllegalArgumentException(
                "Strategy already registered: " + strategy.getId());
        }
        strategies.put(strategy.getId(), strategy);
        logger.info("Registered strategy: " + strategy.getId());
    }
    
    /**
     * Register multiple strategies.
     */
    public synchronized void registerAll(CcrsStrategy... strategies) {
        for (CcrsStrategy strategy : strategies) {
            register(strategy);
        }
    }
    
    /**
     * Unregister a strategy by ID.
     * 
     * @param strategyId The strategy ID to remove
     * @return The removed strategy, or null if not found
     */
    public synchronized CcrsStrategy unregister(String strategyId) {
        logger.info("Unregistering strategy: " + strategyId);
        return strategies.remove(strategyId);
    }
    
    /**
     * Get a strategy by ID.
     */
    public synchronized Optional<CcrsStrategy> getStrategy(String strategyId) {
        return Optional.ofNullable(strategies.get(strategyId));
    }
    
    /**
     * Get all registered strategies.
     */
    public synchronized Collection<CcrsStrategy> getAll() {
        return List.copyOf(strategies.values());
    }
    
    /**
     * Get strategies by category.
     */
    public synchronized List<CcrsStrategy> getByCategory(CcrsStrategy.Category category) {
        return strategies.values().stream()
            .filter(s -> s.getCategory() == category)
            .collect(Collectors.toList());
    }
    
    /**
     * Get strategies by escalation level.
     */
    public synchronized List<CcrsStrategy> getByLevel(int level) {
        return strategies.values().stream()
            .filter(s -> s.getEscalationLevel() == level)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all enabled strategies, filtered by configuration.
     */
    public synchronized List<CcrsStrategy> getEnabled(ContingencyConfiguration config) {
        return strategies.values().stream()
            .filter(config::isStrategyEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Get strategies sorted by the default escalation level order.
     * Learned runtime ordering may reorder strategies within a level later.
     */
    public synchronized List<CcrsStrategy> getOrderedForEvaluation(ContingencyConfiguration config) {
        List<CcrsStrategy> enabled = getEnabled(config);
        
        // Custom comparator: L1, L2, L3, L4, then L0 (last resort)
        Comparator<CcrsStrategy> escalationOrder = (a, b) -> {
            int levelA = a.getEscalationLevel() == 0 ? 100 : a.getEscalationLevel();
            int levelB = b.getEscalationLevel() == 0 ? 100 : b.getEscalationLevel();
            return Integer.compare(levelA, levelB);
        };
        
        enabled.sort(escalationOrder);

        logger.info("Ordered strategies for evaluation: " +
            enabled.stream().map(CcrsStrategy::getId).collect(Collectors.joining(", ")));

        return enabled;
    }
    
    /**
     * Check if registry has any strategies.
     */
    public synchronized boolean isEmpty() {
        return strategies.isEmpty();
    }
    
    /**
     * Get count of registered strategies.
     */
    public synchronized int size() {
        return strategies.size();
    }
    
    /**
     * Clear all registered strategies.
     */
    public synchronized void clear() {
        strategies.clear();
    }
    
    @Override
    public synchronized String toString() {
        return String.format("StrategyRegistry{%d strategies: %s}",
            strategies.size(), strategies.keySet());
    }
}
