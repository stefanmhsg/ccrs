# Contingency CCRS from AgentSpeak

The `evaluate` internal action gives a Jason agent runtime guidance from Java contingency CCRS. Its public signature is intentionally type-free:

```asl
ccrs.jacamo.jason.contingency.evaluate(ContextMap, Suggestions)
```

`ContextMap` is a Jason `map(...)` containing any available evidence. Strategies decide applicability; the caller does not select a request category or strategy.

## Supported evidence

```asl
map(
    trigger("http_error"),
    current(CurrentURI),
    target(TargetURI),
    action("GET"),
    http_status("503"),
    error_type("SERVER_FAILURE"),
    error_message("Service Unavailable"),
    metadata("agent_role", "navigator")
)
```

Supported entries are:

| Entry | Java field |
|---|---|
| `trigger(Value)` | `Situation.trigger` |
| `current(Value)` | `Situation.currentResource` |
| `target(Value)` | `Situation.targetResource` |
| `action(Value)` | `Situation.failedAction` |
| `error(Value)` | A three-digit value sets `errorInfo["httpStatus"]` and an HTTP message; any other value sets `errorInfo["message"]` |
| `http_status(Code)` | `errorInfo["httpStatus"]` |
| `error_type(Value)` | `errorInfo["errorType"]` |
| `error_message(Value)` | `errorInfo["message"]` |
| `metadata(Key, Value)` | `metadata[Key]` |

Every entry except `metadata(Key, Value)` accepts exactly one value. Malformed or unknown entries fail the action with a clear error. There are no positional or typed compatibility signatures.

## Examples

Minimal request:

```asl
ccrs.jacamo.jason.contingency.evaluate(
    map(trigger("no_valid_options"), current(Location)),
    Suggestions
);
```

Retry evidence:

```asl
ccrs.jacamo.jason.contingency.evaluate(
    map(
        trigger("rate_limited"),
        current(CurrentURI),
        target(TargetURI),
        action("POST"),
        http_status("429"),
        error_message("Too many requests")
    ),
    Suggestions
);
```

The output is a list of `suggestion(...)` terms. Consumers should inspect the proposed action and confidence; `stop` is advice to consider ending the run, not an automatic termination command.

## Strategy evidence

- Retry requires an action, target, and retriable error evidence.
- Backtrack requires a current resource and retained interaction history.
- Consultation and prediction use their configured providers and context.
- Stop uses run-local CCRS trace history. It first requests a one-invocation learned-gate bypass; only after the configured number of unsuccessful bypass cycles can it suggest stopping.

The Java `CcrsContext` supplied through `CcrsJasonServices` owns interaction and trace history for the agent run. Keep that service/context instance stable across invocations.

The application-owned
[examples.asl](../../../../../../../../src/agt/examples/contingency/examples.asl)
contains complete AgentSpeak handler patterns. It is not packaged in this
module and is not part of the supported JaCaMo library runtime contract. See the
[core contingency README.md](../../../../../../../../ccrs-core/src/main/java/ccrs/core/contingency/README.md)
for configuration and Stop semantics.
