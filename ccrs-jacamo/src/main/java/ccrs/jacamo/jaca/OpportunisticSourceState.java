package ccrs.jacamo.jaca;

import ccrs.core.rdf.RdfTriple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-architecture current RDF state, partitioned by logical source. */
final class OpportunisticSourceState {

    private final Map<String, Set<RdfTriple>> triplesBySource = new LinkedHashMap<>();
    private final Set<String> dirtySources = new LinkedHashSet<>();

    synchronized void add(String source, RdfTriple triple) {
        triplesBySource.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(triple);
        dirtySources.add(source);
    }

    synchronized void remove(String source, RdfTriple triple) {
        Set<RdfTriple> triples = triplesBySource.get(source);
        if (triples != null) {
            triples.remove(triple);
            if (triples.isEmpty()) {
                triplesBySource.remove(source);
            }
        }
        dirtySources.add(source);
    }

    synchronized Map<String, List<RdfTriple>> drainDirtySnapshots() {
        Map<String, List<RdfTriple>> snapshots = new LinkedHashMap<>();
        for (String source : dirtySources) {
            snapshots.put(source, new ArrayList<>(
                triplesBySource.getOrDefault(source, Set.of())));
        }
        dirtySources.clear();
        return snapshots;
    }

    synchronized List<RdfTriple> currentSnapshot(String source) {
        return new ArrayList<>(triplesBySource.getOrDefault(source, Set.of()));
    }
}
