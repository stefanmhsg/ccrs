package ccrs.jacamo.jason.contingency;

import ccrs.core.contingency.ContingencyCcrs;
import ccrs.core.contingency.ContingencyConfiguration;
import ccrs.core.contingency.StrategyRegistry;
import ccrs.core.contingency.strategies.internal.RetryStrategy;
import ccrs.core.logging.CcrsEventLogger;
import ccrs.jacamo.CcrsJacamoRuntime;
import jason.asSemantics.Agent;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Term;
import jason.asSyntax.VarTerm;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EvaluateAuditTest {
    @Test
    void actualInternalActionSharesIdentityAndLogsExactlyItsReturnedTerms() throws Exception {
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) { messages.add(record.getMessage()); }
            public void flush() { }
            public void close() { }
        };
        Logger adapter = Logger.getLogger(evaluate.class.getName());
        Logger core = Logger.getLogger(ContingencyCcrs.class.getName());
        adapter.addHandler(handler);
        core.addHandler(handler);
        try {
            StrategyRegistry registry = new StrategyRegistry();
            registry.register(new RetryStrategy()); // Produces advice; executes no request or delay.
            CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> new ContingencyCcrs(registry,
                ContingencyConfiguration.builder().learnedSelection(false).build()));
            Agent syntax = new Agent();
            syntax.setConsiderToAddMIForThisAgent(false);
            syntax.initAg(); // Local terms/TS only: no launcher, reasoning cycle or environment.
            Unifier binding = new Unifier();
            assertEquals(true, new evaluate().execute(syntax.getTS(), binding, new Term[]{
                ASSyntax.parseTerm("map(trigger(http_error),current(\"urn:cell:1\"),target(\"urn:cell:2\"),action(\"GET\"),http_status(\"503\"))"),
                new VarTerm("Results")
            }));
            assertFalse(((ListTerm) binding.get("Results")).isEmpty());
            String request = event(messages, "ccrs.contingency.evaluate.request");
            String returned = event(messages, "ccrs.contingency.evaluate.returned");
            String strategy = event(messages, "ccrs.contingency.strategy.evaluated");
            String identity = request.split("invocation_id=", 2)[1].split(" ", 2)[0];
            UUID.fromString(identity);
            for (String row : List.of(request, returned, strategy)) {
                assertTrue(row.contains("invocation_id=" + identity));
                Instant.parse(row.split("timestamp_utc=", 2)[1].split(" ", 2)[0]);
            }
            String expectedField = CcrsEventLogger.format("fixture",
                CcrsEventLogger.fields("selected_results", binding.get("Results").toString()))
                .split(" selected_results=", 2)[1];
            assertTrue(returned.contains("selected_results=" + expectedField));
        } finally {
            adapter.removeHandler(handler);
            core.removeHandler(handler);
            CcrsJacamoRuntime.reset();
        }
    }

    private static String event(List<String> messages, String name) {
        return messages.stream().filter(m -> m.contains("event=" + name + " ")).findFirst().orElseThrow();
    }
}
