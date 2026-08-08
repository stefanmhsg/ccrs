package ccrs.hypermedea;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hypermedea.op.Operation;
import org.hypermedea.op.Response;

import ccrs.core.contingency.dto.Interaction;
import ccrs.jacamo.jason.contingency.InteractionHistoryProvider;

/**
 * A centralized interaction log that partitions history by Agent Name.
 * 
 * It bridges the gap between CArtAgO's synchronous execution model (Requests)
 * and Hypermedea's asynchronous I/O (Responses) by preserving the agent identity
 * in an inflight context.
 *
 * <p>Agent histories are independently synchronized. Agent names identify
 * logical history partitions and are not an authentication boundary.</p>
 */
public class JasonInteractionLog implements InteractionLogSink, InteractionHistoryProvider {

    private static final Logger logger = Logger.getLogger(JasonInteractionLog.class.getName());
    private static final String UNKNOWN_AGENT = "unknown";
    private static final int DEFAULT_MAX_SIZE = 1000;

    /**
     * Preserves the agent identity between the Request (Agent Thread) 
     * and the Response (Network Thread).
     */
    private record InflightContext(InteractionBuilder builder, String agentName) {}

    // Maps the specific operation instance to its context (Builder + Agent Name)
    private final Map<Operation, InflightContext> inflight = new ConcurrentHashMap<>();

    // Partitioned history: Agent Name -> Their independently synchronized history
    private final Map<String, AgentHistory> agentHistories = new ConcurrentHashMap<>();

    private final int maxSize;

    public JasonInteractionLog() {
        this(DEFAULT_MAX_SIZE);
    }

    JasonInteractionLog(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1");
        }
        this.maxSize = maxSize;
    }
    // === WRITE API (Called by Artifacts) ===

    /**
     * Called when we explicitly know the agent name (from Artifact)
     */
    public void onRequest(Operation op, long ts, String agentName) {
        String partition = normalizeAgentName(agentName);
        logger.fine("[JasonInteractionLog] onRequest from agent: '" + partition + "'");
        inflight.put(op, new InflightContext(InteractionBuilder.fromRequest(op, ts), partition));
    }

    // Fallback for interface compliance (defaults to "unknown")
    @Override
    public void onRequest(Operation op, long ts) {
        logger.fine("[JasonInteractionLog] onRequest from unknown agent");
        onRequest(op, ts, "unknown");
    }

    @Override
    public void onResponse(Operation op, Response res, long ts) {
        InflightContext context = inflight.remove(op);
        if (context == null) return;

        logger.fine("[JasonInteractionLog] onResponse for agent: '" + context.agentName() + "'");
        try {
            Interaction interaction = context.builder().withResponse(res, ts).build();
            append(context.agentName(), interaction);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "[JasonInteractionLog] Failed to append response interaction for agent: '" + context.agentName() + "'", e);
            append(context.agentName(), context.builder().withError(ts).build());
        }
    }

    @Override
    public void onError(Operation op, long ts) {
        InflightContext context = inflight.remove(op);
        if (context == null) return;

        logger.fine("[JasonInteractionLog] onError for agent: '" + context.agentName() + "'");
        try {
            Interaction interaction = context.builder().withError(ts).build();
            append(context.agentName(), interaction);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "[JasonInteractionLog] Failed to append error interaction for agent: '" + context.agentName() + "'", e);
        }
    }

    private void append(String agentName, Interaction interaction) {
        AgentHistory history = agentHistories.computeIfAbsent(
            normalizeAgentName(agentName),
            ignored -> new AgentHistory());
        int size = history.append(interaction, maxSize);
        logger.fine("[JasonInteractionLog] Appended interaction for agent: '"
            + normalizeAgentName(agentName) + "' (size=" + size + ")");
    }

    // === READ API (Called by Agents/Context) ===

    public List<Interaction> getRecentInteractions(String agentName, int n) {
        if (n <= 0) return List.of();
        String partition = normalizeAgentName(agentName);
        AgentHistory history = agentHistories.get(partition);
        if (history == null) return List.of();
        List<Interaction> recent = history.recent(n);
        logger.fine("[JasonInteractionLog] getRecentInteractions for agent: '"
            + partition + "' (requested=" + n + ", available=" + history.size() + ")");
        return recent;
    }

    public Optional<Interaction> getLastInteraction(String agentName) {
        String partition = normalizeAgentName(agentName);
        AgentHistory history = agentHistories.get(partition);
        if (history == null) return Optional.empty();
        Optional<Interaction> last = history.last();
        if (last.isPresent()) {
            logger.fine("[JasonInteractionLog] getLastInteraction for agent: '" + partition + "'");
        }
        return last;
    }

    public List<Interaction> getInteractionsFor(String agentName, String logicalSource) {
        String partition = normalizeAgentName(agentName);
        AgentHistory history = agentHistories.get(partition);
        if (history == null) return List.of();
        logger.fine("[JasonInteractionLog] getInteractionsFor for agent: '" + partition
            + "' and logicalSource: '" + logicalSource + "'");
        return history.forSource(logicalSource);
    }

    public String formatAgentHistory(String agentName) {
        String partition = normalizeAgentName(agentName);
        AgentHistory history = agentHistories.get(partition);
        return history == null
            ? "[JasonInteractionLog] {requested='" + partition + "', found=none}"
            : history.format(partition);
    }

    private static String normalizeAgentName(String agentName) {
        return agentName == null || agentName.isBlank() ? UNKNOWN_AGENT : agentName;
    }

    /**
     * Encapsulates one agent partition so readers and writers observe consistent
     * snapshots without serializing unrelated agents through one global lock.
     */
    private static final class AgentHistory {
        private final Deque<Interaction> interactions = new ArrayDeque<>();

        private synchronized int append(Interaction interaction, int maxSize) {
            interactions.addFirst(Objects.requireNonNull(interaction, "interaction"));
            while (interactions.size() > maxSize) interactions.removeLast();
            return interactions.size();
        }

        private synchronized List<Interaction> recent(int limit) {
            return interactions.stream().limit(limit).toList();
        }

        private synchronized Optional<Interaction> last() {
            return Optional.ofNullable(interactions.peekFirst());
        }

        private synchronized List<Interaction> forSource(String logicalSource) {
            return interactions.stream()
                .filter(interaction -> Objects.equals(logicalSource, interaction.logicalSource()))
                .toList();
        }

        private synchronized int size() {
            return interactions.size();
        }

        private synchronized String format(String agentName) {
            Interaction first = interactions.peekFirst();
            return first == null
                ? "[JasonInteractionLog] {requested='" + agentName + "', found=none}"
                : "[JasonInteractionLog] {history[0]=" + first
                    + ", size=" + interactions.size() + "}";
        }
    }

}
