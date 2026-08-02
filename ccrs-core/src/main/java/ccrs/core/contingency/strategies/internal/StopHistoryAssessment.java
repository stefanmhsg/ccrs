package ccrs.core.contingency.strategies.internal;

import java.util.List;
import java.util.OptionalDouble;

import ccrs.core.contingency.dto.CcrsTrace;
import ccrs.core.contingency.dto.StrategyResult;
import ccrs.core.contingency.options.StopStrategyOptions;

/**
 * Immutable classification of the current run's bounded contingency history.
 */
final class StopHistoryAssessment {

    enum Trigger {
        NONE("none"),
        NO_SUGGESTIONS("no_suggestions"),
        LOW_CONFIDENCE("low_confidence"),
        BOTH("both");

        private final String parameterValue;

        Trigger(String parameterValue) {
            this.parameterValue = parameterValue;
        }

        String parameterValue() {
            return parameterValue;
        }
    }

    private final Trigger trigger;
    private final int consecutiveNoSuggestionCount;
    private final int recentLowConfidenceCount;
    private final int completedSelectionBypassCount;
    private final int episodeTraceCount;

    private StopHistoryAssessment(
            Trigger trigger,
            int consecutiveNoSuggestionCount,
            int recentLowConfidenceCount,
            int completedSelectionBypassCount,
            int episodeTraceCount) {
        this.trigger = trigger;
        this.consecutiveNoSuggestionCount = consecutiveNoSuggestionCount;
        this.recentLowConfidenceCount = recentLowConfidenceCount;
        this.completedSelectionBypassCount = completedSelectionBypassCount;
        this.episodeTraceCount = episodeTraceCount;
    }

    static StopHistoryAssessment assess(
            List<CcrsTrace> recentTraces,
            StopStrategyOptions options) {
        int consecutiveNoSuggestionCount = 0;
        int recentLowConfidenceCount = 0;
        int completedSelectionBypassCount = 0;
        int episodeTraceCount = 0;
        boolean countingNoSuggestionStreak = true;

        if (recentTraces != null) {
            for (CcrsTrace trace : recentTraces) {
                if (trace == null) {
                    continue;
                }

                OptionalDouble maxConfidence = trace.getMaxNonStopSuggestionConfidence();
                if (trace.hasSuccessfulOutcome()
                        || (maxConfidence.isPresent()
                            && maxConfidence.getAsDouble() >= options.getLowConfidenceThreshold())) {
                    break;
                }

                episodeTraceCount++;
                if (trace.didStrategyReturnNoHelp(
                        StopStrategy.ID,
                        StrategyResult.NoHelpReason.SELECTION_RECONSIDERATION_REQUESTED)) {
                    completedSelectionBypassCount++;
                }

                if (maxConfidence.isEmpty()) {
                    if (countingNoSuggestionStreak) {
                        consecutiveNoSuggestionCount++;
                    }
                } else {
                    countingNoSuggestionStreak = false;
                    recentLowConfidenceCount++;
                }
            }
        }

        boolean noSuggestions = consecutiveNoSuggestionCount
            >= options.getNoSuggestionInvocationThreshold();
        boolean lowConfidence = recentLowConfidenceCount
            >= options.getLowConfidenceInvocationThreshold();
        Trigger trigger = noSuggestions && lowConfidence
            ? Trigger.BOTH
            : noSuggestions
                ? Trigger.NO_SUGGESTIONS
                : lowConfidence
                    ? Trigger.LOW_CONFIDENCE
                    : Trigger.NONE;

        return new StopHistoryAssessment(
            trigger,
            consecutiveNoSuggestionCount,
            recentLowConfidenceCount,
            completedSelectionBypassCount,
            episodeTraceCount);
    }

    boolean isTriggered() {
        return trigger != Trigger.NONE;
    }

    Trigger trigger() {
        return trigger;
    }

    int consecutiveNoSuggestionCount() {
        return consecutiveNoSuggestionCount;
    }

    int recentLowConfidenceCount() {
        return recentLowConfidenceCount;
    }

    int completedSelectionBypassCount() {
        return completedSelectionBypassCount;
    }

    int episodeTraceCount() {
        return episodeTraceCount;
    }
}
