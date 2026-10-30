package ccrs.core.contingency;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class ContingencyAuditTest {
    @Test
    void eventsKeepInvocationOrderApplicabilityFailureAndMonotonicScope() {
        List<String> messages = new ArrayList<>();
        Logger logger = Logger.getLogger(ContingencyCcrs.class.getName());
        Handler handler = new Handler() {
            public void publish(LogRecord record) { messages.add(record.getMessage()); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        try {
            StrategyRegistry registry = new StrategyRegistry();
            registry.registerAll(new FixtureStrategy("skipped", false), new FixtureStrategy("failed", true));
            ContingencyCcrs ccrs = new ContingencyCcrs(registry,
                ContingencyConfiguration.builder().learnedSelection(false).build());
            String id = UUID.randomUUID().toString();
            CcrsTrace trace = ccrs.evaluateWithTrace(Situation.builder().trigger("fixture").build(), new EmptyContext(), id);
            assertEquals(id, trace.getId());
            List<String> events = messages.stream().filter(m -> m.contains("event=ccrs.contingency.strategy.evaluated")).toList();
            assertEquals(2, events.size());
            for (int i = 0; i < events.size(); i++) {
                String event = events.get(i);
                assertTrue(event.contains("invocation_id=" + id));
                assertTrue(event.contains("strategy_evaluation_id=" + id + ":" + (i + 1)));
                assertTrue(event.contains("evaluation_order=" + (i + 1)));
                assertTrue(event.contains("duration_clock=monotonic"));
                assertTrue(event.contains("escalation_level=1"));
                Instant.parse(event.substring(event.indexOf("timestamp_utc=") + 14));
                assertFalse(event.contains("evaluation_time_ms=-"));
            }
            assertTrue(events.stream().anyMatch(e -> e.contains("result_type=none") && e.contains("confidence=null")));
            assertTrue(events.stream().anyMatch(e -> e.contains("no_help_reason=EVALUATION_FAILED")));
            assertTrue(events.stream().anyMatch(e -> e.contains("strategy_id=skipped") && e.contains("strategy_body_executed=false")));
            assertTrue(events.stream().anyMatch(e -> e.contains("strategy_id=failed") && e.contains("strategy_body_executed=true")));
            assertTrue(trace.getSelectedResults().isEmpty());
        } finally {
            logger.removeHandler(handler);
        }
    }

    private record FixtureStrategy(String id, boolean fail) implements CcrsStrategy {
        public String getId() { return id; }
        public String getName() { return id; }
        public Category getCategory() { return Category.INTERNAL; }
        public int getEscalationLevel() { return 1; }
        public Applicability appliesTo(Situation situation, CcrsContext context) {
            return fail ? Applicability.APPLICABLE : Applicability.NOT_APPLICABLE;
        }
        public StrategyResult evaluate(Situation situation, CcrsContext context) {
            throw new IllegalStateException("fixture failure, no external service");
        }
    }

    private static class EmptyContext implements CcrsContext {
        public List<RdfTriple> query(String subject, String predicate, String object) { return List.of(); }
        public boolean contains(RdfTriple triple) { return false; }
        public Optional<CcrsTrace> getLastCcrsInvocation() { return Optional.empty(); }
        public List<CcrsTrace> getCcrsHistory(int maxCount) { return List.of(); }
        public void recordCcrsInvocation(CcrsTrace trace) { }
    }
}
