# PLAN_CONTINGENCY_SITUATION_MODEL: Decouple runtime-guidance requests from a closed situation enum

This ExecPlan is a temporary, living design and implementation document. The sections `Rules`, `Progress`, `Surprises & Discoveries`, and `Decision Log` must be kept current as work proceeds. Work packages must be updated with their local context, discussion, todos, concrete steps, validation, and outcomes. The target is a full removal of situation types; WP1 implements the settled type-free Java contract and WP2 specifies the redesigned Stop/reconsideration safeguard.

The repository has no checked-in `PLANS.md` template. This plan follows the execution-plan guidance in [the repository AGENTS.md](../AGENTS.md) and uses the local `PLAN_<SCOPE>.md` convention. It is separate from the completed [contingency strategy configuration plan](PLAN_CONTINGENCY_STRATEGY_CONFIGURATION.md), although the two plans share Java-to-React validation concerns.

## Purpose / Big Picture

Contingency CCRS currently requires every runtime-guidance request to carry one of four caller-selected values: `FAILURE`, `STUCK`, `UNCERTAINTY`, or `PROACTIVE`. That value is required by [Situation.java](src/main/java/ccrs/core/contingency/dto/Situation.java), and some strategies trust it when deciding whether they apply. A caller can therefore enable or exclude recovery behavior by choosing a category before CCRS has examined the available facts.

After this refactor, a caller describes what was observed and requests runtime guidance without supplying any situation type, category, type hint, or replacement classification field. Strategy applicability follows the existing concrete fields and `CcrsContext` evidence rather than an unverified caller label. The result remains usable from Java, JaCaMo/Jason, and the React agent's JPype bridge, and traces remain useful for history, consultation, prompts, logs, and experiment reports.

The behavior is demonstrably improved when the same evidence produces the same applicable strategies regardless of whether a caller knows a predefined category name; a retriable failed action can still produce retry guidance; navigation history can still produce backtrack guidance; and a request outside today's four categories can reach general strategies without an enum change.

## Rules

- Rule: Remove `Situation.Type` completely and do not introduce a replacement type, category, tag, hint, or equivalent classification field.
  Reason: The caller-selected classification is the design flaw being removed, not an API shape to preserve under another name.
  Added/Updated: 2026-08-02.

- Rule: Do not add temporary compatibility code.
  Reason: This is a full refactor. Remove old Java builders and getters, JaCaMo signatures, Python enums and aliases, serialized fields, telemetry fields, tests, examples, and documentation in the same change.
  Added/Updated: 2026-08-02.

- Rule: Keep `ccrs-core` agent-agnostic and make Java core the semantic owner of contingency requests and strategy applicability.
  Reason: React and JaCaMo are adapters; they must not invent different situation semantics.
  Added/Updated: 2026-08-02.

- Rule: Separate the reason for invoking runtime guidance from the evidence a strategy uses to declare itself applicable.
  Reason: Invocation, problem description, strategy applicability, and strategy selection are different decisions in the current flow.
  Added/Updated: 2026-08-02.

- Rule: Preserve retry accounting by correlating actual retry evaluations through the failed action and target resource; do not introduce an operation-correlation field as part of this refactor.
  Reason: The current type equality is redundant because retry is evaluated only for requests with retriable failure evidence. The existing bounded grouping already treats the same action and target as one retry series. Stop no longer groups requests by situation identity; it evaluates the recent sequence of complete CCRS invocation outcomes.
  Added/Updated: 2026-08-02.

- Rule: Treat Stop as an advisory, two-stage escalation safeguard, never as an instruction that terminates the agent directly.
  Reason: CCRS can recognize persistently absent or weak guidance and recommend stopping, but only the consuming agent knows its remaining time, token, cost, safety, and task-specific obligations.
  Added/Updated: 2026-08-02.

- Rule: Before Stop may suggest termination, force at least one configurable one-invocation learned-selection bypass and record the request through Stop's typed `NoHelp` result in the normal CCRS trace.
  Reason: The trace-based selector can learn to prune strategies. A stop recommendation is not justified until CCRS has deliberately reconsidered candidates once without learned ordering or gating. Earlier learning remains available after that one invocation.
  Added/Updated: 2026-08-02.

- Rule: Expose Stop thresholds `x`, `y`, `z`, and `v` as end-user configuration properties through the same Java configuration, React/JPype mapping, examples, and documentation mechanisms as other strategy options.
  Reason: These values express application policy and cannot be fixed implementation constants.
  Added/Updated: 2026-08-02.

- Rule: Keep Stop's public result contract simple: it remains the sole `stop` suggestion with confidence `1.0` and gains no advisory booleans, confirmation flags, or parallel result type.
  Reason: Stop is evaluated only when the current invocation has produced no other suggestion. Extra result flags would duplicate the rationale without clarifying selection behavior.
  Added/Updated: 2026-08-02.

- Rule: Treat the in-memory `CcrsContext` history as one run-scoped stream and do not add a run ID or Stop episode-correlation field.
  Reason: Context state is in memory for one run. Stop assesses that complete run stream, while retry retains its separate operation-level correlation rule.
  Added/Updated: 2026-08-02.

- Rule: Migrate Java, JaCaMo, React/JPype, telemetry, experiment tooling, examples, tests, and documentation as one coordinated contract change.
  Reason: All of these areas expose or consume the current situation type.
  Added/Updated: 2026-08-02.

- Rule: Preserve historical experiment logs, LangSmith exports, CSV files, and generated reports as immutable evidence, but do not retain active compatibility branches solely to reparse their old situation-type fields.
  Reason: Historical outputs describe runs that actually used the old model; preserving files does not require preserving the old active schema.
  Added/Updated: 2026-08-02.

- Rule: Publish updated Java snapshots before React integration validation and use a fresh Python process or restarted notebook kernel for every republished Java model.
  Reason: JPype cannot unload already-loaded Java classes, and [java_runtime.py](../../ccrs-react/react_agent/ccrs/java_runtime.py) deliberately caches class objects and stable copies of Maven-local jars.
  Added/Updated: 2026-08-02.

- Rule: Preserve unrelated worktree changes, especially the existing edits to `ccrs-core/build.gradle`, `BacktrackStrategy.java`, the five strategy regression tests, the two React integration tests, `PLAN_CCRS_README.md`, and `test_agent.ipynb`.
  Reason: Those edits and new files predate this plan update and overlap files that the eventual refactor must migrate.
  Added/Updated: 2026-08-02.

- Rule: Update every public example and README in the same work package that changes its corresponding interface.
  Reason: The core README already contains a stale Java builder example, and this refactor will otherwise create more drift.
  Added/Updated: 2026-08-02.

## Now / Next / Later

| NOW | NEXT | LATER |
| --- | --- | --- |
| Completed: WP1-WP6 implemented and validated | Final audit complete | No deferred compatibility work |

## Progress

- [x] (2026-08-02) Read repository guidance, the existing contingency plans, the core and JaCaMo documentation, and the React adapter guidance.
- [x] (2026-08-02) Traced direct `Situation.Type`, `getType()`, `SituationType`, `situation_type`, and `situationType` uses through active Java, Python, AgentSpeak, PowerShell, tests, examples, and Markdown.
- [x] (2026-08-02) Separated active code and documentation from historical experiment artifacts that must remain unchanged.
- [x] (2026-08-02) Recorded the JPype/Maven-local lifecycle constraint from `java_runtime.py`.
- [x] (2026-08-02) Created this temporary plan and initial impact map.
- [x] (2026-08-02) Added the existing retry, backtrack, stop, prediction, consultation, JPype, and live A2A tests as mandatory regression/integration coverage.
- [x] (2026-08-02) Decided on a full refactor: remove situation types without a replacement classification and without compatibility code.
- [x] (2026-08-02) Specified the target Stop behavior as a two-stage, trace-driven reconsideration and advisory-stop state machine in WP2.
- [x] (2026-08-02) Refined WP2 from a permanent learning epoch reset to a one-invocation learned-selection bypass, retained Stop as the only confidence-1.0 suggestion, added reported-success recovery semantics, and made `x/y/z/v` explicit end-user properties.
- [x] (2026-08-02) Settled the remaining type-free applicability and retry-history rules and folded them into the Java implementation package instead of retaining a separate design work package.
- [x] (2026-08-02) Renamed Stop's bounded trace-scanning option to `traceHistoryLookbackLimit` / `trace_history_lookback_limit` so its scope is explicit.
- [x] (2026-08-02) Adopted and implemented the recorded defaults `x=2`, `y=3`, `z=0.5`, and `v=1`.
- [x] (2026-08-02) Implemented WP1-WP6 across Java core, JaCaMo, React/JPype, telemetry, tests, examples, and documentation.
- [x] (2026-08-02) Passed the full `ccrs-core` test suite, multi-module Java classes build, JaCaMo and standalone consumer compilation, Maven-local publication, all React unittest discovery tests, and both experiment-report fixture suites; live A2A remained correctly skip-safe because its local service was absent.

## Surprises & Discoveries

- Observation: The enum does not directly control `ContingencyCcrs` ordering or the trace-based selector; it gates `RetryStrategy` and `BacktrackStrategy` inside `appliesTo(...)`.
  Evidence: [RetryStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/RetryStrategy.java) requires `FAILURE`, while [BacktrackStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/BacktrackStrategy.java) requires `FAILURE` or `STUCK`. General prediction, consultation, and stop strategies do not use the enum as an applicability gate.

- Observation: The enum also acts as an implicit history-correlation key.
  Evidence: `RetryStrategy.situationMatchesForRetry(...)` and `StopStrategy.sameSituationType(...)` compare `Situation.getType()` before counting prior traces.

- Observation: The trace-based selection policy has no mutable learned model to clear.
  Evidence: [TraceBasedStrategySelectionPolicy.java](src/main/java/ccrs/core/contingency/selection/TraceBasedStrategySelectionPolicy.java) constructs a new model from `StrategySelectionRequest.recentTraces()` for every invocation, and [TraceBasedStrategySelectionModel.java](src/main/java/ccrs/core/contingency/selection/TraceBasedStrategySelectionModel.java) derives all profiles from that supplied history. A one-invocation reset is therefore implemented by bypassing model creation/order/gates for the invocation immediately following Stop's reset `NoHelp`, not by clearing an object or discarding old traces.

- Observation: The current L0 orchestration rule guarantees that Stop is the only suggestion when it is evaluated successfully.
  Evidence: [ContingencyCcrs.java](src/main/java/ccrs/core/contingency/ContingencyCcrs.java) breaks before L0 whenever `allSuggestions` is non-empty. Preserve this rule. Historical low-confidence guidance may arm Stop, but Stop waits until a later current invocation produces no suggestion.

- Observation: A strategy cannot directly alter the active selection plan because the plan is built before strategies are evaluated and `CcrsStrategy.evaluate(...)` receives only `Situation` and `CcrsContext`.
  Evidence: [ContingencyCcrs.java](src/main/java/ccrs/core/contingency/ContingencyCcrs.java) builds and applies the plan before Stop runs. Stop's reset `NoHelp` therefore arms a one-shot bypass for the next CCRS invocation, not strategies already skipped in the current invocation.

- Observation: Type names cross process and service boundaries even when they do not gate applicability.
  Evidence: [ConsultationStrategy.java](src/main/java/ccrs/core/contingency/strategies/social/ConsultationStrategy.java) emits `situationType` to consultation channels; [PredictionLlmStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategy.java) includes it in the LLM prompt; [CcrsTrace.java](src/main/java/ccrs/core/contingency/dto/CcrsTrace.java) prints it in trace summaries.

- Observation: The core README's Java example is already inconsistent with the current builder API.
  Evidence: The pre-refactor [core README](src/main/java/ccrs/core/contingency/README.md) called `Situation.builder().type(...)`, while the old `Situation.java` exposed `Situation.builder(Type)` and no `Builder.type(...)` method. The rewritten guide now uses `Situation.builder()`.

- Observation: React duplicates the Java enum and loads the nested Java enum class by its binary name.
  Evidence: [situation.py](../../ccrs-react/react_agent/ccrs/contingency/situation.py) defines `SituationType`; [contingency_ccrs.py](../../ccrs-react/react_agent/ccrs/contingency/contingency_ccrs.py) loads `ccrs.core.contingency.dto.Situation$Type`, calls `valueOf(...)`, constructs the Java builder, and serializes `getType()` back to Python.

- Observation: React notebook reload tolerance is part of the adapter behavior.
  Evidence: `Situation.from_value(...)` structurally normalizes stale same-shaped objects, and [PLAN_CCRS_README.md](../../ccrs-react/PLAN_CCRS_README.md) records an earlier stale `SituationType` class-identity failure.

- Observation: Experiment report generation has a live dependency on the old telemetry field.
  Evidence: [parse-experiment-logs.ps1](../../ccrs-react/experiments/scripts/parse-experiment-logs.ps1) captures and exports `situation_type`; [write-report.ps1](../../ccrs-react/experiments/scripts/write-report.ps1) renders it into contingency rationale text.

- Observation: A substantial strategy regression suite now exists in the working tree, but much of it deliberately asserts the enum-based behavior this refactor will replace.
  Evidence: [BacktrackStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/BacktrackStrategyTest.java) asserts applicability only for `FAILURE`/`STUCK`; [RetryStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/RetryStrategyTest.java) rejects non-`FAILURE` situations and groups attempts using the current model; [StopStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/StopStrategyTest.java) counts matching types and asserts a `situationType` action parameter.

- Observation: Prediction and consultation regression tests are affected even though their strategies accept any current situation type.
  Evidence: [PredictionLlmStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategyTest.java) constructs typed requests and verifies prepared prompt context; [ConsultationStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/social/ConsultationStrategyTest.java) constructs typed requests and verifies bounded question/context projection including previous traces.

- Observation: React already has a non-live JPype integration suite in addition to the live A2A smoke.
  Evidence: [test_jpype_contingency_integration.py](../../ccrs-react/tests/test_jpype_contingency_integration.py) exercises runtime class loading plus retry, backtrack, stop, configuration, context proxy, and result round trips. [test_live_a2a_contingency.py](../../ccrs-react/tests/test_live_a2a_contingency.py) exercises the discovered A2A consultation provider against the optional local key-holder service.

- Observation: A prior React plan already identified situation typing as underdeveloped.
  Evidence: [PLAN_CCRS_README.md](../../ccrs-react/PLAN_CCRS_README.md) says it is not clear that the four-value enum is the ideal long-term representation.

- Observation: React graph routing treated a Stop suggestion as an immediate termination command.
  Evidence: `route_after_ccrs_node(...)` returned `end` for an uncompleted result carrying `stop=true`. The refactor now always returns contingency guidance to the LLM so the consuming agent applies its own budget, safety, and task checks.

- Observation: `java_runtime.py` required no source change.
  Evidence: A fresh Python process loaded the newly published type-free `ccrs-core` artifact and passed the JPype retry, backtrack, configuration, trace, and multi-invocation Stop round trips. The generic classpath/fingerprinting lifecycle remained sufficient.

## Decision Log

- Decision: Superseded. The initial plan kept the replacement model open for WP1 discussion.
  Rationale: The user subsequently decided that types must disappear completely and that no compatibility code is wanted. The newer decision below controls the plan.
  Date/Author: 2026-08-02 / Codex.

- Decision: Treat the JaCaMo call signature, React tool schema, JPype class mapping, trace/log schema, and experiment parsers as part of the refactor rather than incidental cleanup.
  Rationale: They are active producers or consumers of the closed type set and can otherwise keep the old assumption alive outside Java core.
  Date/Author: 2026-08-02 / Codex.

- Decision: Preserve historical generated artifacts unchanged, but do not retain active old-schema parsing solely for those artifacts.
  Rationale: Rewriting old experimental evidence would misrepresent the software used for those runs, while active compatibility code would violate the full-refactor rule.
  Date/Author: 2026-08-02 / Codex.

- Decision: Place the cross-repository plan in `ccrs-bdi/ccrs-core` and link React files explicitly.
  Rationale: Java core owns the semantic model, while the relative links keep the adapter portion resumable from this one document.
  Date/Author: 2026-08-02 / Codex.

- Decision: Treat the five named Java strategy tests and two named React integration tests as the required regression suite for the refactor; migrate their assertions in place rather than replacing them with parallel tests.
  Rationale: These tests already capture strategy metadata, applicability, history limits, result construction, prompt/consultation projections, JPype conversion, context proxies, and live provider discovery. Preserving that coverage makes semantic changes explicit and protects behavior unrelated to the situation model.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: Fully remove situation types with no replacement classification and no temporary compatibility layer.
  Rationale: The closed type is itself the wrong design choice. Keeping deprecated builders, adapter translations, type aliases, hints, or dual schemas would preserve the same concept and leave two models to maintain.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: Keep the remaining `Situation` fields as the initial type-free input and derive strategy applicability from those fields plus `CcrsContext`.
  Rationale: Retry already has concrete failed-action, target, and error evidence. Backtrack already has current-resource and interaction-history evidence. Prediction, consultation, and stop do not require the enum as an applicability gate. New fields should be added only if a concrete behavior cannot be represented safely after removal.
  Date/Author: 2026-08-02 / Codex.

- Decision: Replace Stop's same-type attempt count with two independent degradation triggers over completed CCRS invocations: consecutive invocations with no non-stop suggestion, and recent suggestion-bearing invocations whose non-stop suggestions are all below a configurable confidence threshold.
  Rationale: These signals describe whether CCRS is producing usable guidance directly. They do not require a request category or an unreliable guess that two situations are the same kind.
  Date/Author: 2026-08-02 / user, refined and recorded by Codex.

- Decision: Superseded. The first WP2 draft implemented learned-selection reset as a persisted trace epoch boundary.
  Rationale: The policy is stateless, so an epoch boundary was technically workable, but permanently excluding prior learning is broader than required. The newer one-invocation bypass decision below controls the plan.
  Date/Author: 2026-08-02 / Codex.

- Decision: Implement reset as a one-invocation bypass of trace-learned ordering and gating, triggered by Stop's typed reset `NoHelp`; resume normal learned selection with the full retained history afterward.
  Rationale: This guarantees a fresh consideration of available strategies without throwing away useful learning. The reset marker already exists as a typed strategy result in the trace, so no additional reset fields are needed.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: A Stop trigger first requests a one-invocation learned-selection bypass and returns `NoHelp`. Stop suggests the advisory `stop` action only after the same degradation episode contains the configured number `v` of completed bypass cycles.
  Rationale: This guarantees deliberate reconsideration before recommending termination. With the proposed default `v = 1`, the first trigger arms the next invocation's bypass; if that invocation still produces no suggestion, Stop may then suggest stopping.
  Date/Author: 2026-08-02 / user, refined and recorded by Codex.

- Decision: A non-stop suggestion at or above the low-confidence threshold or a trace later reported as `Outcome.SUCCESS` ends the active degradation episode; Stop suggestions themselves never count as recovery evidence.
  Rationale: Reset counts must not leak indefinitely across recovered runs. A successful low-confidence suggestion is still evidence that guidance worked, while Stop's own high-confidence result must not make the next invocation look recovered.
  Date/Author: 2026-08-02 / Codex.

- Decision: The Stop recommendation remains advisory and contains no agent-specific budget policy.
  Rationale: Time, token, cost, and task-specific termination checks belong to the consuming agent. CCRS reports why it recommends considering stop; the agent retains the final decision.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: Keep Stop confidence at `1.0` and do not add advisory/confirmation flags or a new result type.
  Rationale: Existing orchestration evaluates Stop only after an invocation has produced no other suggestion, so Stop cannot win a confidence competition against recovery guidance. Its rationale communicates that the agent retains the final decision.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: Treat `x`, `y`, `z`, and `v` as public end-user properties and expose them everywhere other Stop strategy options are configured.
  Rationale: Different agents and applications have different tolerance for absent or weak runtime guidance and different requirements for forced reconsideration.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: Use the existing in-memory context as the one-run history scope and add no Stop-specific run or episode identifier.
  Rationale: State does not cross runs. A healthy or successful trace supplies the degradation-episode boundary within that run.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: Remove the standalone applicability/history specification package and fold its settled behavior into WP1, the Java core implementation package.
  Rationale: No model choice remains. Retry applies from failed action, target resource, and retriable error evidence; its existing bounded attempt series is identified by failed action plus target resource. Backtrack applies from a resolvable current resource plus available context history, with evaluation deciding whether that history contains a usable checkpoint. Keeping a separate design package would delay implementation without resolving a real open question.
  Date/Author: 2026-08-02 / user question, resolved by Codex.

- Decision: Name Stop's bounded trace-scanning option `traceHistoryLookbackLimit` in Java and `trace_history_lookback_limit` in React.
  Rationale: The value limits how many prior `CcrsTrace` records Stop scans; the more generic `historyLookbackLimit` could be confused with interaction, graph, or retry history.
  Date/Author: 2026-08-02 / user, recorded by Codex.

- Decision: React always returns an advisory Stop result to the LLM/agent instead of routing directly to graph `END`.
  Rationale: Automatic graph termination would override the agreed requirement that agent-specific time, token, safety, and task checks retain the final stopping decision.
  Date/Author: 2026-08-02 / Codex, implementing the user's advisory-stop rule.

## Context and Orientation

Contingency CCRS is the runtime-guidance part of CCRS. A caller constructs a [Situation.java](src/main/java/ccrs/core/contingency/dto/Situation.java) and supplies a `CcrsContext`, which exposes current resources, interaction history, RDF knowledge, and earlier CCRS traces. [ContingencyCcrs.java](src/main/java/ccrs/core/contingency/ContingencyCcrs.java) obtains registered strategies, asks each strategy's `appliesTo(...)` method whether it is a candidate, evaluates allowed candidates, ranks suggestions, and records a `CcrsTrace`.

Before this refactor, `Situation` contained a required closed `Type`. The implemented model retains only an optional textual trigger, current and target resources, a failed action, error information, and metadata; strategies derive applicability from that evidence and context.

Strategy applicability and strategy selection remain separate. Applicability is the strategy-local decision in [CcrsStrategy.java](src/main/java/ccrs/core/contingency/CcrsStrategy.java): for example, retry inspects action, target, and retriable error evidence. Strategy selection is the later orchestration policy that gates and orders already registered candidates based on escalation configuration and trace history.

The request reaches Java through three active paths. Java library consumers call the DTO directly. JaCaMo agents call the internal action in [evaluate.java](../ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/evaluate.java), whose first argument is the type string. React creates a Python `Situation`, stores it transiently in LangGraph state, and [contingency_ccrs.py](../../ccrs-react/react_agent/ccrs/contingency/contingency_ccrs.py) translates it through JPype into the Java DTO.

### Settled type-free applicability and history behavior

The data-model direction and remaining strategy rules are settled; they are implementation requirements in WP1 rather than a separate design phase:

- Retry is applicable only when `failedAction` and `targetResource` are present and `errorInfo` contains a retriable HTTP status or error type. Its bounded attempt history counts prior retry evaluations with the same non-null failed action and target resource. Removing type equality does not broaden that series because only actual retry evaluations are counted. Do not add a new operation-correlation field in this refactor.
- Backtrack is applicable when the current resource can be resolved from the request or context and `context.hasHistory()` is true. This remains a cheap admission check; evaluation inspects the history and returns `NoHelp` when no usable checkpoint or alternative exists.
- Stop does not use a recovery-attempt identity. Under WP2 it observes prior `CcrsTrace` records from the one-run in-memory context, detects absent or persistently weak non-Stop guidance, and counts completed one-invocation learned-selection bypass cycles within the current degradation episode.
- Prediction and consultation receive the trigger, resources, failed action, errors, metadata, and bounded history without a synthetic summary label.
- Traces and audit events report concrete request facts. Aggregation by the old four categories intentionally disappears.

### Impact map

“Change” means the file is expected to be edited while executing the work packages. “Review” means its public wording or compile-time relationship must be checked, but the accepted design may leave it unchanged.

| Area | File | Impact | Why |
| --- | --- | --- | --- |
| Java public model | [Situation.java](src/main/java/ccrs/core/contingency/dto/Situation.java) | Change | Owns the enum, required field, getter, builder signatures, convenience builders, validation, and `toString()` output. |
| Java strategy contract | [CcrsStrategy.java](src/main/java/ccrs/core/contingency/CcrsStrategy.java) | Review/change | `appliesTo(Situation, CcrsContext)` is the semantic boundary; its signature may stay, but its contract and explanation requirements must change. |
| Java orchestration | [ContingencyCcrs.java](src/main/java/ccrs/core/contingency/ContingencyCcrs.java) and [StrategySelectionRequest.java](src/main/java/ccrs/core/contingency/selection/StrategySelectionRequest.java) | Change/test | Carry the type-free request through selection and traces; preserve the unconditional “any current suggestion skips L0” rule and detect the immediately preceding typed Stop reset `NoHelp` so the next invocation bypasses learned ordering/gating once. |
| Retry applicability/history | [RetryStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/RetryStrategy.java) | Change | Removes the `FAILURE` gate; applicability uses the existing action/target/retriable-error evidence, and bounded retry attempts match the same failed action and target resource. |
| Backtrack applicability | [BacktrackStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/BacktrackStrategy.java) | Change | Removes the `FAILURE`/`STUCK` gate while preserving resource/history prerequisites. |
| Stop behavior | [StopStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/StopStrategy.java) | Change | Replace same-type attempt counting with no-suggestion and low-confidence invocation triggers, emit a typed reset `NoHelp` before any stop suggestion, count completed one-shot bypass cycles in the active degradation episode, and build the Stop rationale from the observed thresholds. |
| Stop configuration | [StopStrategyOptions.java](src/main/java/ccrs/core/contingency/options/StopStrategyOptions.java) and [ContingencyConfiguration.java](src/main/java/ccrs/core/contingency/ContingencyConfiguration.java) | Change | Remove `requireExhaustion`, `exhaustionThreshold`, and `stopLookbackLimit`; add public end-user properties for `x`, `y`, `z`, `v`, and the `traceHistoryLookbackLimit` bound through the same builder/customizer APIs as other strategy options. |
| Learned selection bypass | [TraceBasedStrategySelectionPolicy.java](src/main/java/ccrs/core/contingency/selection/TraceBasedStrategySelectionPolicy.java) and [TraceBasedStrategySelectionModel.java](src/main/java/ccrs/core/contingency/selection/TraceBasedStrategySelectionModel.java) | Review/test | Their normal learned behavior may remain unchanged because `ContingencyCcrs` can bypass plan construction for one invocation. Prove they resume with the full retained history afterward; edit them only if a small explicit bypass-plan abstraction is needed. |
| LLM input | [PredictionLlmStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategy.java) | Change | Replaces the “Situation type” prompt line with the accepted structured description. |
| Consultation payload | [ConsultationStrategy.java](src/main/java/ccrs/core/contingency/strategies/social/ConsultationStrategy.java) | Change | Replaces `situationType` and type-only historical summaries sent to A2A/other consultation channels. |
| Trace representation | [CcrsTrace.java](src/main/java/ccrs/core/contingency/dto/CcrsTrace.java) | Change | Replace type-only summaries and expose helpers for non-Stop suggestions, maximum confidence, successful outcomes, and typed Stop reset `NoHelp` detection. Do not add reset/advisory booleans. |
| History helpers | [CcrsTraceHistoryAnalyzer.java](src/main/java/ccrs/core/rdf/CcrsTraceHistoryAnalyzer.java) | Review/test | Generic filters can likely remain, but new correlation predicates depend on its counting semantics. |
| Backtrack regression | [BacktrackStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/BacktrackStrategyTest.java) | Change | Replace enum-gate assertions while preserving metadata, missing-resource, graph traversal, exhausted alternative, checkpoint, confidence, and option coverage. |
| Retry regression | [RetryStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/RetryStrategyTest.java) | Change | Replace `FAILURE` gating and type-based attempt grouping while preserving retriable codes, required retry identity, backoff, confidence, limits, and option snapshots. |
| Stop regression | [StopStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/StopStrategyTest.java) | Change | Replace matching-type exhaustion, terminal-status shortcuts, immediate-stop mode, and `situationType` assertions with exact no-suggestion, low-confidence, completed-bypass, confidence/success recovery-boundary, rationale, and option-bound tests. |
| Stop/selector orchestration regression | New `ContingencyCcrsStopReconsiderationTest.java` and `ContingencyCcrsLearnedSelectionBypassTest.java` under `src/test/java/ccrs/core/contingency` | Add | Prove the complete multi-invocation sequence: trigger, typed reset `NoHelp`, one-shot ungated reconsideration, learned-selection resumption, repeated bypass cycles, sole advisory Stop suggestion, confidence recovery, and reported-success recovery. |
| Prediction regression | [PredictionLlmStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategyTest.java) | Change | Migrate typed construction and prompt-context assertions while preserving LLM access/location applicability, parsing, confidence fallback, bounds, filtering, error mapping, and options. |
| Consultation regression | [ConsultationStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/social/ConsultationStrategyTest.java) | Change | Migrate typed construction and context/history projection while preserving channel/peer prerequisites, no-help cases, confidence, artifact projection, bounds, and options. |
| Java consumer example | [CcrsLibraryConsumer.java](../examples/ccrs-library-consumer/src/main/java/example/CcrsLibraryConsumer.java) | Changed | Previously called `Situation.failure(...)`; now demonstrates the type-free public Java API. |
| JaCaMo public boundary | [evaluate.java](../ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/evaluate.java) | Change | First argument, parser, builder call, errors, Javadoc, and `situation_type` event field encode the four-value set. |
| JaCaMo example | [examples.asl](../ccrs-jacamo/src/main/resources/ccrs/jacamo/jason/contingency/examples.asl) | Change | AgentSpeak calls pass legacy type strings and comments teach the classification rule. |
| Python input model | [situation.py](../../ccrs-react/react_agent/ccrs/contingency/situation.py) | Change | Duplicates the enum, requires `type`, accepts five type-key aliases, and normalizes old notebook objects. |
| Python-Java bridge | [contingency_ccrs.py](../../ccrs-react/react_agent/ccrs/contingency/contingency_ccrs.py) | Change | Loads `Situation$Type`, calls `valueOf`, selects a typed builder, logs `situation_type`, and serializes `getType()`. |
| Shared Java runtime | [java_runtime.py](../../ccrs-react/react_agent/ccrs/java_runtime.py) | Review/validate | Generic class loading may need no edit, but cached Java classes, stable jar copies, classpaths, and process restart behavior govern the migration test procedure. |
| React explicit escalation | [escalation.py](../../ccrs-react/react_agent/ccrs/contingency/escalation.py) | Change | The LLM tool schema exposes the four categories, defaults to `UNCERTAINTY`, and builds a typed situation. |
| React automatic escalation | [default_escalation_controller.py](../../ccrs-react/react_agent/ccrs/contingency/default_escalation_controller.py) | Change | Remove hard-coded `FAILURE`; continue populating trigger, resources, failed action, error information, and metadata. |
| React package API | [__init__.py](../../ccrs-react/react_agent/ccrs/contingency/__init__.py) | Change | Publicly exports `SituationType`. |
| React graph/state boundary | [ccrs_node.py](../../ccrs-react/react_agent/ccrs/ccrs_node.py), [state.py](../../ccrs-react/react_agent/ccrs/state.py), and [decision.py](../../ccrs-react/react_agent/ccrs/contingency/decision.py) | Review/test | Carry `contingency_situation` structurally; likely no semantic logic change, but type-free mappings and annotations must be verified. |
| React JPype integration | [test_jpype_contingency_integration.py](../../ccrs-react/tests/test_jpype_contingency_integration.py) | Change | Remove `SituationType` and type-bearing mapping inputs while preserving real Maven-local class loading, default strategy registration, Python context proxy, retry/backtrack round trips, all public Stop property mappings, and the typed-reset/bypass/sole-Stop sequence. |
| React live A2A integration | [test_live_a2a_contingency.py](../../ccrs-react/tests/test_live_a2a_contingency.py) | Change | Replace the `UNCERTAINTY` input while preserving discovered A2A provider applicability and the live consultation suggestion assertions. |
| React notebook | [test_agent.ipynb](../../ccrs-react/test_agent.ipynb) | Review | Renamed modules and public objects must remain in dependency-safe reload order; validate only in a restarted kernel after JPype tests pass. |
| Experiment parser | [parse-experiment-logs.ps1](../../ccrs-react/experiments/scripts/parse-experiment-logs.ps1) | Change | Remove `situation_type` ingestion/export and retain only concrete request fields for new runs. |
| Experiment report writer | [write-report.ps1](../../ccrs-react/experiments/scripts/write-report.ps1) | Change | Remove `situation_type` rendering and build request context from concrete fields in the new CSV schema. |
| Core documentation | [contingency README.md](src/main/java/ccrs/core/contingency/README.md) | Change | Describes enum semantics, strategy applicability, JaCaMo signature, flow diagram, and an already-invalid Java builder example. |
| Selection documentation | [selection README.md](src/main/java/ccrs/core/contingency/selection/README.md) | Change | Must explain the one-invocation bypass, resumption of retained learning, and the distinction between learned gating and fixed orchestration rules. |
| Context/history documentation | [RDF context README.md](src/main/java/ccrs/core/rdf/README.md) | Review/change | Must make in-memory one-run scoping and exactly-once trace recording explicit because Stop assesses the ordered invocation stream and consumes typed reset results from history. |
| JaCaMo documentation | [JaCaMo contingency README.md](../ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/README.md) | Change | Defines the four situation types, signatures, best practices, strategy matrix, and troubleshooting advice. |
| React documentation | [React CCRS README.md](../../ccrs-react/react_agent/ccrs/README.md) | Change | Documents the Python-to-Java situation bridge, state flow, concept map, and JPype lifecycle. |
| Java example documentation | [consumer README.md](../examples/ccrs-library-consumer/README.md) | Change | Describes construction of a retryable `Situation`. |
| Existing plans | [PLAN_CCRS_README.md](../../ccrs-react/PLAN_CCRS_README.md) and [PLAN_CONTINGENCY_STRATEGY_CONFIGURATION.md](PLAN_CONTINGENCY_STRATEGY_CONFIGURATION.md) | Change narrowly | Update current validation/examples or add a supersession note without rewriting historical decisions and completed progress. |
| Repository guidance | [ccrs-bdi AGENTS.md](../AGENTS.md) and [ccrs-react AGENTS.md](../../ccrs-react/AGENTS.md) | Review | Execution-plan discovery already exists; only update conceptual wording if the accepted terminology makes it stale. |

Historical files under `ccrs-react/experiments/runs`, `experiments/reports`, `experiments/backup`, and `experiments/langsmith` are explicitly out of the edit set. They remain immutable evidence of old runs, not migration targets or a reason to preserve old-schema parsing.

## Work Packages

### WP1: Refactor the type-free Java core and strategy semantics

Status: Completed and validated on 2026-08-02

Purpose: Remove the type field from Java core and prove that built-in strategies use relevant evidence rather than a caller category.

Local context: The main type-removal edits are [Situation.java](src/main/java/ccrs/core/contingency/dto/Situation.java), [RetryStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/RetryStrategy.java), [BacktrackStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/BacktrackStrategy.java), [PredictionLlmStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategy.java), [ConsultationStrategy.java](src/main/java/ccrs/core/contingency/strategies/social/ConsultationStrategy.java), and their trace projections. Stop, selection bypass, and the associated `CcrsTrace`/`ContingencyCcrs` changes follow WP2. `ContingencyCcrs` continues to orchestrate strategies and selection rather than classify requests itself. The five existing strategy test classes are the regression baseline and must be migrated alongside their production classes.

Discussion: Most enum uses are deleted without replacement. Retry applicability uses non-null failed action and target plus retriable error evidence. Its bounded attempt count continues to group actual retry evaluations by exact failed-action and target-resource equality; the removed type comparison contributed no finer identity because retry could only evaluate `FAILURE` requests. This refactor does not add an operation ID or attempt-series field. Backtrack's cheap admission check uses a resolvable current resource plus `context.hasHistory()`; evaluation remains responsible for finding a usable checkpoint and returns `NoHelp` if none exists. Prediction and consultation remain generally type-agnostic. Keep prompt and consultation projections bounded; do not dump arbitrary metadata merely to replace the missing type string.

Todos:

- [x] Add focused tests proving `Situation.builder()` works without a type and that no type API remains.
- [x] Update [BacktrackStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/BacktrackStrategyTest.java) so applicability follows resolvable-current-resource plus `context.hasHistory()` evidence, while retaining all graph, checkpoint, no-help, confidence, metadata, and option regressions.
- [x] Update [RetryStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/RetryStrategyTest.java) so applicability follows action/target/retriable-error evidence and bounded attempt counts match actual retry evaluations by failed action plus target resource, while retaining retriable-code, backoff, confidence, exhaustion, and option regressions.
- [x] Implement the WP2 Stop and selection-bypass contract together with the rewritten [StopStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/StopStrategyTest.java) and new orchestration/policy tests; do not retain the old exhaustion model.
- [x] Update [PredictionLlmStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategyTest.java) so prompt assertions cover the type-free request representation while preserving response parsing, failure mapping, prompt bounds/filtering, and option behavior.
- [x] Update [ConsultationStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/social/ConsultationStrategyTest.java) so consultation context and prior-trace assertions cover the type-free request representation while preserving channel, peer, confidence, bounds, artifact, and no-help behavior.
- [x] Add an orchestration test proving a request outside the former four categories reaches general strategies using only its concrete fields.
- [x] Update strategy logs, prompts, consultation maps, trace summaries, and suggestion parameters to the type-free representation.
- [x] Remove `Situation.Type`, `getType()`, typed builder construction, `failure(...)`, `stuck(...)`, and every old Java entry point rather than deprecating them.

Concrete steps: From `S:\dev\ma\ccrs-bdi`, run focused tests after each strategy change:

    rtk .\gradlew.bat :ccrs-core:test --tests ccrs.core.contingency.strategies.internal.BacktrackStrategyTest --tests ccrs.core.contingency.strategies.internal.RetryStrategyTest --tests ccrs.core.contingency.strategies.internal.StopStrategyTest --tests ccrs.core.contingency.strategies.internal.prediction.PredictionLlmStrategyTest --tests ccrs.core.contingency.strategies.social.ConsultationStrategyTest

Then compile all Java library consumers:

    rtk .\gradlew.bat classes

Validation and acceptance: All five named strategy test classes pass with no deleted regression scenarios unless the plan records why a scenario became invalid. A request with retriable error evidence selects retry without requiring a category. Retry traces for a different failed action or target do not consume the current series's attempt allowance. A navigation request with a resolvable current resource and any context history admits backtrack, while evaluation returns `NoHelp` when no usable checkpoint exists. A request with neither prerequisite does not admit those strategies. Prediction and consultation tests prove that the type-free request description reaches prompts and external consultation context without losing existing bounds or failure behavior.

Outcome and notes: The Java model and all five named strategy suites are type-free. Retry correlates action plus target; Backtrack uses current resource plus history. `:ccrs-core:test` and the full Java classes build pass.

### WP2: Redesign Stop as an advisory reconsideration safeguard

Status: Completed and validated on 2026-08-02

Purpose: Make Stop useful as a deliberate safety escalation. Stop observes whether contingency CCRS has repeatedly failed to provide usable guidance. Before it recommends that the agent consider terminating its run, it bypasses trace-learned ordering and gates for one invocation so previously pruned strategies receive a fresh opportunity. Stop never terminates the agent. It eventually returns an advisory `stop` suggestion that the agent may accept or reject after applying its own time, token, cost, safety, and task-specific checks.

Local context: [ContingencyCcrs.java](src/main/java/ccrs/core/contingency/ContingencyCcrs.java) builds a selection plan, evaluates normal recovery strategies, and places L0 Stop last. It already skips Stop whenever any current strategy produced a suggestion; preserve that behavior. [TraceBasedStrategySelectionPolicy.java](src/main/java/ccrs/core/contingency/selection/TraceBasedStrategySelectionPolicy.java) is stateless: every invocation creates a new [TraceBasedStrategySelectionModel.java](src/main/java/ccrs/core/contingency/selection/TraceBasedStrategySelectionModel.java) from recent [CcrsTrace.java](src/main/java/ccrs/core/contingency/dto/CcrsTrace.java) records. A reset therefore means bypassing learned ordering and gating for exactly the invocation immediately after a typed Stop reset `NoHelp`, then resuming normal learning with the retained history. [StopStrategy.java](src/main/java/ccrs/core/contingency/strategies/internal/StopStrategy.java) currently counts same-type traces and [StopStrategyOptions.java](src/main/java/ccrs/core/contingency/options/StopStrategyOptions.java) exposes the old exhaustion model; both contracts are replaced completely.

#### Behavioral vocabulary

A **completed invocation** is a `CcrsTrace` returned by one finished contingency evaluation and recorded in `CcrsContext`. Its later agent-reported `Outcome` may still be `PENDING` or `UNKNOWN`; Stop is measuring the quality of guidance CCRS produced, not whether the agent executed it successfully.

A **non-Stop suggestion** is a selected `StrategyResult.Suggestion` whose strategy ID is not `stop`. Stop's own advisory suggestion is excluded from every guidance-quality calculation.

A **no-guidance invocation** contains zero non-Stop suggestions. It does not matter whether strategies were inapplicable, gated, failed, or returned `NoHelp`; from the agent's perspective CCRS supplied no action to consider.

A **low-confidence invocation** contains at least one non-Stop suggestion and the maximum confidence among those suggestions is strictly less than `z`. “All suggestions are below `z`” is therefore computed as `max(nonStopSuggestion.confidence) < z`. A confidence equal to `z` is not low.

A **healthy invocation** contains at least one non-Stop suggestion with confidence greater than or equal to `z`, or its trace has subsequently been reported as `CcrsTrace.Outcome.SUCCESS`. It ends the current degradation episode and clears the effective Stop bypass count. A reported success overrides the original confidence classification because even low-confidence guidance that worked is recovery evidence. Outcome reporting is not yet wired end to end, but the Java behavior and regression coverage must be implemented now so later reporting works without redesigning Stop.

A **degradation episode** is the contiguous recent history after the latest healthy or successfully reported invocation. No-guidance invocations, low-confidence invocations, Stop reset `NoHelp` results, and ignored Stop suggestions can belong to the episode. History before its recovery boundary cannot contribute triggers or bypass counts.

A **selection bypass request** is Stop's typed reset `NoHelp` result recorded through the existing `CcrsTrace.StrategyEvaluation` structure. When it appears in the immediately preceding trace, the next invocation uses default registry order and bypasses all trace-learned gates once. No history is deleted or excluded from later models. After that invocation, normal learned selection resumes using the full bounded history, including the new reconsideration evidence.

An **ungated reconsideration** means candidates are not skipped or reordered by trace-derived profiles for one invocation. It does not override explicit strategy enable/disable lists, category filters, `maxEscalationLevel`, strategy applicability, or the configured `SEQUENTIAL`, `PARALLEL`, or `BEST_PER_LEVEL` orchestration semantics. “Consider all available strategies” in this plan means all candidates permitted by those non-learned rules.

#### Configuration contract

Replace the old `requireExhaustion`, `exhaustionThreshold`, and `stopLookbackLimit` options; do not retain aliases or translation branches. The following values are public end-user properties, not internal constants. Expose them through immutable `StopStrategyOptions`, `StopStrategyOptions.Builder`, `toBuilder()`, the `ContingencyConfiguration.Builder.stop(...)` customizer, React's snake-case/camel-case configuration mapping, Java/Python examples, and the relevant READMEs:

- `noSuggestionInvocationThreshold` is `x`, an integer of at least `1`. Proposed default: `2`.
- `lowConfidenceInvocationThreshold` is `y`, an integer of at least `1`. Proposed default: `3`.
- `lowConfidenceThreshold` is `z`, a finite double clamped to `[0.0, 1.0]`. Proposed default: `0.5`.
- `selectionResetCountBeforeStop` is `v`, an integer of at least `1`. Proposed default: `1`. A value of `1` means the first qualifying Stop evaluation requests a bypass and returns `NoHelp`; if the immediately following bypassed invocation still produces no suggestion, Stop may suggest stopping at its end.
- `traceHistoryLookbackLimit` bounds how many prior `CcrsTrace` records Stop scans and is at least `max(x, y, v + 1)`. Proposed default: `30`. It does not limit interaction history, RDF/graph history, or Retry's separate trace lookback. Configuration normalization must raise too-small values to the computed minimum rather than silently making a configured trigger impossible.

The defaults are conservative initial values, not compatibility defaults. They keep the current rough two-invocation patience for absent guidance, require more evidence for merely weak guidance, and guarantee one ungated reconsideration before a stop recommendation. If maintainers choose different initial values, update the Decision Log and every example/test atomically.

The Java end-user shape must remain consistent with other strategy properties:

    ContingencyConfiguration.builder()
        .stop(options -> options
            .noSuggestionInvocationThreshold(2)
            .lowConfidenceInvocationThreshold(3)
            .lowConfidenceThreshold(0.5)
            .selectionResetCountBeforeStop(1)
            .traceHistoryLookbackLimit(30))
        .build();

The React end-user mapping exposes the same values without changing their meaning:

    {
        "stop": {
            "no_suggestion_invocation_threshold": 2,
            "low_confidence_invocation_threshold": 3,
            "low_confidence_threshold": 0.5,
            "selection_reset_count_before_stop": 1,
            "trace_history_lookback_limit": 30,
        }
    }

Configuration tests must prove that all five properties survive builder snapshots, `toBuilder()`, `ContingencyConfiguration`, and Python-to-Java conversion. Documentation must explain `x`, `y`, `z`, and `v` in behavioral terms rather than exposing only the longer property names.

#### Trigger computation

At the beginning of an invocation, derive a bounded `StopHistoryAssessment` from prior recorded traces, newest first. The assessment excludes Stop suggestions and stops scanning at the latest invocation that either contains confidence-at-least-`z` non-Stop guidance or has been reported as `Outcome.SUCCESS`.

The no-guidance trigger is active when the newest `x` completed invocations are all no-guidance invocations. This condition is strictly consecutive; one suggestion-bearing invocation breaks the streak even when its confidence is low.

The low-confidence trigger is active when at least `y` suggestion-bearing invocations exist in the current degradation episode and every one of the `y` most recent suggestion-bearing invocations is low-confidence. Intervening no-guidance invocations neither increment nor clear this count. A healthy invocation ends the episode, so older weak suggestions cannot accumulate forever.

Stop becomes historically eligible when either trigger is active. Record which trigger or triggers fired, the observed counts, `x`, `y`, `z`, history length, and the active completed-bypass count in the `NoHelp` explanation or final rationale; do not add separate advisory flags. Do not correlate these invocations by `Situation.Type`, trigger text, resource, failed action, or a replacement category. The existing in-memory `CcrsContext` represents one run, so Stop intentionally evaluates that complete ordered run stream. No new run ID or Stop episode ID is needed. Document this one-run assumption in [the RDF context README.md](src/main/java/ccrs/core/rdf/README.md).

The current invocation is not yet in history while Stop is evaluated. Preserve the existing L0 admission rule in [ContingencyCcrs.java](src/main/java/ccrs/core/contingency/ContingencyCcrs.java): if any non-Stop strategy produces any suggestion in the current invocation, skip Stop regardless of that suggestion's confidence. Stop is evaluated only when the current invocation has no suggestion, so a resulting confidence-`1.0` Stop result is necessarily the only suggestion.

The historical trigger deliberately uses only prior completed invocations. Consequently, `y` low-confidence invocations can arm Stop, but continuing to receive a current low-confidence suggestion keeps Stop from running. Stop runs when a later current invocation produces no suggestion while the historical trigger remains active. This consequence is intentional and must have a regression test.

#### Two-stage reset and stop state machine

When a trigger is active, the current invocation has no suggestion, and fewer than `v` completed one-shot bypass cycles exist in the current degradation episode, Stop must:

1. Return `StrategyResult.NoHelp` with a dedicated machine-readable reason such as `SELECTION_RECONSIDERATION_REQUESTED`; do not encode the directive only in explanation text.
2. Let the existing trace machinery record that typed Stop evaluation result. Do not add `selectionResetRequested`, `selectionResetApplied`, advisory, or confirmation fields.
3. Produce no selected Stop suggestion.
4. Leave the current selection plan untouched because it has already run. The typed `NoHelp` arms the next invocation's one-shot bypass.

On the next invocation, `ContingencyCcrs` detects that the immediately preceding trace contains Stop's typed reset `NoHelp` and bypasses the trace-based selection plan for this invocation only. Strategies use default registry order and no learned gate. Explicit configuration, applicability, and escalation policy still apply. The reset request must remain detectable even when verbose per-strategy tracing is disabled, so the minimal Stop control evaluation is always retained. If learned selection is disabled, the same reset/NoHelp delay remains deterministic while reconsideration is naturally already ungated.

One reset `NoHelp` followed by its immediately subsequent contingency invocation is one completed bypass cycle. For historical cycles, this is derived by pairing the reset-result trace with the next newer trace. During the currently executing bypass, the newer trace does not exist yet; if Stop is reached, it can infer completion because the immediately preceding trace is the reset `NoHelp` that caused `ContingencyCcrs` to bypass learned selection for the current invocation. No stored “applied” flag is required. If the bypassed invocation produces a suggestion, Stop is skipped. A high-confidence suggestion ends the degradation episode; a low-confidence suggestion preserves the episode but normal learned selection resumes on the following invocation. If the bypassed invocation produces no suggestion, Stop is reached at its end and may either request another bypass or suggest stopping according to `v`.

When the historical trigger remains active, the current invocation again has no suggestion, and `v` bypass cycles have been completed in the current degradation episode, Stop returns a suggestion with action type `stop`. With `v = 1`, this can occur at the end of the first bypassed invocation when it still produced no recovery suggestion. With larger `v`, Stop emits another typed reset `NoHelp` until the configured number of cycles has occurred.

If the agent rejects or ignores a Stop suggestion and later invokes contingency CCRS again without a healthy boundary, Stop may suggest `stop` again. CCRS must not mutate agent state, cancel work, throw a termination exception, or report the run as stopped merely because it emitted the suggestion.

#### Reset representation and policy responsibilities

Use the existing `CcrsTrace.StrategyEvaluation` and `StrategyResult.NoHelp` structures as the reset representation. Add one dedicated `NoHelpReason` value so detection is typed; never parse explanation or logger text. Add trace helper methods if they improve readability, such as `didStrategyReturnNoHelp("stop", SELECTION_RECONSIDERATION_REQUESTED)`, but do not duplicate the fact into new stored booleans or metadata.

`ContingencyCcrs` owns inspecting the immediately preceding trace before plan construction and selecting the one-shot default-order/no-learned-gate path. `TraceBasedStrategySelectionPolicy` and its model remain unchanged in their normal learned behavior; they are simply bypassed for that invocation. `StopStrategy` owns historical trigger assessment, completed-bypass counting from the ordered traces, `NoHelp` versus suggestion choice, and rationale construction. Stop does not receive or mutate the selection policy.

Verbose tracing may control whether ordinary strategy evaluations are retained, but it must not remove selected results, outcomes, or the minimal typed Stop reset `NoHelp` required by the control flow. Java `evaluate()` records its returned trace. Callers of `evaluateWithTrace()`, including the React bridge, remain responsible for recording it exactly once; integration tests must prove that the one-shot bypass survives that path.

#### Advisory suggestion and rationale

The final suggestion remains `strategyId=stop`, `actionType=stop`, `actionTarget=null`, and confidence `1.0`. Stop is evaluated only when no recovery suggestion exists in the current invocation, so this result is the sole suggestion and does not outrank another action. Its rationale explains that CCRS recommends considering termination while the agent retains the final decision. Do not add `advisory`, `requiresAgentDecision`, `terminatesRun`, confirmation, or equivalent flags, and do not introduce a separate result type.

Replace `situationType`, terminal-HTTP shortcuts, and the vague `attemptedCount` parameter. The action parameters must include stable machine-readable evidence:

- `trigger`: `no_suggestions`, `low_confidence`, or `both`;
- `consecutiveNoSuggestionCount` and configured `noSuggestionInvocationThreshold`;
- `recentLowConfidenceCount`, configured `lowConfidenceInvocationThreshold`, and `lowConfidenceThreshold`;
- `completedSelectionBypassCount` and configured `selectionResetCountBeforeStop`;
- `traceHistoryLookbackLimit`;
- bounded final request/error context useful to the agent, without a situation category.

The human-readable rationale must say that CCRS is recommending that the agent **consider** stopping, not commanding termination. It must identify the observed pattern, state how many one-shot learned-selection bypasses occurred, explain that strategies were reconsidered without learned gating before escalation, and remind the consumer that final termination depends on agent-specific checks such as remaining time, token/cost budget, safety constraints, task obligations, and whether a partial result can still be returned.

#### Representative sequences

With defaults `x=2`, `y=3`, `z=0.5`, and `v=1`:

    Trace A: no non-Stop suggestion
    Trace B: no non-Stop suggestion
    Invocation C: historical x-trigger is active; current guidance is absent
                  -> Stop returns typed reset NoHelp; C arms bypass cycle #1
    Invocation D: learned selection is bypassed once; candidates use default order/gates
                  -> if any suggestion exists, Stop is skipped
                  -> if confidence >= 0.5, the episode ends
                  -> if confidence < 0.5, the episode remains but normal learning resumes next time
                  -> if no suggestion exists, Stop suggests considering termination because v=1

The weak-guidance path behaves similarly:

    Trace A: suggestions [0.42]
    Trace B: suggestions [0.20, 0.47]
    Trace C: no suggestion (does not increment or clear the weak count)
    Trace D: suggestions [0.49]
    Invocation E: y-trigger is historically active; current suggestion is [0.35]
                  -> Stop is skipped because a current suggestion exists
    Invocation F: y-trigger remains active; current invocation has no suggestion
                  -> Stop requests a one-shot bypass and returns NoHelp
    Invocation G: learned selection is bypassed once
                  -> any suggestion skips Stop; a suggestion >= 0.5 ends the episode
                  -> no suggestion allows Stop to suggest termination because v=1

Boundary cases must be explicit: confidence `0.5` is healthy; a later `Outcome.SUCCESS` makes an originally low-confidence trace healthy; a Stop suggestion at confidence `1.0` is excluded from health calculations; a recovery trace before old reset `NoHelp` results makes their effective count zero; fewer than `y` suggestion-bearing traces cannot activate the weak-guidance trigger; a current low-confidence suggestion still skips Stop; and an unrecorded `evaluateWithTrace()` result cannot influence later invocations.

Discussion: This design intentionally separates “CCRS is currently producing poor guidance” from “the agent should terminate.” Stop monitors the former and advises about the latter only after fresh reconsideration. The bypass is a one-invocation exploration safeguard against learned pruning, not deletion or invalidation of learning: all traces remain available and normal learned selection resumes afterward. The policy does not require situation correlation because the in-memory context contains one run-scoped guidance stream. Retry still requires its own operation-level correlation because retry limits answer a different question.

Todos:

- [x] Replace old Stop options with public end-user properties `noSuggestionInvocationThreshold`, `lowConfidenceInvocationThreshold`, `lowConfidenceThreshold`, `selectionResetCountBeforeStop`, and `traceHistoryLookbackLimit`, including validation, getters, builders, `toBuilder()`, Java configuration wiring, React snake/camel key mapping, examples, and documentation.
- [x] Introduce a focused immutable `StopHistoryAssessment` or equivalently named helper that classifies traces and returns trigger kinds, counts, recovery boundary, and completed bypass count without depending on `Situation.Type`.
- [x] Extend `CcrsTrace` only with derived helper methods needed for non-Stop suggestion confidence, `Outcome.SUCCESS`, and typed Stop reset `NoHelp` detection; do not add stored reset/advisory flags.
- [x] Add a dedicated reset `NoHelpReason` or another typed control result; never parse explanation text to detect a reset.
- [x] Preserve L0 admission in `ContingencyCcrs`: any current non-Stop suggestion skips Stop, regardless of confidence.
- [x] Detect a typed Stop reset `NoHelp` in the immediately preceding trace and bypass learned ordering/gating for the next invocation only; resume normal selection with retained history after it.
- [x] Ensure explicit strategy configuration and escalation policies remain authoritative during ungated reconsideration.
- [x] Rewrite [StopStrategyTest.java](src/test/java/ccrs/core/contingency/strategies/internal/StopStrategyTest.java) around the state machine and remove old same-type, immediate-stop, terminal-status, and attempted-trace semantics.
- [x] Add tests proving a reported `Outcome.SUCCESS` ends degradation even when its suggestion confidence was below `z`, while `PENDING`, `UNKNOWN`, `PARTIAL`, and `FAILED` do not receive that override.
- [x] Add `ContingencyCcrsStopReconsiderationTest.java` for complete multi-invocation orchestration and `ContingencyCcrsLearnedSelectionBypassTest.java` for one-shot default order, gate bypass, learned-selection resumption, and retained history.
- [x] Update [test_jpype_contingency_integration.py](../../ccrs-react/tests/test_jpype_contingency_integration.py) to configure all public `x/y/z/v` properties, record the typed reset `NoHelp` trace, execute the bypassed invocation, and observe the final sole Stop suggestion through the production bridge.
- [x] Update telemetry, trace serialization, the core/selection/React READMEs, and experiment reporting with trigger, bypass, and Stop rationale evidence without adding advisory/reset flag fields.

Concrete steps: Implement trace classification, public option mapping, and Stop unit tests first. Then add detection of the typed reset `NoHelp` and the one-invocation default-order/no-learned-gate path with its focused test. Preserve current L0 admission and run the multi-invocation orchestration test. Only after Java behavior passes, publish the Java artifacts and migrate the JPype configuration and integration sequence. Work from `S:\dev\ma\ccrs-bdi` for Java:

    rtk .\gradlew.bat :ccrs-core:test --tests ccrs.core.contingency.strategies.internal.StopStrategyTest
    rtk .\gradlew.bat :ccrs-core:test --tests ccrs.core.contingency.ContingencyCcrsLearnedSelectionBypassTest
    rtk .\gradlew.bat :ccrs-core:test --tests ccrs.core.contingency.ContingencyCcrsStopReconsiderationTest
    rtk .\gradlew.bat :ccrs-core:test
    rtk .\gradlew.bat :ccrs-core:publishToMavenLocal

Then use a fresh process from `S:\dev\ma\ccrs-react`:

    rtk S:\anaconda\agent\python.exe -m unittest discover -s tests -p "test_jpype_contingency_integration.py"

Validation and acceptance: Tests demonstrate both triggers independently and together; exact threshold boundaries; all public Java/React `x/y/z/v` mappings; Stop exclusion from confidence calculations; confidence-based and reported-success recovery boundaries; bounded history; option normalization; typed reset `NoHelp` with tracing on and off; one-shot default order and learned-gate bypass; normal learned-selection resumption using retained history; preservation of explicit configuration and escalation policy; current low-confidence suggestions still skipping Stop; Stop as the sole confidence-`1.0` suggestion after `v` completed bypasses; repeated advisory behavior when the agent declines; and the same sequence through JPype. No Java or Python code automatically terminates an agent, and no advisory/reset flags are added to the public result contract.

Outcome and notes: Implemented the complete two-stage Stop state machine, typed reconsideration result, bounded assessment, success boundary, one-shot learned-selection bypass, and advisory confidence-`1.0` result. Unit, orchestration, policy, and JPype sequences pass.

### WP3: Migrate the JaCaMo/Jason boundary

Status: Completed and validated on 2026-08-02

Purpose: Let AgentSpeak callers express a type-free request and remove the type-first calling convention completely.

Local context: `ccrs-jacamo/.../evaluate.java` currently supports 3-, 4-, and 7-argument forms whose first two inputs are `Type` and `Trigger`. Its map form is the natural extension point but currently still requires the type argument. `examples.asl` and the JaCaMo README teach all signatures.

Discussion: Prefer one composable map-based primary form over multiplying positional overloads. Remove the old positional forms rather than parsing or translating them. Do not keep category-based strategy rules inside `evaluate.java`.

Todos:

- [x] Define the exact new AgentSpeak signature and supported map keys from the accepted Java fields.
- [x] Add or update parser tests where feasible; at minimum compile the module and exercise parsing in a focused non-agent smoke.
- [x] Remove `situation_type` request logging and emit concrete request fields only.
- [x] Update `examples.asl` and JaCaMo documentation in WP6.

Concrete steps: From `S:\dev\ma\ccrs-bdi`:

    rtk .\gradlew.bat :ccrs-jacamo:compileJava
    rtk .\gradlew.bat classes

Validation and acceptance: The new AgentSpeak form builds the same Java request as a direct Java caller. The old type-first signatures and parser no longer exist. No optional A2A or LangChain4j class is imported directly into the JaCaMo adapter.

Outcome and notes: JaCaMo now exposes only `evaluate(ContextMap, Suggestions)` with trigger/resource/action/error/metadata entries. Positional typed parsing is gone; `:ccrs-jacamo:compileJava` and the full classes build pass.

### WP4: Migrate React and validate the JPype boundary

Status: Completed and validated on 2026-08-02

Purpose: Keep React graph routing and automatic/LLM escalation working while removing the duplicated four-value classification requirement from Python.

Local context: `situation.py` owns normalization, `escalation.py` owns the LLM tool schema, `default_escalation_controller.py` creates automatic requests, and `contingency_ccrs.py` loads and invokes Java through `java_runtime.py`. `ccrs_node.py` carries either a Python object or mapping from state/config to evaluation. `test_jpype_contingency_integration.py` is the required non-live production bridge suite, while `test_live_a2a_contingency.py` is the optional-service end-to-end consultation test.

Discussion: Python should mirror the accepted Java contract, not maintain an independent taxonomy. Structural normalization must continue to tolerate notebook module reloads. If Java binary names or builders change, update the class map in `contingency_ccrs.py`; `java_runtime.py` should remain generic unless the migration demonstrates a missing classpath/class-cache capability.

Todos:

- [x] Remove `SituationType`, `type_name`, and all type-key mapping aliases; keep a type-free Python `Situation` matching the remaining Java fields.
- [x] Change the escalation tool schema so the LLM reports observations/reason rather than choosing an authoritative category.
- [x] Change automatic HTTP/tool-failure controllers to populate evidence fields.
- [x] Update Java class loading, builder calls, trace serialization, and audit fields.
- [x] Expose `no_suggestion_invocation_threshold` (`x`), `low_confidence_invocation_threshold` (`y`), `low_confidence_threshold` (`z`), `selection_reset_count_before_stop` (`v`), and `trace_history_lookback_limit` through the same React configuration mapping used for other strategy properties, with camel-case aliases only where that is the existing active convention rather than legacy Stop compatibility.
- [x] Update [test_jpype_contingency_integration.py](../../ccrs-react/tests/test_jpype_contingency_integration.py) to use the accepted Python request and mapping forms while retaining runtime loading, registered strategy, retry, backtrack, configuration, context-proxy, and trace/result round trips and replacing immediate Stop with the WP2 reset/reconsider/advise sequence.
- [x] Add focused Python normalization tests if the integration suite cannot cover malformed and notebook-reloaded type-free objects clearly.
- [x] Update [test_live_a2a_contingency.py](../../ccrs-react/tests/test_live_a2a_contingency.py) to express the need for external help through the accepted model while retaining consultation applicability and returned key-holder action assertions.
- [x] Review `ccrs_node.py`, `state.py`, `decision.py`, package exports, and the notebook reload list for renamed objects.
- [x] Confirm whether `java_runtime.py` needs no source change; record that conclusion with validation evidence.

Concrete steps: Publish the Java snapshot first from `S:\dev\ma\ccrs-bdi`:

    rtk .\gradlew.bat :ccrs-core:publishToMavenLocal

Then start a fresh process in `S:\dev\ma\ccrs-react`:

    rtk S:\anaconda\agent\python.exe -m compileall react_agent
    rtk S:\anaconda\agent\python.exe -m unittest discover -s tests -p "test*.py"

Do not reuse a notebook kernel that loaded the pre-refactor `Situation` class. For notebook validation, restart the kernel and run [test_agent.ipynb](../../ccrs-react/test_agent.ipynb) top to bottom after the unit bridge passes.

Validation and acceptance: `test_jpype_contingency_integration.py` passes in a fresh process with the newly published Maven-local artifacts and proves Python can create a type-free request from an object and a mapping, convert it to Java, receive it back in the trace dictionary, preserve structured fields, configure all public Stop `x/y/z/v` properties, and exercise retry/backtrack plus the typed-reset/bypass/sole-Stop sequence through the real context proxy. Type-bearing Python inputs and mappings are absent from the public API and tests. `test_live_a2a_contingency.py` remains skip-safe when its service is absent and passes with the same consultation behavior when the key-holder service is available.

Outcome and notes: The Python API, escalation tool/controller, Java builder mapping, result schema, and Stop configuration are type-free. React returns advisory Stop guidance to the LLM. Fresh-process unittest discovery passed 8 tests with the live A2A smoke correctly skipped.

### WP5: Remove type-based telemetry and update experiment tooling

Status: Completed and validated on 2026-08-02

Purpose: Make new traces and reports explain requests through concrete facts and remove the old type column from active telemetry tooling.

Local context: Java and React emit `situation_type`; `parse-experiment-logs.ps1` carries it into `contingency.csv`; `write-report.ps1` renders it. Generated logs/reports must not be edited.

Discussion: Remove `situation_type` from active writers, parser state, CSV headers, and report rendering. Replace it with only the bounded concrete fields needed for analysis, such as trigger, resources, failed action, and selected error facts. Historical generated artifacts remain untouched, but active scripts do not carry a dual schema solely for them.

Todos:

- [x] Define stable event field names and the serialized form of structured evidence.
- [x] Update Java and React audit events consistently.
- [x] Add parser fixtures for new type-free log lines and verify they produce meaningful contingency rows.
- [x] Update the report writer to render concrete request details without a category column.
- [x] Confirm no files under historical run/report/export directories changed.

Concrete steps: Add focused PowerShell fixture validation to the existing experiment script workflow and record the exact command here when the fixture entry point is known. Run `git diff --name-only` in `ccrs-react` and reject any migration that rewrites historical artifacts.

Validation and acceptance: A new type-free log fixture parses successfully and its report contains meaningful trigger/action/resource/error context. No active parser, CSV header, or report template references `situation_type`. No historical file content changes.

Outcome and notes: Active telemetry and CSV/report generation now use trigger, current/target resource, failed action, HTTP status, error type, and error message. Both React and BDI report fixture suites pass; generated historical artifacts are untouched.

### WP6: Update all affected documentation and examples

Status: Completed and validated on 2026-08-02

Purpose: Ensure a new caller sees one accurate model and can copy working Java, AgentSpeak, and Python examples.

Local context: Use the documentation rows in the impact map. The core and JaCaMo READMEs contain the densest obsolete material. The React adapter README owns the Python/JPype explanation. Existing plans contain validation snippets that import `SituationType`.

Discussion: Document observations, applicability, and selection separately. Remove advice that tells callers to choose the right enum in order to get the desired strategy. Preserve historical Decision Log and Progress entries in older plans; add a concise supersession note or update only active validation guidance.

Todos:

- [x] Rewrite the core README file description, flow, strategy applicability table, JaCaMo synopsis, and Java example.
- [x] Update the selection README with the one-invocation bypass and learned-selection-resumption contract, and update the RDF context README with in-memory one-run scoping and exactly-once trace-recording requirements.
- [x] Rewrite the JaCaMo README signatures, examples, situation-type section, best practices, matrix, and troubleshooting.
- [x] Update the React adapter README's input model, concept map, graph flow, state description, and lifecycle notes.
- [x] Document all public Stop `x/y/z/v` properties plus `traceHistoryLookbackLimit` / `trace_history_lookback_limit` with Java and React examples, defaults, boundary semantics, and the consequence that historical low confidence only arms Stop while any current suggestion still skips it.
- [x] Update the standalone Java consumer source and README.
- [x] Update active examples/validation in `PLAN_CCRS_README.md` and add a supersession note to the completed strategy-configuration plan if needed.
- [x] Search active code/docs again for old symbols and classify every remaining match as an excluded historical artifact or a missed migration; no compatibility code is allowed.

Concrete steps: From `S:\dev\ma`:

    rtk rg -n -S "Situation\.Type|SituationType|situation_type|situationType|FAILURE|STUCK|UNCERTAINTY|PROACTIVE" ccrs-bdi ccrs-react -g "!**/build/**" -g "!**/experiments/runs/**" -g "!**/experiments/reports/**" -g "!**/experiments/backup/**" -g "!**/experiments/langsmith/**"

Manually inspect every remaining active match. Validate all Markdown links touched by the change and compile/run the examples they show.

Validation and acceptance: No active README or plan teaches the closed enum, and no active example contains it. Java, AgentSpeak, and Python examples use the type-free contract and pass their associated compile/smoke checks. Historical generated artifacts remain unchanged.

Outcome and notes: Core, selection, RDF, JaCaMo, React, root, and consumer documentation now teach the type-free model and advisory Stop semantics. Older plans carry explicit supersession notes while retaining historical decisions.

## Validation and Acceptance

WP3 must record the exact type-free AgentSpeak signature once its map shape is implemented. The retry-history rule is already settled in WP1. The plan-wide minimum validation is:

From `S:\dev\ma\ccrs-bdi`:

    rtk .\gradlew.bat :ccrs-core:test --tests ccrs.core.contingency.strategies.internal.BacktrackStrategyTest --tests ccrs.core.contingency.strategies.internal.RetryStrategyTest --tests ccrs.core.contingency.strategies.internal.StopStrategyTest --tests ccrs.core.contingency.strategies.internal.prediction.PredictionLlmStrategyTest --tests ccrs.core.contingency.strategies.social.ConsultationStrategyTest
    rtk .\gradlew.bat :ccrs-core:test --tests ccrs.core.contingency.ContingencyCcrsLearnedSelectionBypassTest --tests ccrs.core.contingency.ContingencyCcrsStopReconsiderationTest
    rtk .\gradlew.bat :ccrs-core:test
    rtk .\gradlew.bat :ccrs-jacamo:compileJava
    rtk .\gradlew.bat classes
    rtk .\gradlew.bat -p examples\ccrs-library-consumer compileJava
    rtk .\gradlew.bat publishToMavenLocal

The expected result is `BUILD SUCCESSFUL` for each command. If the repository-wide test task still triggers known JaCaMo suite failures, keep focused module tests and compilation as the acceptance checks and record the known failure separately; do not describe it as caused by this refactor without evidence.

From a fresh process in `S:\dev\ma\ccrs-react` after publishing:

    rtk S:\anaconda\agent\python.exe -m compileall react_agent
    rtk S:\anaconda\agent\python.exe -m unittest discover -s tests -p "test_jpype_contingency_integration.py"
    rtk S:\anaconda\agent\python.exe -m unittest discover -s tests -p "test_live_a2a_contingency.py"
    rtk S:\anaconda\agent\python.exe -m unittest discover -s tests -p "test*.py"

The expected result is successful compilation and all non-live tests passing. With Maven-local `ccrs-core` available, `test_jpype_contingency_integration.py` covers runtime loading, retry/backtrack round trips, all public Stop threshold mappings, typed reset `NoHelp`, one-shot bypass, sole Stop suggestion, and context recording. `test_live_a2a_contingency.py` runs when the documented key-holder service is available and otherwise reports it as `skipped`.

Behavioral acceptance must cover these cases in both direct Java tests and the Python bridge where applicable:

- Retriable failure evidence admits retry and preserves retry limits.
- Non-retriable or incomplete failure evidence does not admit retry.
- Current-resource and interaction-history evidence admits backtrack without needing `STUCK` or `FAILURE`.
- General consultation/prediction strategies can see a new condition that was not defined by the old enum.
- No Java, JaCaMo, Python, serialized-result, telemetry, test, example, or active-documentation API accepts or emits a situation type.
- Retry attempts count prior retry evaluations with the same failed action and target resource; a different action or target starts a separate bounded series.
- Stop becomes historically eligible after `x` consecutive no-guidance invocations or `y` recent low-confidence suggestion-bearing invocations below `z`, but any current suggestion still skips Stop.
- After a historically eligible no-suggestion invocation, Stop emits a typed reset `NoHelp`; the next invocation bypasses learned order/gates once, and Stop becomes the sole confidence-`1.0` suggestion only after `v` completed bypass cycles still fail to produce current guidance.
- A non-Stop suggestion at confidence `z` or above or a trace reported as `Outcome.SUCCESS` ends the degradation episode; Stop's own suggestion never counts as recovery evidence.
- Java and React end users can independently configure `x`, `y`, `z`, `v`, and the Stop trace-history lookback through the same configuration surfaces as other strategy properties.
- The invocation immediately after a typed Stop reset `NoHelp` uses default order and no learned gate; the following invocation resumes normal learned selection with all retained history.
- Traces, consultation payloads, prompts, Python dictionaries, and reports explain the request using the new model.
- Historical experiment artifacts remain byte-for-byte unchanged but are not an active compatibility target.

## Outcomes & Retrospective

The breaking refactor is complete without a compatibility layer. The request
model is smaller, strategy applicability now follows evidence that the caller
can actually observe, and adding a future strategy no longer requires adding a
request category. Retry retains its meaningful operation-level correlation,
while Stop deliberately analyzes the single run-wide guidance stream.

Stop now behaves as a safeguard rather than an immediate fallback: degradation
first produces a typed request for one invocation without learned gates, and
only continued lack of guidance produces an advisory stop suggestion. The
React graph change was essential to preserve that advisory interpretation; it
returns the suggestion to the agent instead of ending the graph.

Validation completed on 2026-08-02:

- `:ccrs-core:test` passed, including the five named regression suites and the
  new Stop reconsideration and learned-selection bypass tests.
- `:ccrs-jacamo:compileJava`, `classes`, the standalone consumer compile, and
  `:ccrs-core:publishToMavenLocal` completed successfully.
- Fresh-process React unittest discovery passed 10 tests; the live A2A smoke
  was the sole skip because the optional local key-holder service was absent.
- Python compilation and both React and BDI experiment-report fixture suites
  passed.
- Active-code/documentation legacy-symbol searches found only negative
  regression assertions; historical run/report/LangSmith artifacts were not
  modified.

## Idempotence and Recovery

Implement this as one coordinated breaking refactor across core and both adapters; do not leave an intermediate committed state containing two public models. Re-running builds, tests, Maven-local publication, Python compilation, and parser fixtures is safe.

Before editing overlapping core files, inspect both staged and unstaged diffs. Do not reset, checkout, or overwrite the current `BacktrackStrategy` changes, the five strategy test files, or the two React integration tests. Some are currently staged, modified-after-staging, or untracked. If those changes evolve while this plan is active, rebase the package-specific todos on their current behavior and record the discovery.

If Maven-local publication succeeds but the React bridge appears to expose old methods, stop the Python process or notebook kernel and start a new one. Adding a new jar to an existing JPype classpath does not replace a class already loaded by name. `java_runtime.py` creates fingerprinted stable jar copies specifically to keep one Python process on a coherent jar image.

Do not delete or regenerate historical experiment directories as a cleanup step. Parser changes should be exercised against new minimal type-free fixtures. If an implementation attempt is abandoned, revert only files created by that attempt and retain this plan's Decision Log explaining why.

## Artifacts and Notes

Pre-refactor direct strategy gates (historical evidence):

    RetryStrategy.appliesTo:
        situation.getType() == FAILURE
        AND failedAction and targetResource exist
        AND httpStatus/errorType is retriable

    BacktrackStrategy.appliesTo:
        situation.getType() is FAILURE or STUCK
        AND currentResource is known
        AND context has history

Pre-refactor hidden grouping behavior (historical evidence):

    Retry history: same type + same failed action + same target resource
    Stop history: same type

Target Stop behavior:

    x consecutive no-guidance traces
        OR y recent suggestion-bearing traces with max confidence < z
        -> Stop waits for a current invocation with no suggestion
        -> Stop emits typed reset NoHelp
        -> next invocation bypasses learned order/gates once
        -> any current suggestion still skips Stop
        -> confidence >= z or reported SUCCESS ends the degradation episode
        -> otherwise, after v completed bypass cycles and another no-suggestion result,
           Stop is the sole confidence-1.0 suggestion

Pre-refactor React bridge (historical evidence):

    Python Situation.type
        -> SituationType/type_name
        -> Java Situation$Type.valueOf(...)
        -> Situation.builder(javaType)
        -> trace.getSituation().getType()
        -> Python result["situation"]["type"] and situation_type audit fields

These snippets preserve the discovery evidence that motivated the completed refactor; they are not current API guidance.

## Interfaces and Dependencies

The target interfaces are:

- `ccrs-core` owns the canonical runtime-guidance request model.
- `Situation` retains `trigger`, `currentResource`, `targetResource`, `failedAction`, `errorInfo`, and `metadata`, and is constructed with `Situation.builder()` without arguments.
- `Situation.Type`, the `type` field, `getType()`, `builder(Type)`, `failure(...)`, and `stuck(...)` do not exist after the refactor.
- `ContingencyCcrs.evaluate(Situation, CcrsContext)` and `evaluateWithTrace(Situation, CcrsContext)` remain the orchestration entry points.
- `CcrsStrategy.appliesTo(Situation, CcrsContext)` continues returning `Applicability` and bases its result on concrete `Situation` fields and `CcrsContext` evidence.
- Retry accounting counts prior actual retry evaluations within `retryLookbackLimit` when both requests have the same non-null `failedAction` and equal `targetResource`; no new operation-correlation field is introduced. Stop does not use request identity; it analyzes the ordered stream of completed invocation summaries in the one-run in-memory `CcrsContext`.
- `StopStrategyOptions` exposes end-user properties `noSuggestionInvocationThreshold` (`x`), `lowConfidenceInvocationThreshold` (`y`), `lowConfidenceThreshold` (`z`), `selectionResetCountBeforeStop` (`v`), and `traceHistoryLookbackLimit`; the old exhaustion options do not exist. React exposes the last property as `trace_history_lookback_limit`. `ContingencyConfiguration.Builder.stop(...)`, React configuration mapping, examples, and READMEs expose the same properties.
- `CcrsTrace` exposes derived helpers for non-Stop guidance confidence, `Outcome.SUCCESS`, and typed Stop reset `NoHelp` detection; no reset/advisory booleans are stored. The minimal Stop control evaluation remains available when verbose tracing is disabled.
- The invocation immediately following a typed Stop reset `NoHelp` bypasses `TraceBasedStrategySelectionPolicy` ordering/gating once. The subsequent invocation resumes normal learned selection with the full retained bounded history.
- `ContingencyCcrs` preserves the existing L0 rule: any current non-Stop suggestion skips Stop, regardless of confidence. Therefore a Stop suggestion with confidence `1.0` is necessarily the sole suggestion.
- Stop first returns a typed reset `NoHelp`; after `v` completed one-invocation bypass cycles in the same degradation episode and another current no-suggestion result, it returns the sole confidence-`1.0` `stop` suggestion. No confirmation/advisory fields are added, and no core or adapter API terminates the consuming agent.
- JaCaMo and React translate their native data into the canonical Java model without any situation-type parameter, enum, alias, or output field.
- Trace, consultation, prompt, audit, Python dictionary, and CSV projections are bounded views of the same concrete request fields and contain no situation-type field.

No new provider dependency belongs in `ccrs-core`. The existing dependency direction remains `core <- jacamo <- hypermedea`, `core <- langchain4j`, and `core <- a2a`. React continues to load published Java modules through JPype and Maven-local artifacts. Do not add a serialization framework merely to remove the type across the JPype boundary.

Revision note (2026-08-02): Created the temporary ExecPlan after repository-wide impact discovery. The initial version deferred the replacement design to WP1, mapped active Java/JaCaMo/React/telemetry/documentation consumers, and recorded historical-artifact and JPype lifecycle constraints.

Revision note (2026-08-02): Added the existing `BacktrackStrategyTest`, `RetryStrategyTest`, `StopStrategyTest`, `PredictionLlmStrategyTest`, `ConsultationStrategyTest`, `test_jpype_contingency_integration.py`, and `test_live_a2a_contingency.py` as mandatory in-place regression/integration coverage. Updated discoveries, the impact map, WP2, WP4, plan-wide validation, recovery guidance, and the Decision Log so implementation cannot bypass these tests.

Revision note (2026-08-02): Recorded the user's decision to remove situation types completely with no replacement classification and no compatibility code. Narrowed WP1 to applicability and history-correlation behavior, removed WP7 and all staged-compatibility work, made the target `Situation.builder()` interface explicit, and changed telemetry acceptance to a type-free active schema while preserving historical artifacts unchanged.

Revision note (2026-08-02): Added WP2 to replace Stop's same-type trace count with configurable no-guidance and low-confidence triggers, a trace-backed learned-selection reset epoch, configurable repeated reconsideration before stopping, and an explicitly advisory final result. Renumbered downstream packages to WP3-WP7 and updated the impact map, rules, discoveries, decisions, Java/JPype regression scope, validation, artifacts, and target interfaces accordingly.

Revision note (2026-08-02): Refined WP2 from a persistent learning-epoch reset to a one-invocation bypass followed by normal learned-selection resumption. Preserved the existing rule that any current suggestion skips Stop, retained confidence `1.0` without adding result flags, added `Outcome.SUCCESS` as a recovery boundary ahead of end-to-end outcome reporting, documented the one-run in-memory history assumption, and made `x/y/z/v` explicit Java and React end-user properties with regression and documentation requirements.

Revision note (2026-08-02): Removed the standalone type-free applicability/history design package because its decisions are now concrete. Promoted the Java core refactor to WP1, specified Retry correlation as failed-action plus target-resource equality over actual retry evaluations, retained Backtrack's current-resource plus `hasHistory()` admission check, renumbered downstream packages to WP3-WP6 while preserving Stop as WP2, and renamed Stop's trace scanning option to `traceHistoryLookbackLimit` / `trace_history_lookback_limit` so it cannot be confused with other histories.

Revision note (2026-08-02): Completed WP1-WP6. Recorded the implemented Java/JaCaMo/React/telemetry/documentation surfaces, the React advisory-routing decision, full validation evidence, unchanged historical artifacts, and the final outcomes retrospective.
