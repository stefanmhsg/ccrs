package ccrs.core.contingency.strategies.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Interaction;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.ConsultationStrategyOptions;
import ccrs.core.contingency.strategies.social.ConsultationStrategy.ConsultationResponse;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class ConsultationStrategyTest {

    private static final String CONTAINS = "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#contains";
    private static final String AGENT_CARD = "https://example.org/a2a#agentCard";
    private static final String PROVIDES_TYPE = "https://example.org/a2a#providesType";
    private static final String CURRENT = "https://example.test/cells/1";
    private static final String PEER = "https://example.test/agents/helper";

    @Test
    void exposesStableStrategyMetadata() {
        ConsultationStrategy strategy = new ConsultationStrategy();

        assertEquals("consultation", strategy.getId());
        assertEquals("Consultation (Social)", strategy.getName());
        assertEquals(CcrsStrategy.Category.SOCIAL, strategy.getCategory());
        assertEquals(3, strategy.getEscalationLevel());
        assertEquals("Consultation (Social) (L3)", strategy.getDescription());
        assertTrue(strategy.isEnabled());
    }

    @Test
    void applicabilityRequiresAvailableChannelHistoryAndDiscoveredPeer() {
        Situation situation = Situation.builder().trigger("blocked").currentResource(CURRENT).build();
        TestContext usable = contextWithPeer();

        assertNotApplicable(new ConsultationStrategy(), situation, usable);
        assertNotApplicable(new ConsultationStrategy(new RecordingChannel(false)), situation, usable);
        assertNotApplicable(new ConsultationStrategy(new RecordingChannel(true)), situation,
            new TestContext(false, List.of(), List.of(), List.of(), "self"));
        assertNotApplicable(new ConsultationStrategy(new RecordingChannel(true)), situation,
            new TestContext(true, List.of(interaction(List.of())), List.of(), List.of(), "self"));
        assertApplicable(new ConsultationStrategy(new RecordingChannel(true)), situation, usable);
    }

    @Test
    void evaluateReportsMissingOrUnavailableChannel() {
        Situation situation = Situation.builder().trigger("blocked").build();

        assertNoHelp(new ConsultationStrategy().evaluate(situation, contextWithPeer()),
            StrategyResult.NoHelpReason.PRECONDITION_MISSING, "No consultation channel configured");
        assertNoHelp(new ConsultationStrategy(new RecordingChannel(false)).evaluate(situation, contextWithPeer()),
            StrategyResult.NoHelpReason.PRECONDITION_MISSING, "Consultation channel is not available");
    }

    @Test
    void mapsChannelFailureMissingActionAndExceptionToNoHelp() {
        Situation situation = Situation.builder().trigger("blocked").build();
        RecordingChannel failed = new RecordingChannel(true);
        failed.response = ConsultationResponse.failure("no expert available");
        RecordingChannel empty = new RecordingChannel(true);
        empty.response = ConsultationResponse.success("", null, "no action");
        RecordingChannel throwing = new RecordingChannel(true);
        throwing.failure = new Exception("channel offline");

        assertNoHelp(new ConsultationStrategy(failed).evaluate(situation, contextWithPeer()),
            StrategyResult.NoHelpReason.EVALUATION_FAILED, "Consultation failed: no expert available");
        assertNoHelp(new ConsultationStrategy(empty).evaluate(situation, contextWithPeer()),
            StrategyResult.NoHelpReason.INSUFFICIENT_CONTEXT, "Consultant could not provide actionable advice");
        assertNoHelp(new ConsultationStrategy(throwing).evaluate(situation, contextWithPeer()),
            StrategyResult.NoHelpReason.EVALUATION_FAILED, "Consultation error: channel offline");
    }

    @Test
    void successfulConsultationPreservesAdviceMetadataAndConfidence() {
        RecordingChannel channel = new RecordingChannel(true);
        channel.response = ConsultationResponse.success("navigate", "https://example.test/cells/2", "Take east exit");
        channel.response.confidence = 0.85;
        channel.response.source = "agent-7";
        channel.response.metadata = Map.of("requestId", "abc");

        StrategyResult.Suggestion suggestion = new ConsultationStrategy(channel)
            .evaluate(Situation.builder().trigger("blocked").currentResource(CURRENT).build(), contextWithPeer())
            .asSuggestion();

        assertEquals(ConsultationStrategy.ID, suggestion.getStrategyId());
        assertEquals("navigate", suggestion.getActionType());
        assertEquals("https://example.test/cells/2", suggestion.getActionTarget());
        assertEquals(true, suggestion.getActionParam("consulted"));
        assertEquals("test-channel:agent-7", suggestion.getActionParam("consultationSource"));
        assertEquals("Take east exit", suggestion.getActionParam("originalAdvice"));
        assertEquals("abc", suggestion.getActionParam("requestId"));
        assertEquals(0.85, suggestion.getConfidence(), 0.000_001);
        assertTrue(suggestion.getRationale().contains("External consultation via test-channel (agent-7)"));
    }

    @Test
    void usesConfiguredFallbackForMissingOrOutOfRangeConfidence() {
        ConsultationStrategyOptions options = ConsultationStrategyOptions.builder()
            .defaultConfidence(0.42).build();
        RecordingChannel channel = new RecordingChannel(true);
        channel.response = ConsultationResponse.success("wait", null, "Wait briefly");

        assertEquals(0.42, new ConsultationStrategy(channel, options)
            .evaluate(Situation.builder().trigger("blocked").build(), contextWithPeer())
            .asSuggestion().getConfidence(), 0.000_001);

        channel.response.confidence = 1.5;
        assertEquals(0.42, new ConsultationStrategy(channel, options)
            .evaluate(Situation.builder().trigger("blocked").build(), contextWithPeer())
            .asSuggestion().getConfidence(), 0.000_001);
    }

    @Test
    void buildsBoundedQuestionContextAndFiltersSelfCandidate() {
        String secondPeer = "https://example.test/agents/second";
        List<Interaction> interactions = List.of(
            interaction(List.of(
                new RdfTriple(CURRENT, CONTAINS, "https://example.test/agents/self"),
                new RdfTriple(CURRENT, CONTAINS, PEER),
                new RdfTriple(CURRENT, CONTAINS, secondPeer))),
            interaction(List.of(new RdfTriple(CURRENT, CONTAINS, "https://example.test/agents/older"))));
        List<RdfTriple> memory = List.of(
            new RdfTriple(PEER, AGENT_CARD, "https://example.test/cards/helper.json"),
            new RdfTriple(PEER, PROVIDES_TYPE, "https://example.test/types/Hint"));
        TestContext context = new TestContext(true, interactions, List.of(trace()), memory, "self");
        RecordingChannel channel = new RecordingChannel(true);
        channel.response = ConsultationResponse.success("navigate", "next", "go");
        ConsultationStrategy strategy = new ConsultationStrategy(channel, ConsultationStrategyOptions.builder()
            .maxRecentInteractions(1).maxAgentCandidates(1).maxCcrsTraces(1).build());

        strategy.evaluate(Situation.builder().trigger("request failed")
            .currentResource(CURRENT).failedAction("GET")
            .targetResource("https://example.test/failing").httpError(503, "Unavailable").build(), context);

        assertTrue(channel.question.contains("My action 'GET'"));
        assertTrue(channel.question.contains("HTTP 503"));
        assertEquals(1, context.requestedInteractionLimit);
        assertEquals(1, context.requestedTraceLimit);
        assertEquals(1, ((List<?>) channel.context.get("recentActions")).size());
        assertEquals(1, ((List<?>) channel.context.get("consultationTargets")).size());
        assertEquals("request failed", channel.context.get("trigger"));
        assertFalse(channel.context.containsKey("situationType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previous =
            (List<Map<String, Object>>) channel.context.get("previousCcrsInvocations");
        assertEquals("old", previous.get(0).get("trigger"));
        assertEquals(PEER, channel.context.get("agentUri"));
        assertEquals("https://example.test/cards/helper.json", channel.context.get("agentCardUri"));
    }

    @Test
    void projectsFirstLiteralFromTurtleArtifactOntoCurrentResource() {
        RecordingChannel channel = new RecordingChannel(true);
        channel.response = ConsultationResponse.success("inspect", "ignored", "Apply the discovered value");
        channel.response.confidence = 0.7;
        channel.response.metadata = Map.of(
            "artifactContentType", "text/turtle",
            "rawResponse", "<https://source.test/item> <https://example.test/value> \"quoted value\" .");

        StrategyResult.Suggestion suggestion = new ConsultationStrategy(channel)
            .evaluate(Situation.builder().trigger("blocked").currentResource(CURRENT).build(), contextWithPeer())
            .asSuggestion();

        assertEquals("post", suggestion.getActionType());
        assertEquals(CURRENT, suggestion.getActionTarget());
        assertEquals("first_literal_projection", suggestion.getActionParam("projectionHeuristic"));
        assertEquals("https://example.test/value", suggestion.getActionParam("predicate"));
        assertEquals("quoted value", suggestion.getActionParam("object"));
        assertEquals("text/turtle", suggestion.getActionParam("bodyContentType"));
        assertEquals("<" + CURRENT + "> <https://example.test/value> \"quoted value\" .",
            suggestion.getActionParam("body"));
    }

    @Test
    void malformedTurtleArtifactFallsBackToConsultantAction() {
        RecordingChannel channel = new RecordingChannel(true);
        channel.response = ConsultationResponse.success("inspect", "original-target", "Inspect it");
        channel.response.metadata = Map.of("artifactContentType", "text/turtle", "rawResponse", "not turtle");

        StrategyResult.Suggestion suggestion = new ConsultationStrategy(channel)
            .evaluate(Situation.builder().trigger("blocked").currentResource(CURRENT).build(), contextWithPeer())
            .asSuggestion();

        assertEquals("inspect", suggestion.getActionType());
        assertEquals("original-target", suggestion.getActionTarget());
    }

    @Test
    void optionBoundsAndNullOptionsAreHandled() {
        ConsultationStrategyOptions options = ConsultationStrategyOptions.builder()
            .maxRecentInteractions(0).maxAgentCandidates(0).maxCcrsTraces(-1).defaultConfidence(2.0).build();

        assertEquals(1, options.getMaxRecentInteractions());
        assertEquals(1, options.getMaxAgentCandidates());
        assertEquals(0, options.getMaxCcrsTraces());
        assertEquals(1.0, options.getDefaultConfidence(), 0.0);
        assertApplicable(new ConsultationStrategy(new RecordingChannel(true), null),
            Situation.builder().trigger("blocked").build(), contextWithPeer());
    }

    private static TestContext contextWithPeer() {
        return new TestContext(true,
            List.of(interaction(List.of(new RdfTriple(CURRENT, CONTAINS, PEER)))),
            List.of(), List.of(), "self");
    }

    private static Interaction interaction(List<RdfTriple> state) {
        return new Interaction("GET", CURRENT, Map.of(), null,
            Interaction.Outcome.SUCCESS, state, 1L, 2L, "test");
    }

    private static CcrsTrace trace() {
        return CcrsTrace.builder(Situation.builder().trigger("old").build()).build();
    }

    private static void assertApplicable(ConsultationStrategy strategy, Situation situation, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNotApplicable(ConsultationStrategy strategy, Situation situation, CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.NOT_APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNoHelp(
            StrategyResult result, StrategyResult.NoHelpReason reason, String explanation) {
        assertFalse(result.isSuggestion(), result::toDetailedReport);
        assertEquals(reason, result.asNoHelp().getReason());
        assertEquals(explanation, result.asNoHelp().getExplanation());
    }

    private static final class RecordingChannel implements ConsultationStrategy.ConsultationChannel {
        private final boolean available;
        private ConsultationResponse response = ConsultationResponse.success("wait", null, "wait");
        private Exception failure;
        private String question;
        private Map<String, Object> context;

        private RecordingChannel(boolean available) {
            this.available = available;
        }

        @Override public boolean isAvailable() { return available; }

        @Override
        public ConsultationResponse query(String question, Map<String, Object> context) throws Exception {
            this.question = question;
            this.context = context;
            if (failure != null) throw failure;
            return response;
        }

        @Override public String getChannelType() { return "test-channel"; }
    }

    private static final class TestContext implements CcrsContext {
        private final boolean historyAvailable;
        private final List<Interaction> interactions;
        private final List<CcrsTrace> traces;
        private final List<RdfTriple> triples;
        private final String agentId;
        private int requestedInteractionLimit = -1;
        private int requestedTraceLimit = -1;

        private TestContext(boolean historyAvailable, List<Interaction> interactions,
                List<CcrsTrace> traces, List<RdfTriple> triples, String agentId) {
            this.historyAvailable = historyAvailable;
            this.interactions = interactions;
            this.traces = traces;
            this.triples = triples;
            this.agentId = agentId;
        }

        @Override
        public List<RdfTriple> query(String subject, String predicate, String object) {
            return triples.stream()
                .filter(t -> subject == null || subject.equals(t.subject))
                .filter(t -> predicate == null || predicate.equals(t.predicate))
                .filter(t -> object == null || object.equals(t.object)).toList();
        }

        @Override public boolean contains(RdfTriple triple) { return triples.contains(triple); }

        @Override
        public List<Interaction> getRecentInteractions(int maxCount) {
            requestedInteractionLimit = maxCount;
            return new ArrayList<>(interactions.subList(0, Math.min(maxCount, interactions.size())));
        }

        @Override public Optional<String> getCurrentResource() { return Optional.of(CURRENT); }
        @Override public String getAgentId() { return agentId; }
        @Override public Optional<CcrsTrace> getLastCcrsInvocation() { return traces.stream().findFirst(); }

        @Override
        public List<CcrsTrace> getCcrsHistory(int maxCount) {
            requestedTraceLimit = maxCount;
            return traces.subList(0, Math.min(maxCount, traces.size()));
        }

        @Override public void recordCcrsInvocation(CcrsTrace trace) { }
        @Override public boolean hasHistory() { return historyAvailable; }
    }
}
