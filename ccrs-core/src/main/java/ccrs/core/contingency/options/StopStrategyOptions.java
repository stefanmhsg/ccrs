package ccrs.core.contingency.options;

/**
 * Immutable options for the advisory stop and strategy-reconsideration policy.
 */
public final class StopStrategyOptions {

    public static final int DEFAULT_NO_SUGGESTION_INVOCATION_THRESHOLD = 2;
    public static final int DEFAULT_LOW_CONFIDENCE_INVOCATION_THRESHOLD = 3;
    public static final double DEFAULT_LOW_CONFIDENCE_THRESHOLD = 0.5;
    public static final int DEFAULT_SELECTION_RESET_COUNT_BEFORE_STOP = 1;
    public static final int DEFAULT_TRACE_HISTORY_LOOKBACK_LIMIT = 30;

    private final int noSuggestionInvocationThreshold;
    private final int lowConfidenceInvocationThreshold;
    private final double lowConfidenceThreshold;
    private final int selectionResetCountBeforeStop;
    private final int traceHistoryLookbackLimit;

    private StopStrategyOptions(Builder builder) {
        this.noSuggestionInvocationThreshold = Math.max(1, builder.noSuggestionInvocationThreshold);
        this.lowConfidenceInvocationThreshold = Math.max(1, builder.lowConfidenceInvocationThreshold);
        this.lowConfidenceThreshold = normalizeConfidence(builder.lowConfidenceThreshold);
        this.selectionResetCountBeforeStop = Math.max(1, builder.selectionResetCountBeforeStop);
        int minimumLookback = Math.max(
            Math.max(noSuggestionInvocationThreshold, lowConfidenceInvocationThreshold),
            selectionResetCountBeforeStop + 1);
        this.traceHistoryLookbackLimit = Math.max(minimumLookback, builder.traceHistoryLookbackLimit);
    }

    public static StopStrategyOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .noSuggestionInvocationThreshold(noSuggestionInvocationThreshold)
            .lowConfidenceInvocationThreshold(lowConfidenceInvocationThreshold)
            .lowConfidenceThreshold(lowConfidenceThreshold)
            .selectionResetCountBeforeStop(selectionResetCountBeforeStop)
            .traceHistoryLookbackLimit(traceHistoryLookbackLimit);
    }

    public int getNoSuggestionInvocationThreshold() {
        return noSuggestionInvocationThreshold;
    }

    public int getLowConfidenceInvocationThreshold() {
        return lowConfidenceInvocationThreshold;
    }

    public double getLowConfidenceThreshold() {
        return lowConfidenceThreshold;
    }

    public int getSelectionResetCountBeforeStop() {
        return selectionResetCountBeforeStop;
    }

    public int getTraceHistoryLookbackLimit() {
        return traceHistoryLookbackLimit;
    }

    private static double normalizeConfidence(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_LOW_CONFIDENCE_THRESHOLD;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static final class Builder {
        private int noSuggestionInvocationThreshold = DEFAULT_NO_SUGGESTION_INVOCATION_THRESHOLD;
        private int lowConfidenceInvocationThreshold = DEFAULT_LOW_CONFIDENCE_INVOCATION_THRESHOLD;
        private double lowConfidenceThreshold = DEFAULT_LOW_CONFIDENCE_THRESHOLD;
        private int selectionResetCountBeforeStop = DEFAULT_SELECTION_RESET_COUNT_BEFORE_STOP;
        private int traceHistoryLookbackLimit = DEFAULT_TRACE_HISTORY_LOOKBACK_LIMIT;

        /** Set the number of consecutive no-guidance invocations that arms Stop. */
        public Builder noSuggestionInvocationThreshold(int threshold) {
            this.noSuggestionInvocationThreshold = threshold;
            return this;
        }

        /** Set the number of recent weak-guidance invocations that arms Stop. */
        public Builder lowConfidenceInvocationThreshold(int threshold) {
            this.lowConfidenceInvocationThreshold = threshold;
            return this;
        }

        /** Set the confidence boundary below which non-Stop guidance is weak. */
        public Builder lowConfidenceThreshold(double threshold) {
            this.lowConfidenceThreshold = threshold;
            return this;
        }

        /** Set how many one-invocation learned-selection bypasses precede Stop. */
        public Builder selectionResetCountBeforeStop(int count) {
            this.selectionResetCountBeforeStop = count;
            return this;
        }

        /** Set the maximum number of prior CCRS traces Stop may inspect. */
        public Builder traceHistoryLookbackLimit(int limit) {
            this.traceHistoryLookbackLimit = limit;
            return this;
        }

        public StopStrategyOptions build() {
            return new StopStrategyOptions(this);
        }
    }
}
