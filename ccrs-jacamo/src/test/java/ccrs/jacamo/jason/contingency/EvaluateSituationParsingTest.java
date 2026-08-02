package ccrs.jacamo.jason.contingency;

import ccrs.core.contingency.dto.Situation;
import jason.JasonException;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Term;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluateSituationParsingTest {

    private final evaluate action = new evaluate();

    @Test
    void parsesAllSupportedEvidence() throws Exception {
        Situation situation = parse("""
            map(
                trigger("http_error"),
                current("urn:cell:1"),
                target("urn:cell:2"),
                action("GET"),
                http_status("503"),
                error_type("SERVER_FAILURE"),
                error_message("Service Unavailable"),
                metadata("agent_role", "navigator")
            )
            """);

        assertEquals("http_error", situation.getTrigger());
        assertEquals("urn:cell:1", situation.getCurrentResource());
        assertEquals("urn:cell:2", situation.getTargetResource());
        assertEquals("GET", situation.getFailedAction());
        assertEquals("503", situation.getErrorInfoString("httpStatus"));
        assertEquals("SERVER_FAILURE", situation.getErrorInfoString("errorType"));
        assertEquals("Service Unavailable", situation.getErrorInfoString("message"));
        assertEquals("navigator", situation.getMetadata("agent_role"));
    }

    @Test
    void mapsNumericErrorToHttpStatusAndGeneratedMessage() throws Exception {
        Situation situation = parse("map(error(503))");

        assertEquals("503", situation.getErrorInfoString("httpStatus"));
        assertEquals("HTTP 503", situation.getErrorInfoString("message"));
    }

    @Test
    void mapsNonNumericErrorToMessage() throws Exception {
        Situation situation = parse("map(error(timeout))");

        assertEquals("timeout", situation.getErrorInfoString("message"));
    }

    @Test
    void rejectsNonMapInput() throws Exception {
        Term input = ASSyntax.parseTerm("trigger(http_error)");

        assertThrows(JasonException.class, () -> action.parseSituation(input));
    }

    @Test
    void rejectsNonStructureEntries() {
        assertThrows(JasonException.class, () -> parse("map(http_error)"));
    }

    @Test
    void rejectsUnknownEntries() {
        assertThrows(JasonException.class, () -> parse("map(category(failure))"));
    }

    @Test
    void rejectsMissingAndExtraSingleEntryValues() {
        assertThrows(JasonException.class, () -> parse("map(trigger)"));
        assertThrows(JasonException.class, () -> parse("map(trigger(a, b))"));
    }

    @Test
    void requiresExactlyTwoMetadataValues() {
        assertThrows(JasonException.class, () -> parse("map(metadata(key))"));
        assertThrows(JasonException.class, () -> parse("map(metadata(key, value, extra))"));
    }

    private Situation parse(String source) throws Exception {
        return action.parseSituation(ASSyntax.parseTerm(source));
    }
}
