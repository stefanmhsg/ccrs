package ccrs.jacamo.jason.opportunistic;

import ccrs.core.opportunistic.OpportunisticResult;
import ccrs.core.rdf.RdfTriple;
import ccrs.jacamo.jason.JasonRdfAdapter;
import jason.asSyntax.Literal;
import jason.asSyntax.Structure;
import jason.asSyntax.Term;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Lifecycle metadata and matching for transient opportunistic beliefs. */
public final class OpportunisticBeliefLifecycle {

    public static final String ORIGIN = "opportunistic-ccrs";
    public static final String SINGLE_PERCEPT_PRODUCER = "single-percept";
    public static final String ARTIFACT_BATCH_PRODUCER = "artifact-batch";

    private OpportunisticBeliefLifecycle() {
    }

    /**
     * Adds the lifecycle keys used to refresh a derived belief.
     *
     * @param result scanner result to annotate
     * @param source logical source identifier
     * @param producer producer path identifier
     * @param evidenceId stable evidence identifier, or {@code null} for a source snapshot
     * @return the supplied result
     */
    public static OpportunisticResult annotate(
            OpportunisticResult result,
            String source,
            String producer,
            String evidenceId) {
        result.withMetadata("origin", ORIGIN)
            .withMetadata("source", source)
            .withMetadata("producer", producer);
        if (evidenceId != null) {
            result.withMetadata("evidence_id", evidenceId);
        }
        return result;
    }

    /** Returns a stable identifier for one source/percept evidence tuple. */
    public static String evidenceId(String source, RdfTriple triple) {
        String canonical = framed(source)
            + framed(triple.subject)
            + framed(triple.predicate)
            + framed(triple.object);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    /**
     * Tests whether a belief belongs to the requested transient lifecycle owner.
     * A {@code null} evidence identifier matches every belief for that producer/source.
     */
    public static boolean isOwnedBy(
            Literal belief,
            String producer,
            String source,
            String evidenceId) {
        return "ccrs".equals(belief.getFunctor())
            && belief.getArity() == 3
            && ORIGIN.equals(annotationValue(belief, "origin"))
            && producer.equals(annotationValue(belief, "producer"))
            && source.equals(annotationValue(belief, "source"))
            && (evidenceId == null || evidenceId.equals(annotationValue(belief, "evidence_id")));
    }

    private static String annotationValue(Literal belief, String name) {
        Term annotation = belief.getAnnot(name);
        if (annotation instanceof Structure structure && structure.getArity() > 0) {
            return JasonRdfAdapter.termToString(structure.getTerm(0));
        }
        return null;
    }

    private static String framed(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe;
    }
}
