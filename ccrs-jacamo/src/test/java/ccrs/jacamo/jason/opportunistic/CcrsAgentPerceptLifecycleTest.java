package ccrs.jacamo.jason.opportunistic;

import ccrs.core.opportunistic.OpportunisticResult;
import jason.architecture.AgArch;
import jason.asSemantics.Circumstance;
import jason.asSemantics.Event;
import jason.asSemantics.TransitionSystem;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.PredicateIndicator;
import jason.bb.DefaultBeliefBase;
import jason.runtime.Settings;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CcrsAgentPerceptLifecycleTest {

    @Test
    @SuppressWarnings("deprecation") // Jason's test-friendly initAg overload is deprecated.
    void removingPerceptRemovesItsDerivedBeliefAndEmitsDeletionEvent() throws Exception {
        CcrsAgent agent = new CcrsAgent();
        AgArch architecture = new AgArch();
        TransitionSystem transitionSystem = agent.initAg(
            architecture, new DefaultBeliefBase(), null, new Settings());
        agent.setCcrsScanner((triple, context) -> java.util.Optional.of(
            new OpportunisticResult("signifier", triple.object, "test-pattern", 0.9)));
        Literal percept = ASSyntax.createLiteral(
            "rdf",
            ASSyntax.createString("subject"),
            ASSyntax.createString("predicate"),
            ASSyntax.createString("object"));
        percept.addAnnot(ASSyntax.createStructure("source", ASSyntax.createString("S1")));

        agent.buf(List.of(percept));
        assertEquals(1, ccrsBeliefCount(agent));
        transitionSystem.getC().clearEvents();

        int changes = agent.buf(List.of());

        assertEquals(0, ccrsBeliefCount(agent));
        assertTrue(changes >= 2, "the percept and its derivation are both removed");
        assertTrue(hasCcrsDeletion(transitionSystem.getC()));
    }

    private static int ccrsBeliefCount(CcrsAgent agent) {
        Iterator<Literal> beliefs = agent.getBB().getCandidateBeliefs(
            new PredicateIndicator("ccrs", 3));
        int count = 0;
        while (beliefs != null && beliefs.hasNext()) {
            beliefs.next();
            count++;
        }
        return count;
    }

    private static boolean hasCcrsDeletion(Circumstance circumstance) {
        for (Event event : circumstance.getEvents()) {
            String trigger = event.getTrigger().toString();
            if (trigger.startsWith("-ccrs(")) {
                return true;
            }
        }
        return false;
    }
}
