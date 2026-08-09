# CCRS JaCaMo Adapter Boundary

This package is the JaCaMo-facing adapter for CCRS. It intentionally contains
the pieces that must work together for JaCaMo agents:

- `ccrs.jacamo.jason.opportunistic.CcrsAgent`: Jason BRF integration.
- `ccrs.jacamo.jaca.CcrsAgentArch`: CArtAgO observable batching before the next reasoning cycle.
- `ccrs.jacamo.jason.contingency.evaluate`: AgentSpeak-facing contingency evaluation.
- `ccrs.jacamo.jason.opportunistic.prioritize`: AgentSpeak-facing option prioritization.
- `ccrs.jacamo.jason.contingency.JasonCcrsContext`: Jason belief-base backed `CcrsContext`.

`CcrsAgent` and `CcrsAgentArch` are complementary, not duplicate integration
points. Artifacts such as Hypermedea may synchronize directly with the belief
base, while structural opportunistic CCRS matching needs cycle-level batching
from CArtAgO observables.

## Standalone Build

This directory is a complete Java 21 Gradle build that publishes
`io.github.stefanmhsg.ccrs:ccrs-jacamo:0.1.0-SNAPSHOT`. Its dependency on
`ccrs-core` is a Maven coordinate, not a sibling Gradle project.

With GitHub Packages credentials configured in the user-level Gradle
properties file, run:

```powershell
.\gradlew.bat --refresh-dependencies build
.\gradlew.bat publishToMavenLocal
```

For an isolated coordinate-based build chain, pass the same explicit staging
repository to the core publication and this build:

```powershell
.\gradlew.bat `
  -PccrsRepositoryUrl=S:/path/to/ccrs-staging-repo `
  --refresh-dependencies build
```

Publish a snapshot with
`publishMavenJavaPublicationToGitHubPackagesRepository`. The standalone build
does not read the BDI root build, application sources, `.jcm` files, or
AgentSpeak examples.

## Optional Artifact History

The JaCaMo adapter does not depend on a concrete HTTP artifact implementation.
Interaction history is supplied through
[CcrsJacamoRuntime.java](src/main/java/ccrs/jacamo/CcrsJacamoRuntime.java):

```java
ccrs.jacamo.CcrsJacamoRuntime.setInteractionHistoryProvider(...)
```

The default provider is empty. The separate
[Hypermedea adapter README.md](../ccrs-hypermedea/README.md) describes how
[JasonInteractionLog.java](../ccrs-hypermedea/src/main/java/ccrs/hypermedea/JasonInteractionLog.java)
is installed as the provider when the Hypermedea registry is loaded.

This keeps Hypermedea replaceable: another HTTP artifact can implement
`InteractionHistoryProvider` and install itself without changing the JaCaMo
adapter.

## Runtime Scope and Isolation

`CcrsJacamoRuntime` is process-wide application wiring. Its provider,
configuration, and factory supplier references are safely published to other
threads, but they are not per-agent or per-user values. Install them during MAS
startup and avoid competing runtime writers. Each contingency evaluation uses
one consistent core configuration snapshot.

The JaCaMo adapter can serve multiple mutually trusted agents in one JVM when
each agent has its own `JasonCcrsContext`. Agent names are logical routing keys,
not authenticated identities: duplicate names share a history partition, and
direct API callers can request another name. Run mutually untrusted users or
tenants in separate JVMs with separate runtime wiring, credentials, and logs.

`CcrsAgentArch` treats opportunistic `ccrs/3` beliefs as source-scoped current
state. Updating or removing one RDF source replaces only that source's
architecture-produced guidance. `CcrsAgent` ties single-percept guidance to a
stable evidence identifier and removes it with the percept. Contingency notes
are persistent and are not part of either refresh lifecycle. CArtAgO observable
properties enter through `CcrsAgentArch`'s direct belief-base hooks; ordinary
environment percepts enter through `CcrsAgent.buf`, so one observable is not
scanned by both producers in the supported integration path.

## Optional Strategy Capabilities

The contingency internal action obtains a process-wide configuration generation
of `ContingencyCcrs` through `CcrsJacamoRuntime`; it is shared by all agents and
is not per-agent configuration. Setters invalidate the generation for the next
lookup while an evaluation already in progress keeps its captured evaluator.
The default supplier uses `ServiceLoader` for
`ccrs.core.contingency.CcrsStrategyProvider`.

This is the plugin point for optional strategy capabilities. The JaCaMo adapter
must not import concrete capability implementations such as LangChain4j or A2A,
because those dependencies should be optional. Instead, capability modules
announce themselves to Java's built-in `ServiceLoader` mechanism.

The runtime flow is:

```text
AgentSpeak calls ccrs.jacamo.jason.contingency.evaluate(...)
  -> evaluate asks CcrsJacamoRuntime for a ContingencyCcrs instance
  -> CcrsJacamoRuntime uses ContingencyCcrsFactory
  -> ContingencyCcrsFactory registers built-in core strategies
  -> ServiceLoader discovers CcrsStrategyProvider implementations on the classpath
  -> each provider registers its optional strategies
```

`META-INF/services` is the standard Java location where a jar lists service
implementations. The file name must be the fully qualified interface name:

```text
META-INF/services/ccrs.core.contingency.CcrsStrategyProvider
```

The file content is one provider implementation class per line:

```text
ccrs.capabilities.llm.langchain4j.Langchain4jPredictionStrategyProvider
ccrs.capabilities.a2a.A2aConsultationStrategyProvider
```

When that file is packaged into a jar and the jar is on the JaCaMo application's
classpath, `ServiceLoader` can instantiate those provider classes. Each provider
then decides whether its capability is configured and available before it
registers a strategy. For example, the LangChain4j provider can skip
registration when no LLM API key is configured.

In this repository, each optional capability module carries its own service
file under its own `src/main/resources`. For example,
[ccrs-langchain4j](../ccrs-langchain4j/src/main/resources/META-INF/services/ccrs.core.contingency.CcrsStrategyProvider)
lists only `Langchain4jPredictionStrategyProvider`, and
[ccrs-a2a](../ccrs-a2a/src/main/resources/META-INF/services/ccrs.core.contingency.CcrsStrategyProvider)
lists only `A2aConsultationStrategyProvider`.

This keeps `evaluate` independent from concrete LLM, A2A, or other capability
implementations.

Applications can configure the default factory path without replacing it. Call
this from Java setup code before the AgentSpeak contingency internal action is
used:

```java
import ccrs.core.contingency.ContingencyConfiguration;
import ccrs.jacamo.CcrsJacamoRuntime;

CcrsJacamoRuntime.setContingencyConfiguration(
    ContingencyConfiguration.builder()
        .predictionLlm(options -> options.maxHistoryActions(20))
        .retry(options -> options
            .maxAttempts(5)
            .initialDelayMs(500))
        .stop(options -> options
            .noSuggestionInvocationThreshold(2)
            .lowConfidenceInvocationThreshold(3)
            .lowConfidenceThreshold(0.5)
            .selectionResetCountBeforeStop(1)
            .traceHistoryLookbackLimit(30))
        .build()
);
```

This keeps `ServiceLoader` discovery active while passing the same central
configuration into built-in strategies and optional providers through
[`CcrsStrategyProviderContext.java`](../ccrs-core/src/main/java/ccrs/core/contingency/CcrsStrategyProviderContext.java).

Applications that do not want classpath-based discovery can override the
factory completely:

```java
CcrsJacamoRuntime.setContingencyCcrsSupplier(() -> {
    ContingencyCcrs ccrs = ContingencyCcrs.withDefaults();
    ccrs.getRegistry().register(new MyCustomStrategy());
    return ccrs;
});
```

Use this explicit supplier when a deployment wants full control over which
strategies are available.
