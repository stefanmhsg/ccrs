package ccrs.core.contingency.strategies.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Interaction;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.RetryStrategyOptions;
import ccrs.core.rdf.CcrsContext;
import ccrs.core.rdf.RdfTriple;

class RetryStrategyTest {

    private static final String TARGET = "https://example.test/orders/42";
    private static final String ACTION = "PUT";

    @Test
    void exposesStableStrategyMetadata() {
        RetryStrategy strategy = new RetryStrategy();

        assertEquals("retry", strategy.getId());
        assertEquals("Retry", strategy.getName());
        assertEquals(CcrsStrategy.Category.INTERNAL, strategy.getCategory());
        assertEquals(1, strategy.getEscalationLevel());
        assertEquals("Retry (L1)", strategy.getDescription());
        assertTrue(strategy.isEnabled());
    }

    @Test
    void appliesToDefaultRetriableHttpStatusesAndErrorTypes() {
        RetryStrategy strategy = new RetryStrategy();

        for (int status : List.of(500, 502, 503, 504)) {
            assertApplicable(strategy, failureWithHttpStatus(status, "Transient failure"), null);
        }
        for (String errorType : List.of("timeout", "connection_reset", "connection_refused")) {
            assertApplicable(strategy, failureWithErrorType(errorType), null);
        }

        Situation retriableByEitherField = Situation.failure("request failed")
            .failedAction(ACTION)
            .targetResource(TARGET)
            .httpError(404, "Gateway timed out")
            .errorInfo("errorType", "timeout")
            .build();
        assertApplicable(strategy, retriableByEitherField, null);
    }

    @Test
    void rejectsNonFailuresMissingRetryIdentityAndPermanentErrors() {
        RetryStrategy strategy = new RetryStrategy();
        Situation stuck = Situation.stuck("blocked")
            .failedAction(ACTION)
            .targetResource(TARGET)
            .errorInfo("errorType", "timeout")
            .build();
        Situation missingAction = Situation.failure("failed")
            .targetResource(TARGET)
            .errorInfo("errorType", "timeout")
            .build();
        Situation missingTarget = Situation.failure("failed")
            .failedAction(ACTION)
            .errorInfo("errorType", "timeout")
            .build();
        Situation permanentFailure = failureWithHttpStatus(404, "Not found");

        assertNotApplicable(strategy, stuck, null);
        assertNotApplicable(strategy, missingAction, null);
        assertNotApplicable(strategy, missingTarget, null);
        assertNotApplicable(strategy, permanentFailure, null);
    }

    @Test
    void customRetriableCodesReplaceDefaultsAndAreSnapshotted() {
        Set<String> configuredCodes = new LinkedHashSet<>(Set.of("429"));
        RetryStrategy strategy = new RetryStrategy(RetryStrategyOptions.builder()
            .retriableCodes(configuredCodes)
            .build());
        configuredCodes.add("503");

        assertApplicable(strategy, failureWithHttpStatus(429, "Rate limited"), null);
        assertNotApplicable(strategy, failureWithHttpStatus(503, "Unavailable"), null);
    }

    @Test
    void nullOptionsUseDefaults() {
        RetryStrategy strategy = new RetryStrategy(null);

        assertApplicable(strategy, failureWithHttpStatus(503, "Unavailable"), null);
    }

    @Test
    void firstHttpRetryContainsCompleteSuggestion() {
        StrategyResult result = new RetryStrategy().evaluate(
            failureWithHttpStatus(503, "Temporarily unavailable"),
            null);

        assertTrue(result.isSuggestion(), result::toDetailedReport);
        StrategyResult.Suggestion suggestion = result.asSuggestion();
        assertEquals("retry", suggestion.getStrategyId());
        assertEquals("retry", suggestion.getActionType());
        assertEquals(TARGET, suggestion.getActionTarget());
        assertEquals(ACTION, suggestion.getActionParam("originalAction"));
        assertEquals(1000L, suggestion.<Long>getActionParam("delayMs"));
        assertEquals(1, suggestion.<Integer>getActionParam("attemptNumber"));
        assertEquals(3, suggestion.<Integer>getActionParam("maxAttempts"));
        assertEquals(0.8, suggestion.getConfidence(), 0.000_001);
        assertEquals(
            "HTTP 503 (Temporarily unavailable) is typically transient. "
                + "Retry attempt 1 after 1000ms delay.",
            suggestion.getRationale());
        assertFalse(suggestion.hasOpportunisticGuidance());
    }

    @Test
    void errorTypeRetryUsesGenericRationaleAndBaseConfidence() {
        StrategyResult.Suggestion suggestion = new RetryStrategy()
            .evaluate(failureWithErrorType("timeout"), null)
            .asSuggestion();

        assertEquals(0.7, suggestion.getConfidence(), 0.000_001);
        assertEquals(
            "Transient error detected. Retry attempt 1 after 1000ms delay.",
            suggestion.getRationale());
    }

    @Test
    void appliesExponentialBackoffAndConfidenceDecayForMatchingAttempts() {
        Situation situation = failureWithHttpStatus(500, "Internal error");
        TestContext context = new TestContext(List.of(
            evaluatedRetry(situation),
            evaluatedRetry(situation)));

        StrategyResult.Suggestion suggestion = new RetryStrategy().evaluate(situation, context).asSuggestion();

        assertEquals(3, suggestion.<Integer>getActionParam("attemptNumber"));
        assertEquals(4000L, suggestion.<Long>getActionParam("delayMs"));
        assertEquals(0.32, suggestion.getConfidence(), 0.000_001);
        assertTrue(suggestion.getRationale().contains("Retry attempt 3 after 4000ms delay."));
    }

    @Test
    void countsOnlyEvaluatedRetriesForTheSameFailureActionAndTarget() {
        Situation current = failureWithErrorType("timeout");
        List<CcrsTrace> history = new ArrayList<>();
        history.add(evaluatedRetry(current));
        history.add(evaluatedRetry(failure(ACTION, "https://example.test/orders/99", "timeout")));
        history.add(evaluatedRetry(failure("POST", TARGET, "timeout")));
        history.add(evaluatedRetry(Situation.stuck("blocked")
            .failedAction(ACTION)
            .targetResource(TARGET)
            .errorInfo("errorType", "timeout")
            .build()));
        history.add(evaluatedBy(current, "backtrack", suggestion("backtrack")));
        history.add(evaluatedBy(current, RetryStrategy.ID, null));

        StrategyResult.Suggestion suggestion = new RetryStrategy()
            .evaluate(current, new TestContext(history))
            .asSuggestion();

        assertEquals(2, suggestion.<Integer>getActionParam("attemptNumber"));
        assertEquals(2000L, suggestion.<Long>getActionParam("delayMs"));
        assertEquals(0.56, suggestion.getConfidence(), 0.000_001);
    }

    @Test
    void exhaustedRetriesAreRejectedByApplicabilityAndEvaluation() {
        Situation situation = failureWithErrorType("timeout");
        TestContext context = new TestContext(List.of(
            evaluatedRetry(situation),
            evaluatedRetry(situation),
            evaluatedRetry(situation)));
        RetryStrategy strategy = new RetryStrategy();

        assertNotApplicable(strategy, situation, context);

        StrategyResult result = strategy.evaluate(situation, context);
        assertFalse(result.isSuggestion(), result::toDetailedReport);
        assertEquals(RetryStrategy.ID, result.getStrategyId());
        assertEquals(StrategyResult.NoHelpReason.ALREADY_ATTEMPTED, result.asNoHelp().getReason());
        assertEquals("Max retry attempts (3) exceeded", result.asNoHelp().getExplanation());
    }

    @Test
    void respectsConfiguredAttemptDelayBackoffAndRetryCode() {
        Situation situation = failure(ACTION, TARGET, "busy");
        RetryStrategy strategy = new RetryStrategy(RetryStrategyOptions.builder()
            .maxAttempts(2)
            .initialDelayMs(250L)
            .backoffMultiplier(3.0)
            .retriableCodes(Set.of("busy"))
            .build());
        TestContext context = new TestContext(List.of(evaluatedRetry(situation)));

        assertApplicable(strategy, situation, context);
        StrategyResult.Suggestion suggestion = strategy.evaluate(situation, context).asSuggestion();

        assertEquals(2, suggestion.<Integer>getActionParam("attemptNumber"));
        assertEquals(2, suggestion.<Integer>getActionParam("maxAttempts"));
        assertEquals(750L, suggestion.<Long>getActionParam("delayMs"));
        assertEquals(0.56, suggestion.getConfidence(), 0.000_001);
    }

    @Test
    void respectsConfiguredTraceLookbackLimit() {
        Situation situation = failureWithErrorType("timeout");
        TestContext context = new TestContext(List.of(
            evaluatedRetry(failure("POST", TARGET, "timeout")),
            evaluatedRetry(situation)));
        RetryStrategy strategy = new RetryStrategy(RetryStrategyOptions.builder()
            .retryLookbackLimit(1)
            .build());

        StrategyResult.Suggestion suggestion = strategy.evaluate(situation, context).asSuggestion();

        assertEquals(1, context.requestedTraceLimit);
        assertEquals(1, suggestion.<Integer>getActionParam("attemptNumber"));
    }

    @Test
    void clampsInvalidNumericOptionsAtTheirDocumentedMinimums() {
        RetryStrategyOptions options = RetryStrategyOptions.builder()
            .maxAttempts(-1)
            .initialDelayMs(-50L)
            .backoffMultiplier(-2.0)
            .retryLookbackLimit(0)
            .build();

        assertEquals(0, options.getMaxAttempts());
        assertEquals(0L, options.getInitialDelayMs());
        assertEquals(0.0, options.getBackoffMultiplier(), 0.0);
        assertEquals(1, options.getRetryLookbackLimit());
    }

    private static void assertApplicable(
            RetryStrategy strategy,
            Situation situation,
            CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static void assertNotApplicable(
            RetryStrategy strategy,
            Situation situation,
            CcrsContext context) {
        assertEquals(CcrsStrategy.Applicability.NOT_APPLICABLE, strategy.appliesTo(situation, context));
    }

    private static Situation failureWithHttpStatus(int status, String message) {
        return Situation.failure("request failed")
            .failedAction(ACTION)
            .targetResource(TARGET)
            .httpError(status, message)
            .build();
    }

    private static Situation failureWithErrorType(String errorType) {
        return failure(ACTION, TARGET, errorType);
    }

    private static Situation failure(String action, String target, String errorType) {
        return Situation.failure("request failed")
            .failedAction(action)
            .targetResource(target)
            .errorInfo("errorType", errorType)
            .build();
    }

    private static CcrsTrace evaluatedRetry(Situation situation) {
        return evaluatedBy(situation, RetryStrategy.ID, suggestion(RetryStrategy.ID));
    }

    private static CcrsTrace evaluatedBy(
            Situation situation,
            String strategyId,
            StrategyResult result) {
        return CcrsTrace.builder(situation)
            .addEvaluation(
                strategyId,
                1,
                CcrsStrategy.Applicability.APPLICABLE,
                result,
                1L)
            .build();
    }

    private static StrategyResult suggestion(String strategyId) {
        return StrategyResult.suggest(strategyId, "test").build();
    }

    private static final class TestContext implements CcrsContext {
        private final List<CcrsTrace> recentFirstHistory;
        private int requestedTraceLimit = -1;

        private TestContext(List<CcrsTrace> recentFirstHistory) {
            this.recentFirstHistory = recentFirstHistory;
        }

        @Override
        public List<RdfTriple> query(String subject, String predicate, String object) {
            return Collections.emptyList();
        }

        @Override
        public boolean contains(RdfTriple triple) {
            return false;
        }

        @Override
        public List<Interaction> getRecentInteractions(int maxCount) {
            return Collections.emptyList();
        }

        @Override
        public Optional<CcrsTrace> getLastCcrsInvocation() {
            return recentFirstHistory.stream().findFirst();
        }

        @Override
        public List<CcrsTrace> getCcrsHistory(int maxCount) {
            requestedTraceLimit = maxCount;
            return recentFirstHistory.subList(0, Math.min(maxCount, recentFirstHistory.size()));
        }

        @Override
        public void recordCcrsInvocation(CcrsTrace trace) {
        }

        @Override
        public boolean hasHistory() {
            return true;
        }
    }
}
