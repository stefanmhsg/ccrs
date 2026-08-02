package ccrs.core.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.dto.Situation;

class SituationTest {

    @Test
    void buildsTypeFreeRequestFromConcreteObservations() {
        Situation situation = Situation.builder()
            .trigger("unexpected response")
            .currentResource("https://example.test/current")
            .targetResource("https://example.test/target")
            .failedAction("GET")
            .errorInfo("httpStatus", "503")
            .metadata("agent", "test-agent")
            .build();

        assertEquals("unexpected response", situation.getTrigger());
        assertEquals("https://example.test/current", situation.getCurrentResource());
        assertEquals("https://example.test/target", situation.getTargetResource());
        assertEquals("GET", situation.getFailedAction());
        assertEquals(Map.of("httpStatus", "503"), situation.getErrorInfo());
        assertEquals(Map.of("agent", "test-agent"), situation.getMetadata());
        assertTrue(situation.toString().contains("trigger='unexpected response'"));
        assertFalse(situation.toString().contains("type="));
    }

    @Test
    void emptyRequestIsValidAndNoTypeApiRemains() {
        Situation situation = Situation.builder().build();

        assertEquals("Situation{}", situation.toString());
        assertThrows(NoSuchMethodException.class, () -> Situation.class.getMethod("getType"));
        assertFalse(Arrays.stream(Situation.class.getDeclaredClasses())
            .anyMatch(type -> "Type".equals(type.getSimpleName())));
    }
}
