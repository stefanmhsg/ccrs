# Course Check and Revision Strategies (CCRS)

CCRS is a reusable research artifact for detecting opportunities during an
agent course of action and selecting revision strategies when execution needs
to adapt. This repository contains the framework libraries, optional capability
adapters, reproducible Gradle builds, and a published-artifact consumer.

The concrete JaCaMo/BDI application and its experiments live in the separate
[ccrs-bdi repository](https://github.com/stefanmhsg/ccrs-bdi). Keeping the
application separate makes the boundary observable: it can use CCRS only
through published packages, just like another research artifact consumer.

## Modules

| Module | Responsibility |
| --- | --- |
| [ccrs-core](ccrs-core) | Agent-agnostic opportunistic and contingency CCRS contracts and policies. |
| [ccrs-jacamo](ccrs-jacamo) | JaCaMo/Jason adapter for CCRS. |
| [ccrs-hypermedea](ccrs-hypermedea) | Optional Hypermedea HTTP and interaction-history integration. |
| [ccrs-langchain4j](ccrs-langchain4j) | Optional LangChain4j-backed prediction capability. |
| [ccrs-a2a](ccrs-a2a) | Optional A2A-backed consultation capability. |
| [ccrs-langgraph](ccrs-langgraph) | Installable Python adapter that exposes Java CCRS to LangGraph agents through JPype. |
| [ccrs-workspace](ccrs-workspace) | Development-only Gradle composite for all five standalone builds. |
| [examples/ccrs-library-consumer](examples/ccrs-library-consumer) | Artifact-only consumer and publication contract check. |

The dependency direction is intentionally one-way:

```text
ccrs-core <- ccrs-jacamo <- ccrs-hypermedea
ccrs-core <- ccrs-langchain4j
ccrs-core <- ccrs-a2a
```

Every module is a complete Java 21 Gradle build with its own wrapper, tests,
Javadocs, sources artifact, metadata, and Maven publication. Production
dependencies use Maven coordinates; there are no Gradle project dependencies
between the standalone builds.

`ccrs-langgraph` is a separate Python distribution boundary, not another node
in the Java dependency graph. It resolves selected Java modules and their
transitive dependencies from GitHub Packages at runtime.

## Build

Build one module independently:

```powershell
cd ccrs-core
.\gradlew.bat build
```

Build all local module sources through coordinate-preserving composite
substitution:

```powershell
cd ccrs-workspace
.\gradlew.bat --no-daemon verifyAll
```

The composite is optional. It owns no production code, repositories,
dependencies, Java configuration, or publication.

## Published Packages

Snapshots are published to GitHub Packages at:

```text
https://maven.pkg.github.com/stefanmhsg/ccrs
```

Current coordinates use the group `io.github.stefanmhsg.ccrs` and the aligned
version `0.1.0-SNAPSHOT`:

```gradle
implementation 'io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT'
implementation 'io.github.stefanmhsg.ccrs:ccrs-jacamo:0.1.0-SNAPSHOT'
runtimeOnly 'io.github.stefanmhsg.ccrs:ccrs-hypermedea:0.1.0-SNAPSHOT'
runtimeOnly 'io.github.stefanmhsg.ccrs:ccrs-langchain4j:0.1.0-SNAPSHOT'
runtimeOnly 'io.github.stefanmhsg.ccrs:ccrs-a2a:0.1.0-SNAPSHOT'
```

GitHub Packages requires authentication for Maven downloads. Put local
credentials only in `%USERPROFILE%\.gradle\gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_GITHUB_PAT
```

Use `read:packages` for consumption and add `write:packages` only for
publication. Never commit credentials. The manually dispatched
[snapshot workflow](.github/workflows/publish-ccrs-snapshots.yml) uses its
repository `GITHUB_TOKEN`, builds every standalone module, verifies both local
composite and staged-artifact paths, publishes all five snapshots, and runs the
consumer with a fresh Gradle user home.

The Python adapter is distributed as wheel and source archives on this
repository's GitHub Releases because GitHub Packages has no PyPI registry.
Release tags are adapter-scoped, for example `ccrs-langgraph-v0.1.1`; see the
[adapter installation guide](ccrs-langgraph/README.md) and
[release workflow](.github/workflows/release-ccrs-langgraph.yml).

## Architecture And Reproducibility

[CCRS_LIBRARY.md](CCRS_LIBRARY.md) documents the module boundaries, extension
contracts, accepted simplifications, testing policy, and standalone acceptance
contract. [PLAN_CCRS_PHYSICAL_SEPARATION.md](PLAN_CCRS_PHYSICAL_SEPARATION.md)
records the physical extraction and its validation evidence.

The public classes below `strategies.internal` remain supported public types;
`internal` is a conceptual grouping, not Java visibility. The current
A2A-shaped target discovery and request/response projection in generic
consultation policy is a documented research simplification rather than an
unrecorded packaging leak.
