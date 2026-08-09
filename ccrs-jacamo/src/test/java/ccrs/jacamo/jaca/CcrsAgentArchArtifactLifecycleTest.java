package ccrs.jacamo.jaca;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactObsProperty;
import cartago.WorkspaceId;
import ccrs.core.opportunistic.OpportunisticResult;
import ccrs.jacamo.jason.opportunistic.OpportunisticBeliefLifecycle;
import jason.asSemantics.Agent;
import jason.asSemantics.Circumstance;
import jason.asSemantics.Event;
import jason.asSemantics.TransitionSystem;
import jason.asSyntax.Literal;
import jason.asSyntax.PredicateIndicator;
import jason.asSyntax.Trigger;
import jason.bb.DefaultBeliefBase;
import jason.runtime.Settings;
import jaca.CartagoEnvironment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CcrsAgentArchArtifactLifecycleTest {

    @Test
    @SuppressWarnings("deprecation") // Jason's test-friendly initAg overload is deprecated.
    void refreshingOneArtifactReplacesOnlyItsOpportunisticCcrsBeliefs() throws Exception {
        CcrsAgentArch architecture = new CcrsAgentArch();
        Agent agent = new Agent();
        TransitionSystem transitionSystem = agent.initAg(
            architecture, new DefaultBeliefBase(), null, new Settings());
        CartagoEnvironment environment = new CartagoEnvironment();
        environment.init(new String[0]);
        architecture.init();
        AtomicInteger scannerCalls = new AtomicInteger();
        architecture.setCcrsScanner((triple, context) -> {
            scannerCalls.incrementAndGet();
            return java.util.Optional.of(
                new OpportunisticResult("follow", triple.object, "test-pattern", 0.9));
        });

        ArtifactId sourceA = artifact("artifact-A");
        ArtifactId sourceB = artifact("artifact-B");
        jason.asSyntax.Atom namespace = null;
        ArtifactObsProperty aOld = rdfProperty(1L, "a-old");
        ArtifactObsProperty b = rdfProperty(2L, "b");

        architecture.addObsPropertiesBel(sourceA, aOld, namespace);
        architecture.addObsPropertiesBel(sourceB, b, namespace);
        flush(architecture);

        assertEquals(2, scannerCalls.get());
        assertEquals(1, opportunisticBeliefCount(agent, "artifact-A"));
        assertEquals(1, opportunisticBeliefCount(agent, "artifact-B"));
        transitionSystem.getC().clearEvents();

        architecture.removeObsPropertiesBel(sourceA, aOld, namespace);
        architecture.addObsPropertiesBel(sourceA, rdfProperty(3L, "a-new"), namespace);
        flush(architecture);

        assertEquals(1, opportunisticBeliefCount(agent, "artifact-A"));
        assertEquals(1, opportunisticBeliefCount(agent, "artifact-B"));
        assertEquals(1, ccrsDeletionCount(transitionSystem.getC()));
        environment.stop();
    }

    private static ArtifactObsProperty rdfProperty(long id, String object) {
        return new ArtifactObsProperty("test-property-" + id, id, "rdf", "subject", "predicate", object);
    }

    private static ArtifactId artifact(String name) throws Exception {
        WorkspaceId workspace = new WorkspaceId("main");
        AgentId creator = new AgentId("creator", "creator", 1, "creator", workspace);
        return new ArtifactId(name, UUID.randomUUID(), "test-artifact", workspace, creator);
    }

    private static void flush(CcrsAgentArch architecture) throws Exception {
        Method flush = CcrsAgentArch.class.getDeclaredMethod("flushBatches");
        flush.setAccessible(true);
        flush.invoke(architecture);
    }

    private static int opportunisticBeliefCount(Agent agent, String source) {
        Iterator<Literal> candidates = agent.getBB().getCandidateBeliefs(
            new PredicateIndicator("ccrs", 3));
        int count = 0;
        while (candidates != null && candidates.hasNext()) {
            Literal belief = candidates.next();
            if (OpportunisticBeliefLifecycle.isOwnedBy(
                    belief,
                    OpportunisticBeliefLifecycle.ARTIFACT_BATCH_PRODUCER,
                    source,
                    null)) {
                count++;
            }
        }
        return count;
    }

    private static int ccrsDeletionCount(Circumstance circumstance) {
        int count = 0;
        for (Event event : circumstance.getEvents()) {
            Literal literal = event.getTrigger().getLiteral();
            if (literal != null && event.getTrigger().getOperator() == Trigger.TEOperator.del
                    && "ccrs".equals(literal.getFunctor())) {
                count++;
            }
        }
        return count;
    }
}
