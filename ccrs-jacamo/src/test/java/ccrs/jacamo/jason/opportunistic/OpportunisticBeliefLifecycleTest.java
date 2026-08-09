package ccrs.jacamo.jason.opportunistic;

import ccrs.core.opportunistic.OpportunisticResult;
import ccrs.core.rdf.RdfTriple;
import ccrs.jacamo.jason.JasonRdfAdapter;
import jason.asSyntax.Literal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunisticBeliefLifecycleTest {

    @Test
    void sourceReplacementSelectsOnlyItsArchitectureBeliefs() {
        Literal s1 = belief("S1", OpportunisticBeliefLifecycle.ARTIFACT_BATCH_PRODUCER, null);
        Literal s2 = belief("S2", OpportunisticBeliefLifecycle.ARTIFACT_BATCH_PRODUCER, null);
        Literal contingency = JasonRdfAdapter.createCcrsBelief(
            new OpportunisticResult("retry", "target", "contingency", 0.8)
                .withMetadata("origin", "contingency-ccrs"),
            "S1");

        assertTrue(OpportunisticBeliefLifecycle.isOwnedBy(
            s1, OpportunisticBeliefLifecycle.ARTIFACT_BATCH_PRODUCER, "S1", null));
        assertFalse(OpportunisticBeliefLifecycle.isOwnedBy(
            s2, OpportunisticBeliefLifecycle.ARTIFACT_BATCH_PRODUCER, "S1", null));
        assertFalse(OpportunisticBeliefLifecycle.isOwnedBy(
            contingency, OpportunisticBeliefLifecycle.ARTIFACT_BATCH_PRODUCER, "S1", null));
    }

    @Test
    void singlePerceptEvidenceMatchesOnlyItsOriginalPercept() {
        RdfTriple first = new RdfTriple("subject", "predicate", "first");
        RdfTriple second = new RdfTriple("subject", "predicate", "second");
        String firstId = OpportunisticBeliefLifecycle.evidenceId("S1", first);
        String secondId = OpportunisticBeliefLifecycle.evidenceId("S1", second);
        Literal firstBelief = belief(
            "S1", OpportunisticBeliefLifecycle.SINGLE_PERCEPT_PRODUCER, firstId);

        assertTrue(OpportunisticBeliefLifecycle.isOwnedBy(
            firstBelief,
            OpportunisticBeliefLifecycle.SINGLE_PERCEPT_PRODUCER,
            "S1",
            firstId));
        assertFalse(OpportunisticBeliefLifecycle.isOwnedBy(
            firstBelief,
            OpportunisticBeliefLifecycle.SINGLE_PERCEPT_PRODUCER,
            "S1",
            secondId));
        assertFalse(firstId.equals(secondId));
    }

    private static Literal belief(String source, String producer, String evidenceId) {
        OpportunisticResult result = OpportunisticBeliefLifecycle.annotate(
            new OpportunisticResult("signifier", "target", "pattern", 0.9),
            source,
            producer,
            evidenceId);
        return JasonRdfAdapter.createCcrsBelief(result, source);
    }
}
