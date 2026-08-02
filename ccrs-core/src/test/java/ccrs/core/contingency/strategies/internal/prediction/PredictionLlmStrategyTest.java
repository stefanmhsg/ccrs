package ccrs.core.contingency.strategies.internal.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.LlmClient;
import ccrs.core.contingency.LlmResponseParser;
import ccrs.core.contingency.PromptBuilder;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Interaction;
import ccrs.core.contingency.dto.LlmActionResponse;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.PredictionLlmStrategyOptions;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class PredictionLlmStrategyTest {

    private static final String CURRENT = "https://example.test/cells/1";
    private static final String TARGET = "https://example.test/cells/2";

    @Test
    void exposesStableStrategyMetadata() {
        PredictionLlmStrategy strategy = new PredictionLlmStrategy(prompt -> "{}");

        assertEquals("prediction_llm", strategy.getId());
        assertEquals("Prediction (LLM)", strategy.getName());
        assertEquals(CcrsStrategy.Category.INTERNAL, strategy.getCategory());
        assertEquals(4, strategy.getEscalationLevel());
        assertEquals("Prediction (LLM) (L4)", strategy.getDescription());
        assertTrue(strategy.isEnabled());
    }

    @Test
    void applicabilityRequiresLlmAccessAndCurrentResource() {
        Situation located = Situation.builder().trigger("blocked").currentResource(CURRENT).build();
        Situation unlocated = Situation.builder().trigger("blocked").build();
        TestContext noAccess = new TestContext();
        TestContext contextAccess = new TestContext();
        contextAccess.llmAccess = true;

        assertNotApplicable(new PredictionLlmStrategy(null), located, noAccess);
        assertNotApplicable(new PredictionLlmStrategy(prompt -> "{}"), unlocated, noAccess);
        assertApplicable(new PredictionLlmStrategy(prompt -> "{}"), located, noAccess);
        assertApplicable(new PredictionLlmStrategy(null), located, contextAccess);

        noAccess.currentResource = CURRENT;
        assertApplicable(new PredictionLlmStrategy(prompt -> "{}"), unlocated, noAccess);
    }

    @Test
    void evaluationReportsMissingClientOrParser() {
        Situation situation = Situation.builder().trigger("blocked").currentResource(CURRENT).build();
        RecordingPromptBuilder prompts = new RecordingPromptBuilder();

        assertNoHelp(new PredictionLlmStrategy(null).evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.PRECONDITION_MISSING, "LLM client not configured");
        assertNoHelp(new PredictionLlmStrategy(prompt -> "{}", prompts, null)
                .evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.PRECONDITION_MISSING, "Response parser not configured");
    }

    @Test
    void validResponseProducesCompleteHttpSuggestion() {
        RecordingLlmClient client = new RecordingLlmClient("raw model response");
        LlmActionResponse parsed = LlmActionResponse.valid("post", TARGET, "Create replacement")
            .withConfidence(0.91)
            .withMethod("POST")
            .withHeaders(Map.of("Accept", "application/json"))
            .withBody("{\"value\":1}")
            .withBodyContentType("application/json")
            .withMetadata("parseMethod", "json");
        RecordingPromptBuilder prompts = new RecordingPromptBuilder();

        StrategyResult.Suggestion suggestion = new PredictionLlmStrategy(
            client, prompts, raw -> parsed).evaluate(
                Situation.builder().trigger("failed").currentResource(CURRENT).build(), new TestContext())
            .asSuggestion();

        assertEquals(PredictionLlmStrategy.ID, suggestion.getStrategyId());
        assertEquals("post", suggestion.getActionType());
        assertEquals(TARGET, suggestion.getActionTarget());
        assertEquals(true, suggestion.getActionParam("llmGenerated"));
        assertEquals("Create replacement", suggestion.getActionParam("originalReasoning"));
        assertEquals("POST", suggestion.getActionParam("method"));
        assertEquals(Map.of("Accept", "application/json"), suggestion.getActionParam("headers"));
        assertEquals("{\"value\":1}", suggestion.getActionParam("body"));
        assertEquals("application/json", suggestion.getActionParam("bodyContentType"));
        assertEquals("json", suggestion.getActionParam("parseMethod"));
        assertEquals(0.91, suggestion.getConfidence(), 0.000_001);
        assertEquals("prepared prompt", client.prompt);
        assertTrue(suggestion.getRationale().contains("LLM suggests: post to " + TARGET));
    }

    @Test
    void missingConfidenceUsesConfiguredBaseConfidence() {
        PredictionLlmStrategyOptions options = PredictionLlmStrategyOptions.builder()
            .baseConfidence(0.37).build();
        LlmActionResponse parsed = LlmActionResponse.valid("wait", null, "Allow recovery");

        StrategyResult.Suggestion suggestion = strategyReturning(parsed, options)
            .evaluate(Situation.builder().trigger("blocked").currentResource(CURRENT).build(), new TestContext())
            .asSuggestion();

        assertEquals(0.37, suggestion.getConfidence(), 0.000_001);
    }

    @Test
    void invalidAndExplicitNoSuggestionResponsesMapToNoHelp() {
        Situation situation = Situation.builder().trigger("blocked").currentResource(CURRENT).build();

        assertNoHelp(strategyReturning(LlmActionResponse.invalid("missing action"), null)
                .evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.EVALUATION_FAILED,
            "Could not parse valid action from LLM: missing action");
        assertNoHelp(strategyReturning(LlmActionResponse.noSuggestion("No safe move"), null)
                .evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT, "No safe move");
        assertNoHelp(strategyReturning(LlmActionResponse.noSuggestion(null), null)
                .evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT,
            "LLM explicitly returned no recovery suggestion");
    }

    @Test
    void componentExceptionsBecomeEvaluationFailures() {
        Situation situation = Situation.builder().trigger("blocked").currentResource(CURRENT).build();
        PredictionLlmStrategy clientFailure = new PredictionLlmStrategy(
            prompt -> { throw new Exception("model offline"); },
            context -> "prompt",
            raw -> LlmActionResponse.valid("wait", null, "wait"));
        PredictionLlmStrategy parserFailure = new PredictionLlmStrategy(
            prompt -> "raw",
            context -> "prompt",
            raw -> { throw new IllegalArgumentException("bad parser state"); });

        assertNoHelp(clientFailure.evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.EVALUATION_FAILED, "LLM call failed: model offline");
        assertNoHelp(parserFailure.evaluate(situation, new TestContext()),
            StrategyResult.NoHelpReason.EVALUATION_FAILED, "LLM call failed: bad parser state");
    }

    @Test
    void preparesBoundedAndFilteredPromptContext() {
        String visible = "https://example.test/vocab#visible";
        String filtered = "https://example.org/ui#color";
        TestContext context = new TestContext();
        context.currentResource = CURRENT;
        context.historyAvailable = true;
        context.interactions = List.of(
            interaction(List.of(
                new RdfTriple(CURRENT, visible, TARGET),
                new RdfTriple(CURRENT, filtered, "red"))),
            interaction(List.of(new RdfTriple(CURRENT, visible, "older"))));
        context.traces = List.of(CcrsTrace.builder(Situation.builder().trigger("old").build()).build());
        context.neighborhood = new CcrsContext.Neighborhood(CURRENT,
            List.of(new RdfTriple(CURRENT, visible, TARGET), new RdfTriple(CURRENT, filtered, "blue")),
            List.of(new RdfTriple("incoming", visible, CURRENT)));
        RecordingPromptBuilder prompts = new RecordingPromptBuilder();
        PredictionLlmStrategyOptions options = PredictionLlmStrategyOptions.builder()
            .maxHistoryActions(1).maxInteractionStateTriples(1).maxCcrsTraces(1)
            .maxNeighborhood(2, 1).filteredTripleNamespaces(List.of("https://example.org/ui"))
            .build();
        PredictionLlmStrategy strategy = new PredictionLlmStrategy(
            prompt -> "raw", prompts,
            raw -> LlmActionResponse.valid("navigate", TARGET, "go"), options);

        strategy.evaluate(Situation.builder().trigger("failed").failedAction("GET")
            .targetResource(TARGET).httpError(503, "Unavailable").build(), context);

        assertEquals(1, context.requestedInteractionLimit);
        assertEquals(1, context.requestedTraceLimit);
        assertEquals(2, context.requestedOutgoingLimit);
        assertEquals(1, context.requestedIncomingLimit);
        assertEquals(CURRENT, prompts.context.get("currentResource"));
        String recent = (String) prompts.context.get("recentActions");
        String neighborhood = (String) prompts.context.get("localNeighborhood");
        assertTrue(recent.contains(visible));
        assertFalse(recent.contains(filtered));
        assertTrue(neighborhood.contains(visible));
        assertFalse(neighborhood.contains(filtered));
        String situationDetails = (String) prompts.context.get("situationDetails");
        assertTrue(situationDetails.contains("Trigger: failed"));
        assertTrue(situationDetails.contains("Requested or failed action: GET"));
        assertFalse(situationDetails.contains("Situation type"));
    }

    @Test
    void rationaleTruncatesLongModelExplanation() {
        String explanation = "x".repeat(250);
        StrategyResult.Suggestion suggestion = strategyReturning(
            LlmActionResponse.valid("wait", null, explanation), null)
            .evaluate(Situation.builder().trigger("blocked").currentResource(CURRENT).build(), new TestContext())
            .asSuggestion();

        assertTrue(suggestion.getRationale().endsWith("..."));
        assertEquals(250, suggestion.<String>getActionParam("originalReasoning").length());
    }

    @Test
    void configuredPlainTextFallbackControlsDefaultParser() {
        String plainResponse = "Navigate to https://example.test/cells/9 to recover.";
        Situation situation = Situation.builder().trigger("blocked").currentResource(CURRENT).build();

        StrategyResult enabled = new PredictionLlmStrategy(
            prompt -> plainResponse,
            PredictionLlmStrategyOptions.builder().plainTextFallbackEnabled(true).build())
            .evaluate(situation, new TestContext());
        StrategyResult disabled = new PredictionLlmStrategy(
            prompt -> plainResponse,
            PredictionLlmStrategyOptions.builder().plainTextFallbackEnabled(false).build())
            .evaluate(situation, new TestContext());

        assertTrue(enabled.isSuggestion(), enabled::toDetailedReport);
        assertEquals("navigate", enabled.asSuggestion().getActionType());
        assertEquals("https://example.test/cells/9", enabled.asSuggestion().getActionTarget());
        assertEquals(0.3, enabled.asSuggestion().getConfidence(), 0.000_001);
        assertFalse(disabled.isSuggestion(), disabled::toDetailedReport);
        assertEquals(StrategyResult.NoHelpReason.EVALUATION_FAILED, disabled.asNoHelp().getReason());
        assertTrue(disabled.asNoHelp().getExplanation().startsWith(
            "Could not parse valid action from LLM:"));
    }

    @Test
    void optionBoundsNormalizationAndNullOptionsAreHandled() {
        PredictionLlmStrategyOptions options = PredictionLlmStrategyOptions.builder()
            .maxHistoryActions(-1).maxInteractionStateTriples(-1).maxCcrsTraces(-1)
            .maxNeighborhood(-1, -1).baseConfidence(2.0)
            .filteredTripleNamespaces(List.of("x", "", "x")).build();

        assertEquals(0, options.getMaxHistoryActions());
        assertEquals(0, options.getMaxInteractionStateTriples());
        assertEquals(0, options.getMaxCcrsTraces());
        assertEquals(0, options.getMaxNeighborhoodOutgoing());
        assertEquals(0, options.getMaxNeighborhoodIncoming());
        assertEquals(1.0, options.getBaseConfidence(), 0.0);
        assertEquals(List.of("x"), options.getFilteredTripleNamespaces());

        assertApplicable(new PredictionLlmStrategy(prompt -> "{}", null),
            Situation.builder().trigger("blocked").currentResource(CURRENT).build(), new TestContext());
    }

    private static PredictionLlmStrategy strategyReturning(
            LlmActionResponse response, PredictionLlmStrategyOptions options) {
        return new PredictionLlmStrategy(
            prompt -> "raw", context -> "prompt", raw -> response, options);
    }

    private static Interaction interaction(List<RdfTriple> state) {
        return new Interaction("GET", CURRENT, Map.of("Accept", "text/turtle"), null,
            Interaction.Outcome.SUCCESS, state, 10L, 20L, "test");
    }

    private static void assertApplicable(
            PredictionLlmStrategy strategy, Situation situation, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNotApplicable(
            PredictionLlmStrategy strategy, Situation situation, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.NOT_APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNoHelp(
            StrategyResult result, StrategyResult.NoHelpReason reason, String explanation) {
        assertFalse(result.isSuggestion(), result::toDetailedReport);
        assertEquals(reason, result.asNoHelp().getReason());
        assertEquals(explanation, result.asNoHelp().getExplanation());
    }

    private static final class RecordingLlmClient implements LlmClient {
        private final String response;
        private String prompt;

        private RecordingLlmClient(String response) { this.response = response; }

        @Override
        public String complete(String prompt) {
            this.prompt = prompt;
            return response;
        }
    }

    private static final class RecordingPromptBuilder implements PromptBuilder {
        private Map<String, Object> context;

        @Override
        public String buildPredictionPrompt(Map<String, Object> context) {
            this.context = context;
            return "prepared prompt";
        }
    }

    private static final class TestContext implements CcrsContext {
        private String currentResource;
        private boolean llmAccess;
        private boolean historyAvailable;
        private List<Interaction> interactions = List.of();
        private List<CcrsTrace> traces = List.of();
        private CcrsContext.Neighborhood neighborhood = new CcrsContext.Neighborhood(null, List.of(), List.of());
        private int requestedInteractionLimit = -1;
        private int requestedTraceLimit = -1;
        private int requestedOutgoingLimit = -1;
        private int requestedIncomingLimit = -1;

        @Override public List<RdfTriple> query(String subject, String predicate, String object) { return List.of(); }
        @Override public boolean contains(RdfTriple triple) { return false; }

        @Override
        public List<Interaction> getRecentInteractions(int maxCount) {
            requestedInteractionLimit = maxCount;
            return new ArrayList<>(interactions.subList(0, Math.min(maxCount, interactions.size())));
        }

        @Override
        public CcrsContext.Neighborhood getNeighborhood(String resource, int maxOutgoing, int maxIncoming) {
            requestedOutgoingLimit = maxOutgoing;
            requestedIncomingLimit = maxIncoming;
            return neighborhood;
        }

        @Override public Optional<String> getCurrentResource() { return Optional.ofNullable(currentResource); }
        @Override public String getAgentId() { return "test-agent"; }
        @Override public Optional<CcrsTrace> getLastCcrsInvocation() { return traces.stream().findFirst(); }

        @Override
        public List<CcrsTrace> getCcrsHistory(int maxCount) {
            requestedTraceLimit = maxCount;
            return traces.subList(0, Math.min(maxCount, traces.size()));
        }

        @Override public void recordCcrsInvocation(CcrsTrace trace) { }
        @Override public boolean hasHistory() { return historyAvailable; }
        @Override public boolean hasLlmAccess() { return llmAccess; }
    }
}
