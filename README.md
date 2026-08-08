# CCRS BDI Repository

This repository currently has two roles:

- It is the working source tree for the reusable CCRS library modules:
  [ccrs-core](ccrs-core), [ccrs-jacamo](ccrs-jacamo),
  [ccrs-hypermedea](ccrs-hypermedea),
  [ccrs-langchain4j](ccrs-langchain4j), and [ccrs-a2a](ccrs-a2a).
- It is also a concrete JaCaMo/Jason user project with agents and `.jcm`
  configurations that consume those modules through published Maven
  coordinates in [build.gradle](build.gradle).

The reusable libraries are the `ccrs-*` modules. The root project, `.jcm`
files, `.asl` agents, logs, local environment files, and experiments are
application code and are not intended to be published as libraries.

---

## Repository Layout

| Path | Role |
| --- | --- |
| [ccrs-core](ccrs-core) | Agent-agnostic CCRS core, RDF context contracts, contingency strategies, and strategy extension points. |
| [ccrs-jacamo](ccrs-jacamo) | JaCaMo/Jason adapter for CCRS. |
| [ccrs-hypermedea](ccrs-hypermedea) | Optional Hypermedea integration and interaction history provider. |
| [ccrs-langchain4j](ccrs-langchain4j) | Optional LangChain4j/OpenAI-backed LLM capability provider. |
| [ccrs-a2a](ccrs-a2a) | Optional A2A-backed consultation capability provider. |
| [ccrs-workspace](ccrs-workspace) | Optional Gradle composite for editing and verifying all five standalone libraries together. |
| [src/agt](src/agt) and `*.jcm` | This repository's JaCaMo/Jason application agents and launch configurations. |
| [experiments](experiments) | Manual MASE experiment workflow, metrics documentation, analysis scripts, run archives, and generated reports. |
| [examples/ccrs-library-consumer](examples/ccrs-library-consumer) | Standalone example project that consumes the published CCRS modules from Maven coordinates. |
| [CCRS_LIBRARY.md](CCRS_LIBRARY.md) | Library extraction notes, module boundaries, and remaining library-readiness tasks. |

## Working On The CCRS Libraries

Each of the five library directories is a complete Gradle build with its own
`settings.gradle`, wrapper, Java 21 configuration, tests, and publication.
There are no CCRS subprojects in the root [settings.gradle](settings.gradle)
and no Gradle `project(...)` dependencies between modules.

Open a module directory itself as a Gradle project to work on that library in
isolation. Open [ccrs-workspace](ccrs-workspace) to edit and navigate all five
included builds together while preserving their Maven-coordinate dependency
declarations.

Build one module from its directory:

```powershell
cd ccrs-core
.\gradlew.bat build
```

Build and test all library sources through composite substitution:

```powershell
cd ccrs-workspace
.\gradlew.bat verifyAll
```

The workspace contains no production code, dependency declarations,
repositories, or publication. Its aggregate tasks delegate to the five module
builds. See the [CCRS composite workspace README.md](ccrs-workspace/README.md)
for the source-substitution and published-artifact workflows.

Dependent modules such as `ccrs-jacamo` resolve CCRS dependencies from GitHub
Packages by default. They can instead use one explicitly selected staging
repository by passing `-PccrsRepositoryUrl=S:/path/to/ccrs-staging-repo`.
This changes Maven resolution, not the coordinate declarations, and never
falls back to sibling source or Maven Local automatically.

The root JaCaMo application consumes aligned coordinates:

```gradle
implementation 'io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT'
implementation 'io.github.stefanmhsg.ccrs:ccrs-jacamo:0.1.0-SNAPSHOT'
implementation 'io.github.stefanmhsg.ccrs:ccrs-hypermedea:0.1.0-SNAPSHOT'
implementation 'io.github.stefanmhsg.ccrs:ccrs-langchain4j:0.1.0-SNAPSHOT'
implementation 'io.github.stefanmhsg.ccrs:ccrs-a2a:0.1.0-SNAPSHOT'
```

All current Java projects use a Java 21 Gradle toolchain and compile with
`--release 21`. Install a Java 21 JDK for local builds; Gradle toolchain
resolution may provision one when the environment permits downloads. The
standalone-module acceptance contract and migration sequence are documented in
[CCRS_LIBRARY.md](CCRS_LIBRARY.md) and
[PLAN_CCRS_PHYSICAL_SEPARATION.md](PLAN_CCRS_PHYSICAL_SEPARATION.md).

Compile the coordinate-only root application without running agents:

```powershell
.\gradlew.bat --refresh-dependencies classes
```

The published coordinates use:

```text
io.github.stefanmhsg.ccrs:<module-name>:0.1.0-SNAPSHOT
```

For example:

```gradle
implementation 'io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT'
```

The root JaCaMo application is not a Maven publication. Each `ccrs-*` module
owns exactly one `mavenJava` publication and can publish it independently.

## GitHub Packages Snapshots

The five standalone library builds publish to the repository-scoped Maven
registry at `https://maven.pkg.github.com/stefanmhsg/ccrs-bdi`. The explicitly
triggered
[Publish CCRS snapshots workflow](.github/workflows/publish-ccrs-snapshots.yml)
first validates all local sources through the optional composite, then validates
their coordinate graph in an isolated runner-temporary Maven repository and
publishes them in dependency order through their own wrappers. A separate job
resolves the resulting `0.1.0-SNAPSHOT` artifacts in a fresh Gradle user home.
Pull requests and ordinary builds do not publish.

GitHub Actions uses its automatically created `GITHUB_TOKEN`; no repository
PAT secret is required for this same-repository workflow. For local publication
or consumption, place a GitHub username and classic personal access token in
the user-level Gradle file `%USERPROFILE%\.gradle\gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_GITHUB_PAT
```

Use `read:packages` to consume packages and add `write:packages` to publish
them. Never put these properties in a repository file. A local manual
publication invokes
`publishMavenJavaPublicationToGitHubPackagesRepository` from each module in
dependency order. Prefer the workflow for publishing the aligned set.

```powershell
.\ccrs-core\gradlew.bat -p ccrs-core `
  publishMavenJavaPublicationToGitHubPackagesRepository
```

Each GitHub Packages publication task refuses non-snapshot versions. Stable, tag-driven releases
are intentionally deferred until the release-hardening work package in
[PLAN_CCRS_PHYSICAL_SEPARATION.md](PLAN_CCRS_PHYSICAL_SEPARATION.md).

## Standalone Consumer Example

[examples/ccrs-library-consumer](examples/ccrs-library-consumer) is a separate
Gradle project that demonstrates how another repository can import all five
published CCRS libraries from Maven coordinates instead of using Gradle project
dependencies. It also verifies the public LangChain4j API and the optional
service descriptors.

Run it against GitHub Packages as documented in the
[consumer README.md](examples/ccrs-library-consumer/README.md), or point it at
one explicitly selected staging repository:

```powershell
.\gradlew.bat -p examples/ccrs-library-consumer `
  -PccrsRepositoryUrl=S:/path/to/ccrs-staging-repo `
  --refresh-dependencies clean build run
```

The example first validates the published dependency scopes and service files,
then creates a minimal in-memory CCRS context, evaluates a retryable failure
situation, and prints the resulting strategy suggestion.

## Run The Local JaCaMo App

Defaults to [ccrs_bdi.jcm](ccrs_bdi.jcm):

```powershell
gradle run
```

To run a specific JaCaMo configuration file, use:

##### Depth-First Search (DFS) Baseline Agent:

* [dfs_baseline.asl](src/agt/dfs_baseline.asl) implements a Depth-First Search to navigate the maze. This is a possible solution without considering any CCRS features. Can handle 'unlock' actions.

```powershell
gradle run "-Pjcm=dfs_baseline.jcm"
```

##### DFS Baseline Agent extended with opportunistic CCRS:

* [dfs_opportunistic_ccrs.asl](src/agt/agt_archive/dfs_opportunistic_ccrs.asl) extends the DFS baseline agent with opportunistic CCRS features. It defaults to DFS but prioritizes options at every step based on opportunistic CCRS outcomes.

```powershell
gradle run "-Pjcm=dfs_opportunistic_ccrs.jcm"
```

##### DFS Baseline Agent extended with opportunistic and contingency CCRS:

* [dfs_ccrs.asl](src/agt/dfs_ccrs.asl) extends the DFS baseline agent with opportunistic CCRS features. It defaults to DFS but prioritizes options at every step based on opportunistic CCRS outcomes. It also requests contingency guidance with concrete runtime evidence; each strategy determines whether it applies.

```powershell
gradle run "-Pjcm=dfs_ccrs.jcm"
```

---

## Mindinspector URL

http://192.168.68.53:3272/

## Resources

* [JaCaMo Docs](https://jacamo-lang.github.io/doc)

* [Jason Docs](https://jason-lang.github.io/)
* [Jason API](https://jason-lang.github.io/api/jason/stdlib/package-summary.html#package.description)
* [Unification of Annotations](https://jason-lang.github.io/jason/tech/annotations.html)
* [Plan patterns](https://jason-lang.github.io/jason/tech/patterns.html)

* [Hypermedea Github](https://github.com/Hypermedea/hypermedea)
    * [Artifact](https://github.com/Hypermedea/hypermedea/blob/master/hypermedea-lib/src/main/java/org/hypermedea/HypermedeaArtifact.java)
* [Hypermedea API](https://hypermedea.github.io/javadoc/hypermedea/latest/)
    * [rdf](https://hypermedea.github.io/javadoc/hypermedea/latest/org/hypermedea/ct/rdf/package-summary.html)

* [LDFU](https://linked-data-fu.github.io/#faq)
