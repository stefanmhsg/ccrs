package ccrs.core.contingency.strategies.internal;

import java.util.List;
import java.util.logging.Logger;

import ccrs.core.contingency.CcrsStrategy;
import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.Situation;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.StopStrategyOptions;
import ccrs.core.rdf.CcrsContext;

/**
 * L0 advisory safeguard used after repeated absent or weak runtime guidance.
 *
 * <p>Stop first requests one or more invocations without trace-learned
 * ordering or gates. Only after those reconsideration cycles fail does it
 * suggest that the consuming agent consider stopping. The strategy never
 * terminates the agent itself.</p>
 */
public class StopStrategy implements CcrsStrategy {

    private static final Logger logger = Logger.getLogger(StopStrategy.class.getName());

    public static final String ID = "stop";

    private final StopStrategyOptions options;

    public StopStrategy() {
        this(StopStrategyOptions.defaults());
    }

    public StopStrategy(StopStrategyOptions options) {
        this.options = options == null ? StopStrategyOptions.defaults() : options;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Stop (Last Resort)";
    }

    @Override
    public Category getCategory() {
        return Category.INTERNAL;
    }

    @Override
    public int getEscalationLevel() {
        return 0;
    }

    @Override
    public Applicability appliesTo(Situation situation, CcrsContext context) {
        StopHistoryAssessment assessment = assess(context);
        if (!assessment.isTriggered()) {
            logger.info(String.format(
                "[Stop] Not applicable - no guidance degradation trigger (noSuggestion=%d/%d, lowConfidence=%d/%d)",
                assessment.consecutiveNoSuggestionCount(),
                options.getNoSuggestionInvocationThreshold(),
                assessment.recentLowConfidenceCount(),
                options.getLowConfidenceInvocationThreshold()));
            return Applicability.NOT_APPLICABLE;
        }

        logger.info(String.format(
            "[Stop] Applicable - trigger=%s completedBypasses=%d/%d",
            assessment.trigger().parameterValue(),
            assessment.completedSelectionBypassCount(),
            options.getSelectionResetCountBeforeStop()));
        return Applicability.APPLICABLE;
    }

    @Override
    public StrategyResult evaluate(Situation situation, CcrsContext context) {
        StopHistoryAssessment assessment = assess(context);
        if (!assessment.isTriggered()) {
            return StrategyResult.noHelp(
                ID,
                StrategyResult.NoHelpReason.NOT_APPLICABLE,
                buildAssessmentExplanation(assessment, "No degradation trigger is active"));
        }

        if (assessment.completedSelectionBypassCount()
                < options.getSelectionResetCountBeforeStop()) {
            String explanation = buildAssessmentExplanation(
                assessment,
                "Requesting one contingency invocation without trace-learned ordering or gates");
            logger.warning("[Stop] " + explanation);
            return StrategyResult.noHelp(
                ID,
                StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED,
                explanation);
        }

        String finalContext = buildFinalContext(situation);
        String rationale = buildStopRationale(assessment, finalContext);
        StrategyResult.Suggestion.Builder suggestion = StrategyResult.suggest(ID, "stop")
            .target(null)
            .param("trigger", assessment.trigger().parameterValue())
            .param("consecutiveNoSuggestionCount", assessment.consecutiveNoSuggestionCount())
            .param("noSuggestionInvocationThreshold", options.getNoSuggestionInvocationThreshold())
            .param("recentLowConfidenceCount", assessment.recentLowConfidenceCount())
            .param("lowConfidenceInvocationThreshold", options.getLowConfidenceInvocationThreshold())
            .param("lowConfidenceThreshold", options.getLowConfidenceThreshold())
            .param("completedSelectionBypassCount", assessment.completedSelectionBypassCount())
            .param("selectionResetCountBeforeStop", options.getSelectionResetCountBeforeStop())
            .param("traceHistoryLookbackLimit", options.getTraceHistoryLookbackLimit())
            .param("finalContext", finalContext)
            .confidence(1.0)
            .rationale(rationale);
        addRequestEvidence(suggestion, situation);

        logger.warning(String.format(
            "[Stop] Recommending that the agent consider stopping after %d learned-selection bypass cycle(s)",
            assessment.completedSelectionBypassCount()));
        return suggestion.build();
    }

    private StopHistoryAssessment assess(CcrsContext context) {
        List<CcrsTrace> history = context == null
            ? List.of()
            : context.getCcrsHistory(options.getTraceHistoryLookbackLimit());
        return StopHistoryAssessment.assess(history, options);
    }

    private String buildAssessmentExplanation(
            StopHistoryAssessment assessment,
            String prefix) {
        return String.format(
            "%s: trigger=%s, consecutiveNoSuggestionCount=%d/%d, recentLowConfidenceCount=%d/%d below %.3f, completedSelectionBypassCount=%d/%d, episodeTraceCount=%d, traceHistoryLookbackLimit=%d",
            prefix,
            assessment.trigger().parameterValue(),
            assessment.consecutiveNoSuggestionCount(),
            options.getNoSuggestionInvocationThreshold(),
            assessment.recentLowConfidenceCount(),
            options.getLowConfidenceInvocationThreshold(),
            options.getLowConfidenceThreshold(),
            assessment.completedSelectionBypassCount(),
            options.getSelectionResetCountBeforeStop(),
            assessment.episodeTraceCount(),
            options.getTraceHistoryLookbackLimit());
    }

    private String buildStopRationale(
            StopHistoryAssessment assessment,
            String finalContext) {
        return String.format(
            "CCRS recommends that the agent consider stopping because %s persisted after %d one-invocation learned-selection bypass cycle(s), during which all configured and applicable strategies were reconsidered without learned ordering or gating. Observed no-guidance count=%d/%d and weak-guidance count=%d/%d below confidence %.3f. Final request context: %s. The agent retains the final decision and should check remaining time, token or cost budget, safety constraints, task obligations, and whether a partial result can still be returned.",
            assessment.trigger().parameterValue(),
            assessment.completedSelectionBypassCount(),
            assessment.consecutiveNoSuggestionCount(),
            options.getNoSuggestionInvocationThreshold(),
            assessment.recentLowConfidenceCount(),
            options.getLowConfidenceInvocationThreshold(),
            options.getLowConfidenceThreshold(),
            finalContext);
    }

    private String buildFinalContext(Situation situation) {
        if (situation == null) {
            return "No request details available";
        }

        StringBuilder context = new StringBuilder();
        appendContext(context, "trigger", situation.getTrigger());
        appendContext(context, "currentResource", situation.getCurrentResource());
        appendContext(context, "targetResource", situation.getTargetResource());
        appendContext(context, "failedAction", situation.getFailedAction());
        appendContext(context, "httpStatus", situation.getErrorInfoString("httpStatus"));
        appendContext(context, "errorType", situation.getErrorInfoString("errorType"));
        appendContext(context, "message", situation.getErrorInfoString("message"));
        return context.length() == 0 ? "No request details available" : context.toString();
    }

    private void appendContext(StringBuilder context, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (context.length() > 0) {
            context.append(", ");
        }
        context.append(name).append('=').append(value);
    }

    private void addRequestEvidence(
            StrategyResult.Suggestion.Builder suggestion,
            Situation situation) {
        if (situation == null) {
            return;
        }
        addParamIfPresent(suggestion, "requestTrigger", situation.getTrigger());
        addParamIfPresent(suggestion, "currentResource", situation.getCurrentResource());
        addParamIfPresent(suggestion, "targetResource", situation.getTargetResource());
        addParamIfPresent(suggestion, "failedAction", situation.getFailedAction());
        addParamIfPresent(suggestion, "httpStatus", situation.getErrorInfoString("httpStatus"));
        addParamIfPresent(suggestion, "errorType", situation.getErrorInfoString("errorType"));
        addParamIfPresent(suggestion, "errorMessage", situation.getErrorInfoString("message"));
    }

    private void addParamIfPresent(
            StrategyResult.Suggestion.Builder suggestion,
            String name,
            String value) {
        if (value != null && !value.isBlank()) {
            suggestion.param(name, value);
        }
    }

    @Override
    public String getDescription() {
        return "Advisory stop after degraded guidance and ungated reconsideration";
    }
}
