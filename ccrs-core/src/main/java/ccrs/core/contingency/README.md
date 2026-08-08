# Contingency CCRS Implementation Guide

Contingency Course Check and Revision Strategies (CCRS) provide runtime guidance when an agent cannot confidently continue its normal plan. A request describes observable evidence; strategies decide their own applicability from that evidence and the run-local context history.

## Module overview

```text
ccrs/core/contingency/
|-- ContingencyCcrs.java              # Main evaluation orchestrator
|-- ContingencyConfiguration.java     # Orchestration and strategy options
|-- ContingencyCcrsFactory.java       # Core and ServiceLoader assembly
|-- CcrsStrategy.java                 # Base strategy contract
|-- StrategyRegistry.java             # Available strategy registry
|-- dto/
|   |-- Situation.java                # Type-free request evidence
|   |-- StrategyResult.java           # Suggestion or NoHelp
|   |-- CcrsTrace.java                # Evaluation trace and outcome
|   `-- Interaction.java              # Prior agent interaction
|-- options/                          # Per-strategy public configuration
|-- selection/                        # Default and trace-based selection
`-- strategies/
    |-- internal/
    |   |-- RetryStrategy.java        # L1 transient-failure recovery
    |   |-- BacktrackStrategy.java    # L2 return to a prior decision point
    |   |-- prediction/
    |   |   `-- PredictionLlmStrategy.java # L4 model-based prediction
    |   `-- StopStrategy.java         # L0 advisory last resort
    `-- social/
        `-- ConsultationStrategy.java # L3 external consultation
```

The agent-agnostic core depends on the RDF-focused [CcrsContext.java](../rdf/CcrsContext.java). Platform adapters, such as the [JaCaMo contingency adapter](../../../../../../../ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/README.md), translate their native state into the same core request and context contracts.

## Core APIs

- [Situation.java](dto/Situation.java) is the primary input DTO. It records evidence known by the caller without classifying the request or selecting a strategy.
- [StrategyResult.java](dto/StrategyResult.java) is the output type. A `Suggestion` contains actionable advice, confidence, rationale, and parameters; `NoHelp` explains why a strategy did not provide guidance.
- [CcrsStrategy.java](CcrsStrategy.java) defines strategy identity, category, escalation level, applicability, evaluation, enabled state, and description.
- [ContingencyCcrs.java](ContingencyCcrs.java) orders, gates, evaluates, traces, and selects strategies.
- [StrategyRegistry.java](StrategyRegistry.java) manages built-in and contributed strategies.
- [CcrsTrace.java](dto/CcrsTrace.java) records the request, strategy evaluations, selection, timing, and reported outcome.
- [Interaction.java](dto/Interaction.java) records prior agent interactions used by strategies such as Retry, Backtrack, and Prediction.
- [ContingencyConfiguration.java](ContingencyConfiguration.java) exposes orchestration, learned-selection, and built-in strategy options.

## Request model

`Situation` is deliberately type-free. It contains only information known by the caller:

- `trigger`: a short explanation of why guidance was requested;
- `currentResource`: the agent's current resource or state;
- `targetResource`: the intended resource, if known;
- `failedAction`: the action that failed or made no progress;
- `errorInfo`: structured failure evidence such as `httpStatus`, `errorType`, and `message`;
- `metadata`: caller-specific evidence that does not belong in the common fields.

```java
Situation situation = Situation.builder()
    .trigger("service_unavailable")
    .currentResource("https://example.org/orders")
    .targetResource("https://example.org/orders/42")
    .failedAction("POST")
    .httpError(503, "Service unavailable")
    .metadata("agentName", "order-agent")
    .build();
```

There is no request category or strategy hint. The `trigger` is explanatory evidence, not a classifier. Adding a strategy therefore does not require changing the DTO or every caller.

## Strategy applicability

Applicability belongs to each strategy and is determined from concrete request evidence and context:

| Strategy | Level | Category | Applicability evidence and purpose |
|---|---:|---|---|
| `RetryStrategy` | L1 | INTERNAL | Requires `failedAction`, `targetResource`, and a retriable error code or type. Prior attempts are matched by the concrete `(failedAction, targetResource)` pair. |
| `BacktrackStrategy` | L2 | INTERNAL | Requires `currentResource` and retained interaction history from which a usable earlier decision point can be derived. |
| `ConsultationStrategy` | L3 | SOCIAL | Requires a configured consultation channel and an eligible external agent discoverable from the context. |
| `PredictionLlmStrategy` | L4 | INTERNAL | Requires a configured and usable LLM client. It predicts a recovery action from the available evidence and bounded context. |
| `StopStrategy` | L0 | INTERNAL | Requires run-local trace history to satisfy a degradation threshold and the current invocation to have produced no non-stop suggestion. It advises; it never terminates the agent. |

`CcrsStrategy.appliesTo(...)` is a fast check returning `APPLICABLE`, `NOT_APPLICABLE`, or `UNKNOWN`. Full work belongs in `evaluate(...)`, which returns either a `Suggestion` or `NoHelp`.

## Strategy implementations

### RetryStrategy (L1)

[RetryStrategy.java](strategies/internal/RetryStrategy.java) handles failures that are likely to be transient. It is the cheapest recovery strategy and therefore the first default candidate.

Applicability requires all of the following evidence:

- a `failedAction` to repeat;
- a `targetResource` on which to repeat it;
- an `httpStatus` or `errorType` included in the configured retriable-code set;
- fewer than `maxAttempts` prior Retry evaluations for the same `(failedAction, targetResource)` pair within the retry trace lookback.

The default retriable codes are `500`, `502`, `503`, `504`, `timeout`, `connection_reset`, and `connection_refused`. They are policy, not a closed protocol list: deployments can replace or extend them through [RetryStrategyOptions.java](options/RetryStrategyOptions.java).

When applicable, Retry returns a `retry` suggestion targeting the original resource. Its parameters preserve the original action and expose `delayMs`, `attemptNumber`, and `maxAttempts`. Delay grows exponentially:

```text
delayMs = initialDelayMs * backoffMultiplier ^ priorAttemptCount
```

Confidence depends on the failure evidence and decreases after each prior attempt. For example, HTTP `503` starts with higher confidence than HTTP `500`, but both decay as repeated retries fail. Retry counting uses completed CCRS traces rather than a caller-supplied request category, so unrelated failures do not consume the retry budget.

### BacktrackStrategy (L2)

[BacktrackStrategy.java](strategies/internal/BacktrackStrategy.java) finds an earlier decision point with unexplored alternatives and proposes navigating back to it. It is applicable when a current resource can be resolved and the context retains interaction history.

Evaluation builds a domain-independent interaction graph:

- nodes are requested resources;
- observed edges come from consecutive requests in chronological interaction history;
- advertised edges come from URI objects perceived in successful responses;
- failed resources and the currently blocked resource are treated as exhausted alternatives;
- advertised targets that were never requested are treated as unexplored alternatives.

Candidate checkpoints must be reachable and retain at least one unexplored alternative. They are ranked by:

1. shorter backtrack distance;
2. more unexplored alternatives;
3. greater recency;
4. higher validation score.

The selected suggestion has action `navigate` and targets the checkpoint. Its `backtrackPath` excludes the current resource and starts with the first navigation step the agent can execute. Additional parameters describe alternatives, exhausted branches, graph size, distance, and validation. Confidence increases with useful alternatives and decreases with travel distance. Structured opportunistic guidance exposes both path steps and unexplored options to adapters that integrate contingency advice into normal prioritization.

The interaction window is bounded by [BacktrackStrategyOptions.java](options/BacktrackStrategyOptions.java).

### ConsultationStrategy (L3)

[ConsultationStrategy.java](strategies/social/ConsultationStrategy.java) asks another agent, service, model, or human-facing channel for help after local recovery strategies are insufficient. It does not assume a concrete transport; the nested `ConsultationChannel` interface supplies availability, query, and channel-identification operations.

Applicability requires:

- a configured and currently available channel;
- retained interaction history so the request has a useful basis;
- at least one consultable peer discovered from recent perceived RDF state.

The current discovery convention finds peer candidates through `maze:contains` statements in recent interactions, removes the current agent, and enriches candidates with in-context A2A agent-card and advertised-capability triples when available. The strategy then constructs a bounded question and context containing the Situation evidence, recent interactions, recent CCRS traces, local RDF neighborhood, and discovered consultation targets.

A successful actionable response becomes a suggestion carrying the consultation source, original advice, response metadata, and any action projection derived from that metadata. Channel confidence is used when it is within `(0, 1]`; otherwise the configured fallback confidence is used. Transport failures, unavailable channels, unsuccessful responses, and advice without an action become explicit `NoHelp` results.

[ConsultationStrategyOptions.java](options/ConsultationStrategyOptions.java) bounds recent interactions, candidate targets, and CCRS traces and configures fallback confidence. An A2A channel is one possible provider; it is not part of the core strategy contract.

### PredictionLlmStrategy (L4)

[PredictionLlmStrategy.java](strategies/internal/prediction/PredictionLlmStrategy.java) asks an LLM to infer a recovery action when cheaper strategies cannot provide sufficient guidance. It is applicable only when an LLM capability is available and a current resource can be resolved.

The prompt keeps four evidence sources separate:

1. the current type-free `Situation`;
2. bounded recent interactions, including request details, outcomes, and perceived RDF triples;
3. the bounded incoming and outgoing RDF neighborhood around the current resource;
4. bounded prior CCRS traces.

The configured `PromptBuilder` serializes this context, the `LlmClient` completes the prompt, and the `LlmResponseParser` converts the response into a structured action. A valid action becomes a suggestion containing the model action and target, confidence, original reasoning, parse method, and HTTP action parameters where present. If the model explicitly declines to suggest an action, the parser cannot produce a valid action, or the model call fails, the strategy returns `NoHelp` with the corresponding reason.

Model-provided confidence is used when present; otherwise `baseConfidence` supplies the fallback. [PredictionLlmStrategyOptions.java](options/PredictionLlmStrategyOptions.java) bounds every prompt-history source, controls local-neighborhood limits and namespace filtering, and decides whether the parser may extract a low-confidence action from non-JSON text.

### StopStrategy (L0)

[StopStrategy.java](strategies/internal/StopStrategy.java) is not a generic fallback that immediately ends execution. It is an advisory strategy evaluated only after the current invocation produced no non-stop suggestion and run-local history indicates sustained degradation. Its first effect is a one-invocation learned-selection bypass; only after the configured number of unsuccessful reconsideration cycles can it produce a `stop` suggestion. The complete thresholds, episode boundaries, and interpretation are described in [Stop behavior](#stop-behavior).

## Escalation and selection

The default prior orders strategies by increasing effort and disruption:

```text
L1 Retry -> L2 Backtrack -> L3 Consultation -> L4 Prediction -> L0 Stop
```

L0 is always treated as the last resort, not as the numerically first level. The configured escalation policy determines how much of the ordered list is evaluated:

- `SEQUENTIAL`: stop after the first suggestion;
- `BEST_PER_LEVEL`: evaluate the most promising applicable strategy in each escalation level;
- `PARALLEL`: consider all enabled strategies, subject to learned gates, and rank returned suggestions by confidence.

Maximum level, category filters, explicit enable/disable rules, and maximum returned suggestions remain hard configuration constraints. The default order is a prior used when trace history is insufficient or learned selection is disabled.

### Trace-based learning

Every evaluation produces a `CcrsTrace` containing:

- the request evidence;
- all applicability decisions and evaluated strategy results;
- evaluation time per strategy;
- the selected suggestion or suggestions;
- the invocation timestamp;
- optional outcome feedback reported after the agent acts.

This history supports debugging, auditing, and adaptive strategy selection. The default `ContingencyCcrs.evaluate(...)` path records its completed trace through `CcrsContext`. `evaluateWithTrace(...)` returns the trace directly; an adapter using that method must ensure the completed invocation is retained exactly once. Context adapters can reuse [InMemoryCcrsTraceHistory.java](../rdf/InMemoryCcrsTraceHistory.java) for run-local trace retention.

When learned selection is enabled, [TraceBasedStrategySelectionPolicy.java](selection/TraceBasedStrategySelectionPolicy.java) builds profiles from recent traces. Only applicable, actually evaluated samples are learned from. A `NOT_APPLICABLE` result is context filtering, not evidence that a strategy performs poorly.

Suggestion quality and evaluation cost remain separate signals. After a strategy has run, its suggestions are ranked by confidence because the evaluation cost has already been paid. Before another strategy is run, its history is summarized as:

```text
expectedConfidence = suggestionRate * learnedConfidence
averageEvaluationTimeMs = weighted average runtime of applicable evaluations
```

`learnedConfidence` is the weighted average confidence of suggestions the strategy produced, optionally blended with reported outcome feedback. `suggestionRate` captures how often an applicable evaluation produced actionable guidance. Consequently, `NoHelp` lowers the expected chance of receiving a suggestion without pretending that the strategy's actual suggestions had lower confidence.

For example, with recent-first weighted traces such as:

```text
prediction_llm -> NoHelp
prediction_llm -> Suggestion(confidence=0.72)
prediction_llm -> Suggestion(confidence=0.92)
```

with the default recency decay, the model reports average suggestion confidence near `0.812` and suggestion rate near `0.611`. The resulting expected confidence is approximately `0.611 * 0.812 = 0.496`. The LLM can remain high quality when it speaks while the selector also learns that it sometimes spends time and correctly declines to guess. The exact recency weighting is implemented in [TraceBasedStrategySelectionModel.java](selection/TraceBasedStrategySelectionModel.java).

### Continue-or-skip rule

When no suggestion exists yet, the next applicable strategy is always evaluated. Once a suggestion exists and a candidate has enough applicable samples, learned selection continues with that candidate if any of these conditions holds:

```text
expectedGain >= minimumExpectedConfidenceGain
expectedConfidence >= highConfidenceEvaluationFloor
averageEvaluationTimeMs <= cheapEvaluationTimeMs

expectedGain = candidateExpectedConfidence - currentBestSuggestionConfidence
```

The defaults are:

- `minimumExpectedConfidenceGain = 0.10`;
- `highConfidenceEvaluationFloor = 0.80`;
- `cheapEvaluationTimeMs = 250`;
- `learningHistoryLimit = 25`;
- `minimumLearningSamples = 2` per strategy.

Suppose Backtrack already produced confidence `0.453`, while recent Prediction traces yield:

```text
suggestionRate = 1.00
learnedConfidence = 0.92
averageEvaluationTimeMs = 11951
expectedConfidence = 0.92
expectedGain = 0.92 - 0.453 = 0.467
```

An older scalar model discounted confidence by runtime:

```text
0.92 / (1 + 11951 / 3000) = 0.184
```

That made an expensive, high-confidence strategy appear worse than the existing Backtrack suggestion. The current model keeps quality and cost separate, so expected gain `0.467` justifies evaluating Prediction. If expected confidence were `0.50`, the gain would be only `0.047`; the strategy would be skipped because it is neither sufficiently improving, historically above the high-confidence floor, nor cheap.

`learningHistoryLimit` bounds the recent traces read by the selection model so old behavior cannot dominate indefinitely. `minimumLearningSamples` is per strategy, not global. See the [strategy-selection README.md](selection/README.md) for the complete ordering and gating contract.

### One-invocation learned-selection bypass

A learned gate can repeatedly skip strategies whose recent profile is weak. Before Stop recommends ending a run, CCRS performs a bounded reconsideration: Stop can request one invocation using default registry order without learned reordering or learned gates.

The bypass:

- lasts for exactly one following invocation;
- preserves the same trace history rather than starting a new learning epoch;
- preserves maximum level, disabled strategies, category filters, escalation policy, applicability checks, and suggestion limits;
- automatically restores learned selection afterward.

This gives all otherwise available strategies one fresh opportunity before a Stop suggestion can be produced.

## Stop behavior

Stop is advisory. CCRS never terminates the consuming agent, and the agent remains responsible for applying its own time, token, cost, safety, and domain checks.

Stop is evaluated only when the current invocation has produced no non-stop suggestion. It becomes applicable when either historical condition holds:

1. `x` consecutive prior invocations produced no non-stop suggestion; or
2. `y` prior suggestion-producing invocations in the current degradation episode all had maximum non-stop confidence below `z`.

A reported successful outcome ends the degradation episode even if the originating suggestion confidence was below `z`. A non-stop suggestion at or above `z` also bounds the episode. Trace history is held in memory by the context and therefore scoped to one agent run.

On the first qualifying evaluation, Stop returns `NoHelp(SELECTION_RECONSIDERATION_REQUESTED)`. The next invocation bypasses learned selection once. If no strategy helps, that completed bypass counts as one reset cycle. After `v` completed reset cycles, Stop returns the sole suggestion with confidence `1.0`. Its rationale contains the observed counts and configured thresholds so the agent can interpret why stopping is being proposed.

All Stop controls are end-user properties on [StopStrategyOptions.java](options/StopStrategyOptions.java):

| Property | Meaning | Default |
|---|---|---:|
| `noSuggestionInvocationThreshold` | `x`: consecutive no-suggestion invocations | `2` |
| `lowConfidenceInvocationThreshold` | `y`: weak suggestion-producing invocations | `3` |
| `lowConfidenceThreshold` | `z`: confidence below which guidance is weak | `0.5` |
| `selectionResetCountBeforeStop` | `v`: completed one-invocation bypass cycles before suggesting Stop | `1` |
| `traceHistoryLookbackLimit` | Maximum prior `CcrsTrace` records scanned by Stop | `30` |

The lookback is normalized to at least `max(x, y, v + 1)` so configured thresholds remain observable.

## Hypermedia-oriented context

The core operates on RDF triples without domain-specific assumptions:

- Backtrack treats a resource that links to the current resource as a possible parent; it does not require a hard-coded parent predicate.
- Strategies query the graph generically rather than coupling the core to a domain vocabulary.
- `CcrsContext.getNeighborhood(...)` provides bounded outgoing and incoming links around one resource.
- `CcrsContext.getMemoryTriples(...)` provides a broader, bounded RDF-memory snapshot.

[BacktrackStrategy.java](strategies/internal/BacktrackStrategy.java) may attach an ordered `backtrackPath` to its suggestion. An ordered guidance path excludes the current resource, begins with the immediate executable navigation target, and leaves later entries as opportunistic guidance for the agent's normal option-selection flow.

The relevant context surface is:

```java
public interface CcrsContext {
    List<RdfTriple> query(String subject, String predicate, String object);
    boolean contains(RdfTriple triple);
    List<RdfTriple> getMemoryTriples(int maxCount);
    Neighborhood getNeighborhood(String resource, int maxOutgoing, int maxIncoming);

    List<Interaction> getRecentInteractions(int maxCount);
    Optional<Interaction> getLastInteraction();
    List<Interaction> getInteractionsFor(String logicalSource);
    Optional<CcrsTrace> getLastCcrsInvocation();
    List<CcrsTrace> getCcrsHistory(int maxCount);
    void recordCcrsInvocation(CcrsTrace trace);

    Optional<String> getCurrentResource();
    String getAgentId();
    boolean hasHistory();
    boolean hasLlmAccess();
    boolean hasConsultationChannel();
}
```

For Prediction, recent interactions are formatted with request headers and body, outcome, timing, and perceived RDF triples. This richer prompt representation is intentionally separate from the compact `Interaction.toString()` used in logs.

## Pluggable external services

Prediction and Consultation depend on narrow pluggable interfaces:

```java
public interface LlmClient {
    String complete(String prompt) throws Exception;
    boolean isAvailable();
}

public interface ConsultationChannel {
    boolean isAvailable();
    ConsultationResponse query(String question, Map<String, Object> context)
        throws Exception;
    String getChannelType();
}
```

Provider-specific collaborators remain in optional modules. [ContingencyCcrsFactory.java](ContingencyCcrsFactory.java) assembles built-in strategies and discovers optional [CcrsStrategyProvider.java](CcrsStrategyProvider.java) implementations through `ServiceLoader`. Providers receive the central configuration through [CcrsStrategyProviderContext.java](CcrsStrategyProviderContext.java).

## LLM prompt triple filtering

[PredictionLlmStrategy.java](strategies/internal/prediction/PredictionLlmStrategy.java) filters RDF triples before serializing them into an LLM prompt. By default, it removes triples whose subject, predicate, or object contains the `https://example.org/ui` namespace.

This is prompt shaping only. It does not remove data from `CcrsContext`, the belief base, interaction history, or other strategies. UI triples can dominate a token budget with presentation details such as layers, fills, and drawing properties while contributing little to recovery-action selection. Filtering keeps the prompt focused on actionable hypermedia state, links, interactions, and prior CCRS traces.

The setting is exposed by [PredictionLlmStrategyOptions.java](options/PredictionLlmStrategyOptions.java) and the central configuration, so factories and `ServiceLoader` providers receive the same behavior.

## Execution flow

```text
┌───────────────────────────────────────────────────────┐
│ Agent requests runtime guidance                      │
│ after observing failure, uncertainty, or no progress │
└──────────────────────────┬────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────┐
│ Build a type-free Situation                          │
│ from resources, failed action, error, and metadata   │
└──────────────────────────┬────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────┐
│ ContingencyCcrs.evaluate(situation, context)          │
│ creates the invocation trace                         │
└──────────────────────────┬────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────┐
│ Apply hard configuration filters                     │
│ enabled IDs/categories, maximum level, policy        │
└──────────────────────────┬────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────┐
│ Build the non-stop strategy selection plan           │
│ Normal: learned/default order and learned gates      │
│ Reconsideration: default order, learned gates bypassed│
└──────────────────────────┬────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────┐
│ For each non-stop candidate                          │
│ strategy.appliesTo(situation, context)               │
└───────────────┬──────────────────────────┬────────────┘
                │ NOT_APPLICABLE           │ APPLICABLE / UNKNOWN
                ▼                          ▼
┌───────────────────────────┐  ┌─────────────────────────┐
│ Record applicability      │  │ Apply learned gate      │
│ and continue              │  │ SKIP: trace and continue│
└───────────────┬───────────┘  └────────────┬────────────┘
                │                           │ ALLOW, or bypass active
                │                           ▼
                │              ┌────────────────────────┐
                │              │ strategy.evaluate(...)│
                │              │ Suggestion or NoHelp   │
                │              └────────────┬───────────┘
                │                           │
                └──────────────┬────────────┘
                               │
                               ▼
┌───────────────────────────────────────────────────────┐
│ Continue according to escalation policy              │
│ Retain Suggestions; trace NoHelp and learned skips   │
└──────────────────────────┬────────────────────────────┘
                           │ after candidates/policy stop
                           ▼
┌───────────────────────────────────────────────────────┐
│ Did any non-stop strategy produce a Suggestion?      │
└──────────────────┬─────────────────────────┬──────────┘
                   │ YES                     │ NO
                   ▼                         ▼
┌──────────────────────────────┐  ┌─────────────────────┐
│ Select results by confidence │  │ Evaluate Stop       │
│ Stop is not evaluated        │  │ against run history │
└──────────────────┬───────────┘  └──────────┬──────────┘
                   │                         │
                   │                         ▼
                   │              ┌───────────────────────────────┐
                   │              │ Stop result                   │
                   │              │ Not applicable/NoHelp: none  │
                   │              │ Reconsideration: bypass next  │
                   │              │ Suggestion: advise agent stop │
                   │              └──────────┬────────────────────┘
                   │                         │
                   └──────────────┬──────────┘
                                  │
                                  ▼
┌───────────────────────────────────────────────────────┐
│ Complete and record CcrsTrace exactly once           │
│ Return selected Suggestions, or an empty result list │
└───────────────────────────────────────────────────────┘
```

`evaluate(...)` returns the selected results. `evaluateWithTrace(...)` exposes the full trace for callers that need evaluation diagnostics directly.

## Java usage

```java
Situation situation = Situation.builder()
    .trigger("no_valid_transitions")
    .currentResource("http://example.org/cell/5")
    .targetResource("http://example.org/cell/exit")
    .failedAction("navigate")
    .errorInfo("errorType", "NO_PROGRESS")
    .errorInfo("message", "No valid transitions")
    .build();

ContingencyConfiguration config = ContingencyConfiguration.builder()
    .maxLevel(4)
    .maxSuggestions(3)
    .policy(ContingencyConfiguration.EscalationPolicy.PARALLEL)
    .build();

ContingencyCcrs ccrs = ContingencyCcrs.withDefaults(config);
List<StrategyResult> results = ccrs.evaluate(situation, context);

if (!results.isEmpty() && results.get(0) instanceof StrategyResult.Suggestion suggestion) {
    System.out.println("Action: " + suggestion.actionType());
    System.out.println("Target: " + suggestion.actionTarget());
    System.out.println("Confidence: " + suggestion.confidence());

    CcrsTrace trace = context.getLastCcrsInvocation().orElse(null);
    boolean success = executeAction(suggestion);
    if (trace != null) {
        trace.reportOutcome(
            success ? CcrsTrace.Outcome.SUCCESS : CcrsTrace.Outcome.FAILED,
            success ? "completed" : "action failed");
    }
}
```

Outcome reporting is important for learned selection and Stop's degradation-episode boundary. A successful outcome explicitly indicates that the run recovered.

## AgentSpeak usage

The JaCaMo adapter accepts a type-free `map(...)`, not a positional request category:

```asl
ccrs.jacamo.jason.contingency.evaluate(
    map(
        trigger("service_unavailable"),
        current(CurrentURI),
        target(TargetURI),
        action("POST"),
        http_status("503"),
        error_message("Service unavailable")
    ),
    Suggestions
);
```

The result is a list of `suggestion(...)` terms. Consumers should inspect the proposed action and confidence. A `stop` action is advice to consider ending the run, not an automatic termination command. See the [JaCaMo contingency README.md](../../../../../../../ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/README.md) for the adapter contract. The current [examples.asl](../../../../../../../ccrs-jacamo/src/main/resources/ccrs/jacamo/jason/contingency/examples.asl) contains application-owned handlers and will move with the BDI application during physical separation.

## Configuration

Use [ContingencyConfiguration.java](ContingencyConfiguration.java) for orchestration, learned selection, and built-in strategy properties:

```java
ContingencyConfiguration config = ContingencyConfiguration.builder()
    .maxLevel(4)
    .maxSuggestions(3)
    .policy(ContingencyConfiguration.EscalationPolicy.PARALLEL)
    .learnedSelection(true)
    .learningHistoryLimit(25)
    .minimumLearningSamples(2)
    .minimumExpectedConfidenceGain(0.10)
    .highConfidenceEvaluationFloor(0.80)
    .cheapEvaluationTimeMs(250)
    .predictionLlm(options -> options
        .maxHistoryActions(20)
        .maxInteractionStateTriples(50)
        .maxCcrsTraces(5))
    .retry(options -> options
        .maxAttempts(5)
        .initialDelayMs(500))
    .consultation(options -> options
        .maxRecentInteractions(8)
        .maxAgentCandidates(3))
    .stop(options -> options
        .noSuggestionInvocationThreshold(2)
        .lowConfidenceInvocationThreshold(3)
        .lowConfidenceThreshold(0.5)
        .selectionResetCountBeforeStop(1)
        .traceHistoryLookbackLimit(30))
    .build();

ContingencyCcrs ccrs =
    ContingencyCcrsFactory.withDefaultsAndDiscoveredProviders(config);
```

## Extension points

Implement [CcrsStrategy.java](CcrsStrategy.java) and register it in [StrategyRegistry.java](StrategyRegistry.java), or publish a [CcrsStrategyProvider.java](CcrsStrategyProvider.java) through `ServiceLoader`. A strategy supplies its ID, category, escalation level, applicability decision, evaluation result, and description. It must derive applicability from request evidence and context rather than requiring callers to preselect it.

Keep strategy implementations stateless. Run-specific evidence and history belong in `Situation` and `CcrsContext`; user-tunable behavior belongs in explicit configuration properties.
