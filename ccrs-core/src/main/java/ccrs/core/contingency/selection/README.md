# Strategy Selection Policies

This package contains the core strategy-selection port and deterministic
implementations used by `ContingencyCcrs`.

## Architecture

`ContingencyCcrs` depends only on `StrategySelectionPolicy` and
`StrategySelectionPlan`.

* `StrategySelectionPolicy` creates one plan for one CCRS invocation.
* `StrategySelectionPlan` orders enabled strategies and gates candidates before
  evaluation.
* `StrategySelectionRequest` carries the current situation, context, default
  registry order, configuration, and recent traces.
* `StrategyGateDecision` is the policy-neutral explanation object logged by the
  orchestrator.

## Policy Types

`DefaultStrategySelectionPolicy` is the deterministic baseline. It preserves
the registry/default escalation order and evaluates every enabled candidate.

`TraceBasedStrategySelectionPolicy` is the current adaptive default. It builds a
trace-based model from recent `CcrsTrace` records and can reorder or skip
strategies when enough local evidence exists.

## One-invocation learned-selection bypass

Stop can return `NoHelp(SELECTION_RECONSIDERATION_REQUESTED)` after a run-local
degradation threshold is reached. On the immediately following invocation,
`ContingencyCcrs` deliberately omits the learned selection plan and uses default
registry order with no learned gates. This is a single-invocation bypass, not a
history reset or a new learning epoch.

The bypass still honors configured maximum level, disabled strategies,
escalation policy, normal applicability checks, and the existing context and
trace history. Learned ordering and gates resume on the next invocation.
