package ccrs.jacamo.jaca;

import ccrs.core.rdf.RdfTriple;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpportunisticSourceStateTest {

    @Test
    void refreshingOneSourceKeepsTheOtherSourcesCompleteSnapshot() {
        OpportunisticSourceState state = new OpportunisticSourceState();
        RdfTriple s1Old = new RdfTriple("s1", "p", "old");
        RdfTriple s1New = new RdfTriple("s1", "p", "new");
        RdfTriple s2 = new RdfTriple("s2", "p", "value");

        state.add("S1", s1Old);
        state.add("S2", s2);
        state.drainDirtySnapshots();

        state.remove("S1", s1Old);
        state.add("S1", s1New);
        Map<String, List<RdfTriple>> refresh = state.drainDirtySnapshots();

        assertEquals(Map.of("S1", List.of(s1New)), refresh);
        assertFalse(refresh.containsKey("S2"));
        assertEquals(List.of(s2), state.currentSnapshot("S2"));
    }

    @Test
    void removingLastTripleProducesAnEmptySnapshotForThatSourceOnly() {
        OpportunisticSourceState state = new OpportunisticSourceState();
        RdfTriple s1 = new RdfTriple("s1", "p", "value");
        RdfTriple s2 = new RdfTriple("s2", "p", "value");

        state.add("S1", s1);
        state.add("S2", s2);
        state.drainDirtySnapshots();
        state.remove("S1", s1);

        assertEquals(Map.of("S1", List.of()), state.drainDirtySnapshots());
        assertEquals(List.of(s2), state.currentSnapshot("S2"));
    }
}
