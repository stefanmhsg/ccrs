# PLAN_CCRS_PHYSICAL_SEPARATION: Publish and physically separate the CCRS libraries

This ExecPlan is a living document. The sections `Rules`, `Progress`, `Surprises & Discoveries`, and `Decision Log` must be kept up to date as work proceeds. Work packages must remain current with their local context, discussion, todos, concrete steps, validation, and outcomes.

No repository-local `PLANS.md` or `.agent/PLANS.md` guide is checked in. This plan follows the repository's `PLAN_<SCOPE>.md` convention and the execution-plan guidance in [AGENTS.md](AGENTS.md). It uses [CCRS_LIBRARY.md](CCRS_LIBRARY.md) as the durable architecture note; this file owns the implementation sequence and validation evidence for physical library separation.

## Purpose / Big Picture

The repository already compiles five CCRS Gradle subprojects and can publish Maven artifacts locally, but those source modules still inherit their identity, repositories, and publication configuration from the JaCaMo application root. The goal is to make each CCRS module an independently buildable and publishable library, distribute the resulting Maven packages through GitHub Packages, and make the BDI application consume released coordinates instead of Gradle project dependencies.

After this plan is complete, a developer can enter any CCRS module directory, build and test it without relying on the application root, and publish its artifacts with the same coordinates used by external consumers. A clean checkout of the separated BDI application can authenticate to GitHub Packages, resolve the selected CCRS modules, compile, and run without any sibling CCRS source directories or Maven Local artifacts. A composite workspace may still substitute local source during development, but that workspace is convenience glue rather than a hidden build requirement.

The initial GitHub Packages host is the existing `stefanmhsg/ccrs-bdi` GitHub repository at `https://maven.pkg.github.com/stefanmhsg/ccrs-bdi`. Moving the library sources or package association to another GitHub repository is a later operational migration and is not required to prove standalone modules.

## Rules

- Rule: Preserve the dependency direction `core <- jacamo <- hypermedea`, `core <- langchain4j`, and `core <- a2a`.
  Reason: This is the established architecture in [CCRS_LIBRARY.md](CCRS_LIBRARY.md), and reversing an edge would reintroduce adapter or provider coupling into core.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Define “standalone module” as an independently buildable source build and a consumable published artifact; do not require one Git repository per module.
  Reason: Independent builds and coordinate-based consumption prove the technical boundary. Keeping the builds co-located initially preserves history and makes the migration incremental.
  Added/Updated: 2026-08-08 / Codex

- Rule: Build every Java project with a Java 21 toolchain and `--release 21`.
  Reason: Java 21 is the supported source, bytecode, test, publication, and consumer baseline for the separation work; every independent build must preserve it explicitly.
  Added/Updated: 2026-08-08 / Codex

- Rule: Publish CCRS Maven artifacts to GitHub Packages under `stefanmhsg/ccrs-bdi`; retain Maven Local and the repository-local Maven directory only for local verification.
  Reason: GitHub Packages is the selected distribution target, while local repositories remain useful for fast and credential-free smoke tests.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Never commit GitHub usernames, personal access tokens, `GITHUB_TOKEN` values, API keys, or generated credential files.
  Reason: Local consumers need authenticated GitHub Packages access, and workflows receive credentials at runtime.
  Added/Updated: 2026-08-08 / Codex

- Rule: Treat `ccrs.core.contingency.strategies.internal` as a conceptual CCRS strategy grouping, not a Java visibility declaration.
  Reason: Public classes in that package are intentionally usable by library consumers. Physical separation must not make them package-private or hide them merely because the path contains `internal`.
  Added/Updated: 2026-08-08 / User direction

- Rule: Keep `ccrs-jacamo/src/main/resources/ccrs/jacamo/jason/contingency/examples.asl` application-owned example material, not part of the supported library runtime contract.
  Reason: AgentSpeak examples belong with the BDI application or an explicit example project. The reusable JaCaMo adapter should not imply that the example agent program is library API.
  Added/Updated: 2026-08-08 / User direction

- Rule: Retain the current A2A-shaped target discovery and request/response projection in the generic consultation path as a documented simplification during this migration.
  Reason: Separating that policy is not required for physical packaging and would expand this work into a semantic redesign. Documentation and tests must make the limitation visible.
  Added/Updated: 2026-08-08 / User direction

- Rule: Make structural changes additively, keep the current multi-project build working until an independent replacement build passes, and remove old wiring only after consumer validation.
  Reason: Each dependency edge can be migrated and verified without leaving the repository in an unbuildable intermediate state.
  Added/Updated: 2026-08-08 / Codex

- Rule: Update [CCRS_LIBRARY.md](CCRS_LIBRARY.md), module README files, consumer examples, service-loader documentation, and this plan in the same change as any boundary or package move.
  Reason: Consumers rely on documentation for coordinates, repository authentication, optional capability behavior, and application integration.
  Added/Updated: 2026-08-08 / Existing repository guidance and Codex

- Rule: Validate published metadata and clean consumers, not only project-to-project compilation.
  Reason: Gradle project dependencies can hide incorrect Maven scopes, missing resources, or dependencies that are available only through the root build.
  Added/Updated: 2026-08-08 / Codex

## Now / Next / Later

| NOW | NEXT | LATER |
| --- | --- | --- |
| WP4: Make `ccrs-core` and `ccrs-jacamo` standalone | WP5: Make optional capability modules standalone | WP9: Harden releases and consider repository extraction |
|  | WP6: Make Hypermedea standalone | WP10: Migrate additional consumers such as `ccrs-react` from Maven Local |
|  | WP7: Add the composite development workspace |  |
|  | WP8: Extract the BDI application as a package consumer |  |

## Progress

- [x] (2026-08-08) Confirmed that the `ccrs-bdi` worktree was clean before creating this plan.
- [x] (2026-08-08) Confirmed that `ccrs-core`, `ccrs-jacamo`, `ccrs-hypermedea`, `ccrs-langchain4j`, and `ccrs-a2a` compile and produce jars in the existing multi-project build.
- [x] (2026-08-08) Confirmed stored passing results for 80 `ccrs-core` tests and executed 8 passing `ccrs-jacamo` tests.
- [x] (2026-08-08) Published all five artifacts to `build/local-maven-repo` and ran the independent [CCRS library consumer](examples/ccrs-library-consumer/README.md), which resolved `ccrs-core` by Maven coordinate and printed a retry suggestion.
- [x] (2026-08-08) Inspected the generated POMs and found that `ccrs-langchain4j` publishes LangChain4j dependencies with runtime scope although `ChatModel` appears in public method signatures.
- [x] (2026-08-08) Confirmed that the source modules are not standalone builds because group, version, repositories, publication configuration, and inter-module project dependencies come from the root [build.gradle](build.gradle).
- [x] (2026-08-08) Inspected the sibling `ccrs-react` repository as the reference external-consumer shape. It is repository-separated but presently resolves CCRS from Maven Local and manually enumerates Java runtime dependencies.
- [x] (2026-08-08) Completed WP1: documented the standalone acceptance contract, selected and enforced Java 21 with `--release 21`, clarified strategy package semantics and the retained A2A simplification, and classified `examples.asl` as application-owned.
- [x] (2026-08-08) Rebuilt the application and CCRS dependencies and reran 80 `ccrs-core` plus 8 `ccrs-jacamo` tests under Java 21; all 88 tests passed, and `javap` reported class-file major version 65.
- [x] (2026-08-08) Completed WP2: corrected all published dependency scopes, replaced the Jena aggregate, added 15 integration-module tests, enforced the Javadoc warning baseline, and ran the all-module published consumer successfully.
- [x] (2026-08-08) Configured GitHub Packages publication, published all five
  `0.1.0-SNAPSHOT` modules, confirmed them through GitHub's package API, and ran
  the artifact-only consumer successfully with a fresh Gradle user home.
- [x] (2026-08-08 15:21Z) Committed and pushed the WP3 workflow, then completed
  [Publish CCRS snapshots run 31264049379](https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31264049379):
  publication passed in 3m42s and the fresh remote consumer passed in 1m00s.
- [ ] Complete WP4 through WP7, leaving every CCRS module independently buildable and locally composable.
- [ ] Complete WP8 so the BDI application no longer uses CCRS project dependencies.
- [ ] Complete WP9 release gates before publishing the first non-snapshot version.

## Surprises & Discoveries

- Observation: The current split is already valid at the Java import and local artifact level, but not at the source-build level.
  Evidence: All five publications succeeded and the separate core consumer ran, while module build files still declare dependencies such as `api project(':ccrs-core')` and inherit publishing from the application root.

- Observation: Before WP2, the generated `ccrs-langchain4j` POM did not expose a dependency required by its public API.
  Evidence: [Langchain4jLlmClient.java](ccrs-langchain4j/src/main/java/ccrs/capabilities/llm/langchain4j/Langchain4jLlmClient.java) returns and accepts `dev.langchain4j.model.chat.ChatModel`; WP2 corrected the owning `langchain4j-core` artifact to compile scope and proved it with the published consumer.

- Observation: At the start of WP2, three integration modules had no module-local automated tests.
  Evidence: WP2 added 4 Hypermedea, 4 LangChain4j, and 7 A2A tests under their respective `src/test` trees; all 15 pass without live external services.

- Observation: `ccrs-react` is a useful ownership example but not yet a remote-package consumer example.
  Evidence: `../ccrs-react/react_agent/ccrs/java_runtime.py` locates module jars in Maven Local or Gradle caches and maintains explicit lists of Jena, LangChain4j, and A2A runtime dependencies.

- Observation: GitHub Packages authentication is required for package download as well as publication, including public Maven/Gradle packages under the current GitHub Packages model.
  Evidence: GitHub's Gradle registry documentation requires a classic personal access token for local clients and permits `GITHUB_TOKEN` in workflows with package access. This affects developer setup and clean consumer tests.

- Observation: The repository already ran on a Java 21 launcher, but its build did not declare a toolchain or bytecode release.
  Evidence: Before WP1, `gradlew --version` reported Gradle 9.2.0 and launcher JVM 21 with “no JDK specified”; after adding the Java toolchain and `options.release`, the clean validation build passed and `ContingencyCcrs.class` reported major version 65.

- Observation: The public `ChatModel` type is owned by `langchain4j-core`, and publishing the higher-level `langchain4j` artifact at compile scope still left a clean consumer unable to compile.
  Evidence: The first expanded consumer failed with `package dev.langchain4j.model.chat does not exist`; changing the direct API dependency to `dev.langchain4j:langchain4j-core:1.10.0`, republishing, and refreshing the fixture made the same source compile and run.

- Observation: A full JaCaMo artifact consumer needs Gradle's library repository in addition to the JaCaMo Maven repository.
  Evidence: The expanded fixture initially failed to resolve `org.gradle:gradle-tooling-api:8.10`; adding `https://repo.gradle.org/gradle/libs-releases` to its standalone settings resolved the published `ccrs-jacamo` graph.

- Observation: Maven repository order can hide corrected snapshot metadata or let a broad specialized repository answer for unrelated dependencies.
  Evidence: When Maven Local preceded the repository-local Maven directory, the consumer selected an older `ccrs-langchain4j` snapshot despite `--refresh-dependencies`. Removing Maven Local made `build/local-maven-repo` authoritative, and placing Maven Central before the JaCaMo/Hypermedea repositories removed a malformed third-party POM warning from the refreshed run.

- Observation: The repository-wide `.gitignore` originally excluded the whole
  `.github` directory, which also excluded deployable Actions workflows.
  Evidence: `git check-ignore -v .github/workflows/publish-ccrs-snapshots.yml`
  resolved to the old `.github` rule. WP3 narrowed the rule and explicitly
  re-included `.github/workflows/**`.

- Observation: A fresh Gradle user home intentionally does not inherit
  `%USERPROFILE%\.gradle\gradle.properties`.
  Evidence: The first remote fixture run downloaded a fresh Gradle 9.2.0
  distribution and stopped with the fixture's missing-credentials message.
  Supplying the same credentials as process-only `GITHUB_ACTOR` and
  `GITHUB_TOKEN` variables made the isolated run resolve and execute all remote
  artifacts. The GitHub workflow already uses this environment-variable path.

- Observation: GitHub Actions does not expose the `runner` expression context
  while it evaluates job-level `env`.
  Evidence: Workflow validation rejected
  `jobs.remote-consumer.env.GRADLE_USER_HOME` with `Unrecognized named-value:
  'runner'`. Moving the same expression to the `Resolve and run remote
  publications` step's `env` makes it evaluate after a runner has been
  assigned while preserving the fresh Gradle user home.

- Observation: A working local wrapper script does not prove that a clean
  checkout contains the Gradle wrapper launcher.
  Evidence: The first dispatched workflow reached `./gradlew` but failed with
  `Unable to access jarfile .../gradle/wrapper/gradle-wrapper.jar` because the
  blanket `*.jar` ignore rule excluded the launcher. Commit `4c20e70` added the
  narrow ignore exception and tracked the 45 KB wrapper jar; the replacement
  workflow completed both jobs successfully.

## Decision Log

- Decision: Use GitHub Packages as the first remote Maven registry, associated with `stefanmhsg/ccrs-bdi`.
  Rationale: The repository already owns the source and Maven coordinates, and GitHub Actions can publish with the repository `GITHUB_TOKEN`. A future package-host move can be planned separately after consumers are stable.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Keep the five library builds in one repository during physical separation.
  Rationale: Standalone Gradle builds and coordinate-only consumers prove the boundary without creating an early multi-repository release-coordination problem.
  Date/Author: 2026-08-08 / Codex

- Decision: Keep public strategy classes public even when their package path contains `strategies.internal`.
  Rationale: `internal` expresses conceptual grouping inside CCRS rather than access policy. The migration will document this meaning instead of changing visibility.
  Date/Author: 2026-08-08 / User direction

- Decision: Move `examples.asl` to the BDI application or an explicit app example before declaring `ccrs-jacamo` standalone.
  Rationale: It is an application example and should not become part of the library resource contract.
  Date/Author: 2026-08-08 / User direction

- Decision: Do not redesign A2A consultation target discovery or projection in this plan.
  Rationale: The current behavior is an accepted, documented simplification. Physical separation will add characterization tests and documentation but preserve semantics.
  Date/Author: 2026-08-08 / User direction

- Decision: Model the eventual BDI repository after the ownership boundary demonstrated by `ccrs-react`, but resolve CCRS from GitHub Packages instead of sibling sources or Maven Local.
  Rationale: Both applications should be consumers of the same library artifacts rather than owners of reusable CCRS implementation code.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Use Java 21 as the supported baseline for every Java library, the BDI application, build fixture, and published consumer in this plan.
  Rationale: The repository already uses Java 21 locally, the consumer fixture already requests it, and one explicit toolchain plus `--release` value prevents independently extracted builds from producing inconsistent bytecode.
  Date/Author: 2026-08-08 / Codex

- Decision: Publish `jena-core` and `jena-arq` directly as the core API instead of the `apache-jena-libs` aggregate.
  Rationale: `CcrsVocabulary` publicly exposes both `Model` and `Query`; these two modules own those types and cover the implementation without publishing unrelated Jena modules as the CCRS API surface.
  Date/Author: 2026-08-08 / Codex

- Decision: Publish `langchain4j-core` directly as the LangChain4j module API and keep `langchain4j-open-ai` plus dotenv at runtime scope.
  Rationale: `ChatModel` is public CCRS API and must compile transitively, while the OpenAI client and configuration bridge are provider implementation details.
  Date/Author: 2026-08-08 / Codex

- Decision: Treat missing Javadoc comments as the temporary documentation baseline and fail every other Javadoc warning.
  Rationale: Existing missing-comment debt is broad, but `-Xdoclint:all,-missing` plus `-Werror` immediately prevents malformed documentation and new non-missing warnings from entering published Javadocs.
  Date/Author: 2026-08-08 / Codex

- Decision: Let the consumer select exactly one CCRS repository with
  `-PccrsRepository=local|github` and make that repository exclusive to the
  `io.github.stefanmhsg.ccrs` group.
  Rationale: A clean remote check must not fall back to the repository-local
  directory, while specialized third-party repositories must not answer for
  CCRS coordinates.
  Date/Author: 2026-08-08 / Codex

- Decision: Publish snapshots only from a manually dispatched workflow and
  enforce the `-SNAPSHOT` suffix in Gradle before every GitHub Packages upload.
  Rationale: Pull requests and ordinary branch builds must remain read-only;
  stable versions require the separate tag-driven release gates in WP9.
  Date/Author: 2026-08-08 / Codex

## Context and Orientation

The current `ccrs-bdi` repository has two roles. The root project is a JaCaMo/Jason application containing `.jcm` configurations, AgentSpeak `.asl` programs, experiments, and environment integration. Five `ccrs-*` subprojects contain reusable Java libraries. [AGENTS.md](AGENTS.md) and [CCRS_LIBRARY.md](CCRS_LIBRARY.md) define their conceptual boundaries.

`ccrs-core` contains agent-agnostic RDF, opportunistic CCRS, contingency CCRS, strategy configuration, and provider extension points. `ccrs-jacamo` adapts core to Jason, JaCaMo, and CArtAgO. `ccrs-hypermedea` adds a Hypermedea HTTP artifact and history implementation and depends on both core and JaCaMo. `ccrs-langchain4j` provides a LangChain4j-backed `LlmClient` and strategy provider. `ccrs-a2a` provides an A2A-backed consultation channel and strategy provider.

The root [settings.gradle](settings.gradle) includes all five subprojects. The root [build.gradle](build.gradle) supplies common repositories, group `io.github.stefanmhsg.ccrs`, version `0.1.0-SNAPSHOT`, Maven publication configuration, and application dependencies on every module. Each module build file is therefore only a fragment of a larger build.

A standalone build has its own `settings.gradle`, wrapper, complete build configuration, artifact identity, dependency repositories, tests, and publication definition. Its dependencies on other CCRS modules are Maven coordinates, for example `io.github.stefanmhsg.ccrs:ccrs-core:<version>`, rather than Gradle project paths. It must build when its sibling source directories are unavailable.

A composite build is an optional Gradle workspace that includes several complete builds and substitutes matching Maven coordinates with local projects. It preserves fast cross-module development without making any module depend on the workspace. The authoritative proof remains running each build alone and resolving its published form in a clean consumer.

GitHub Packages exposes a Maven-compatible Gradle registry. The initial repository URL is:

    https://maven.pkg.github.com/stefanmhsg/ccrs-bdi

In GitHub Actions, publication uses the workflow's `GITHUB_TOKEN` with `packages: write` and source checkout with `contents: read`. A local publisher or consumer uses runtime properties or environment variables, never tracked files. Use property names `gpr.user` and `gpr.key`, with `GITHUB_ACTOR` and `GITHUB_TOKEN` as CI fallbacks. Local package reads require a classic personal access token with `read:packages`; local publishing also requires `write:packages`.

The sibling `../ccrs-react` repository demonstrates the desired ownership split: application and adapter code live outside `ccrs-bdi`, while Java CCRS is loaded as Maven artifacts through JPype. It currently assumes Maven Local and explicit dependency caches, so it is a reference for application separation rather than the final remote-resolution implementation.

## Work Packages

### WP1: Freeze the standalone build and consumer contract

Status: Done

Purpose: Establish unambiguous acceptance criteria before changing build topology. A contributor should know exactly which files remain application-owned, what “standalone” means, and which behavior is deliberately unchanged.

Local context: Read [AGENTS.md](AGENTS.md), [CCRS_LIBRARY.md](CCRS_LIBRARY.md), every module `README.md`, the root [settings.gradle](settings.gradle), and all six current `build.gradle` files. Use the decisions in this plan as constraints.

Discussion: The physical migration must not become a semantic redesign. Public strategy types remain public despite the word `internal`; `examples.asl` is app-owned; and A2A-shaped consultation behavior remains a documented simplification. The first migration release remains `0.1.0-SNAPSHOT`, and all modules remain on one aligned version until independent release cadence is justified.

Todos:

- [x] Add a concise “standalone acceptance contract” to [CCRS_LIBRARY.md](CCRS_LIBRARY.md) and link this plan.
- [x] Record Java 21 as the supported version and enforce a Java 21 Gradle toolchain plus `options.release = 21` for every Java project.
- [x] Document that `strategies.internal` is conceptual grouping and not visibility policy.
- [x] Document the retained A2A consultation simplification in the core social-strategy and A2A module README files.
- [x] Mark `examples.asl` for relocation with the BDI application in WP8.
- [x] Define a standalone module as passing its own `build`, `test`, `publishToMavenLocal`, and clean coordinate-consumer checks with sibling sources absent.

Concrete steps: From `S:\dev\ma\ccrs-bdi`, review the dependency graph and baseline again:

    .\gradlew.bat classes :ccrs-core:test :ccrs-jacamo:test

Record the Java and Gradle versions:

    .\gradlew.bat --version

Update the documentation named above without moving Java source. Add the exact chosen Java baseline to every later work package.

Validation and acceptance: The existing build remains green. A reader can determine from this plan and [CCRS_LIBRARY.md](CCRS_LIBRARY.md) which files belong to libraries versus the application and can explain why no A2A policy or strategy visibility refactor is part of this migration.

Outcome and notes: Completed on 2026-08-08. [CCRS_LIBRARY.md](CCRS_LIBRARY.md) now defines the standalone source-build and clean-consumer contract and links this plan. The root build configures a Java 21 toolchain and `--release 21` for the application and every `ccrs-*` project. The contingency documentation explains that `strategies.internal` is conceptual, not a visibility boundary. The core social-strategy and A2A README files retain the current A2A/RDF discovery and projection rules as accepted, characterized simplifications. `examples.asl` is explicitly application-owned and scheduled to move in WP8. Gradle 9.2.0 on Java 21 rebuilt the project and reran all 88 focused tests successfully; generated core bytecode has class-file major version 65.

### WP2: Repair publication metadata and close library test gaps

Status: Done

Purpose: Make the currently published artifacts truthful and sufficiently tested before changing their physical builds. Clean consumers should receive everything required to compile against public APIs and run provider discovery.

Local context: [ccrs-langchain4j/build.gradle](ccrs-langchain4j/build.gradle) now publishes the `langchain4j-core` artifact containing the public `ChatModel` type as API. The optional providers remain registered through files under `META-INF/services`. The [standalone consumer build.gradle](examples/ccrs-library-consumer/build.gradle) now compiles against all five published coordinates. All compilation and consumer fixtures in this package target Java 21 with `--release 21` where Java sources are compiled.

Discussion: Prefer the smallest truthful dependency surface. The exact LangChain4j artifact containing `ChatModel` is `langchain4j-core`, so it is `api`; OpenAI implementation and dotenv remain `implementation`. A2A SDK types are implementation details because the public channel API exposes core consultation types. Characterization tests lock down the accepted A2A simplification without extracting it.

Todos:

- [x] Change the LangChain4j dependency that supplies public `ChatModel` types from `implementation` to `api`; keep provider-specific implementation dependencies internal where possible.
- [x] Inspect public signatures in all five jars and align every external dependency with `api`, `implementation`, or `runtimeOnly` accurately.
- [x] Replace the broad `org.apache.jena:apache-jena-libs` API dependency with the smallest explicit Jena modules that compile and run the public core API, unless evidence shows the aggregate is required.
- [x] Add `ccrs-hypermedea` tests for SPI packaging, binding construction, interaction logging, and runtime provider installation.
- [x] Add `ccrs-langchain4j` tests for public API compilation, provider discovery, missing configuration, and a fake `ChatModel` path without network calls.
- [x] Add `ccrs-a2a` tests for provider discovery, missing configuration, target discovery, response mapping, and the documented request/response simplification using fakes rather than live agents.
- [x] Extend the consumer fixtures to compile against each module's public API and verify `ServiceLoader` resources from published jars.
- [x] Make Javadoc generation warning-free or define and document a temporary warning baseline that fails on new warnings.

Concrete steps: Run focused tests while implementing:

    .\gradlew.bat :ccrs-core:test :ccrs-jacamo:test :ccrs-hypermedea:test :ccrs-langchain4j:test :ccrs-a2a:test

Publish to the repository-local Maven directory and inspect the resulting POM files:

    .\gradlew.bat :ccrs-core:publishMavenJavaPublicationToCcrsLocalRepository :ccrs-jacamo:publishMavenJavaPublicationToCcrsLocalRepository :ccrs-hypermedea:publishMavenJavaPublicationToCcrsLocalRepository :ccrs-langchain4j:publishMavenJavaPublicationToCcrsLocalRepository :ccrs-a2a:publishMavenJavaPublicationToCcrsLocalRepository

Run every consumer fixture against `build/local-maven-repo`. Add a fixture that calls `Langchain4jLlmClient.fromModel(...)`; it must compile without declaring LangChain4j separately.

Validation and acceptance: All module tests pass. Generated POM compile scopes match types visible in public signatures. Published jars contain the two `CcrsStrategyProvider` files and the Hypermedea `ProtocolBinding` file. Consumer fixtures compile and run using only declared CCRS coordinates and repositories.

Outcome and notes: Completed on 2026-08-08. The final published scope table is:

| Module | Compile scope | Runtime scope |
| --- | --- | --- |
| `ccrs-core` | `jena-core`, `jena-arq` | None |
| `ccrs-jacamo` | `ccrs-core`, `org.jacamo:jacamo` | None |
| `ccrs-hypermedea` | `ccrs-core`, `ccrs-jacamo`, `org.hypermedea:hypermedea` | None |
| `ccrs-langchain4j` | `ccrs-core`, `langchain4j-core` | `langchain4j-open-ai`, `dotenv-java` |
| `ccrs-a2a` | `ccrs-core` | `dotenv-java`, `a2a-java-sdk-reference-rest`, `a2a-java-sdk-client`, `a2a-java-sdk-client-transport-rest` |

Public-signature inspection confirms that core exposes Jena `Model` and `Query`, JaCaMo exposes core and JaCaMo types, Hypermedea exposes core/JaCaMo/Hypermedea types, LangChain4j exposes core and `ChatModel`, and A2A exposes only core types. All 103 focused tests passed: core 80, JaCaMo 8, Hypermedea 4, LangChain4j 4, and A2A 7. The three new suites use fake operations, clients, and channels and perform no live service calls. All five Javadoc tasks pass with missing comments excluded and every other warning treated as an error. The published consumer compiles all five coordinates with no project dependency or Maven Local repository, discovers both `CcrsStrategyProvider` implementations and the Hypermedea `ProtocolBinding`, invokes a fake `ChatModel`, and evaluates core successfully.

### WP3: Establish GitHub Packages snapshot publication

Status: Done

Purpose: Prove the remote distribution path before independent builds rely on it. A clean authenticated consumer must resolve all five snapshot artifacts without Maven Local, the repository-local Maven directory, sibling sources, or a warmed Gradle cache.

Local context: The current Maven publications are created in the root [build.gradle](build.gradle). GitHub Packages uses repository-scoped Maven URLs and authentication for reads and writes. The remote is `https://github.com/stefanmhsg/ccrs-bdi.git`. Publication and clean-consumer workflows provision Java 21 explicitly.

Discussion: Keep local publication tasks alongside GitHub publication. Publishing is an external state change and must occur only from an explicitly invoked release workflow or an authorized local command. Normal pull-request CI builds and tests but does not publish. Snapshot publication may use a manual workflow or a protected branch event; non-snapshot publication must be tag-driven in WP9.

Todos:

- [x] Add a `GitHubPackages` Maven publication repository using `https://maven.pkg.github.com/stefanmhsg/ccrs-bdi`.
- [x] Resolve credentials from `gpr.user`/`gpr.key` locally and `GITHUB_ACTOR`/`GITHUB_TOKEN` in CI without logging secrets.
- [x] Add a GitHub Actions workflow with `contents: read` and `packages: write` that builds, tests, and publishes snapshots only when explicitly triggered.
- [x] Add consumer repository documentation for `read:packages` authentication.
- [x] Add a clean remote consumer smoke job using a fresh `GRADLE_USER_HOME` and no `mavenLocal()` repository.
- [x] Verify sources jars, Javadocs jars, Gradle module metadata, POMs, and service files after remote resolution.
- [x] Commit and push the workflow, dispatch it manually, and record its first
  successful run URL.

Concrete steps: Configure publication so the task name is stable, for example `publishMavenJavaPublicationToGitHubPackagesRepository`. In CI, run:

    .\gradlew.bat test publishAllPublicationsToGitHubPackagesRepository

The clean consumer job must set a new temporary `GRADLE_USER_HOME`, configure only Maven Central, required JaCaMo/Hypermedea repositories, and GitHub Packages, then run its build with dependency refresh:

    .\gradlew.bat --refresh-dependencies build run

Do not publish from a pull request originating from untrusted code, and do not expose package credentials to forks.

Validation and acceptance: GitHub shows five `0.1.0-SNAPSHOT` Maven packages associated with `stefanmhsg/ccrs-bdi`. A clean authenticated fixture downloads and runs core, compiles against the LangChain4j `ChatModel` API, discovers optional providers when their jars are selected, and does not read from Maven Local.

Outcome and notes: Implementation and direct remote validation completed on
2026-08-08. The workflow is named `Publish CCRS snapshots` and lives at
[publish-ccrs-snapshots.yml](.github/workflows/publish-ccrs-snapshots.yml). The
local authorized publication created all five package records under
`stefanmhsg/ccrs-bdi`, and GitHub's package API reports
`0.1.0-SNAPSHOT` for every coordinate. A fresh Gradle 9.2.0 user home resolved
five sources jars, five Javadocs jars, five POMs, and five Gradle metadata files
from GitHub Packages; the consumer then discovered both strategy providers and
the Hypermedea binding, invoked the public `ChatModel` path, and evaluated core.
The first clean-checkout attempt exposed the ignored Gradle wrapper jar and
therefore stopped before publication. After tracking the wrapper launcher,
[Publish CCRS snapshots run 31264049379](https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31264049379)
completed successfully at commit `4c20e70`: the build, test, Javadoc, and
publication job passed in 3m42s, followed by the isolated remote-consumer job in
1m00s. WP3 is complete; WP4 is now the only active work package.

### WP4: Make `ccrs-core` and `ccrs-jacamo` independent builds

Status: Now

Purpose: Establish the reusable pattern on the foundational library and its primary BDI adapter. Each directory must build by itself while the existing root build continues to work until the replacement path is proven.

Local context: `ccrs-core` has no CCRS module dependency. `ccrs-jacamo` depends on core and JaCaMo. Both currently inherit group, version, repositories, publication, sources jars, Javadocs jars, and the Java 21 toolchain/`--release 21` baseline from the root. Their independent builds must declare that same Java baseline locally.

Discussion: Start with core because every other build depends on it. Give each module a complete root build with `rootProject.name` matching its artifact ID. In `ccrs-jacamo`, request core by Maven coordinate. During local development, resolve a published local snapshot or use the composite workspace introduced in WP7; never fall back to a filesystem project path inside the standalone build.

Todos:

- [ ] Add complete standalone Gradle settings and build configuration to `ccrs-core`.
- [ ] Add a wrapper or a documented repository-level wrapper invocation that still works when the module directory is copied alone. Prefer a wrapper per independent build.
- [ ] Move core publication, repository, toolchain, test, sources, Javadocs, POM metadata, and GitHub Packages configuration out of root-only inheritance.
- [ ] Repeat the standalone setup for `ccrs-jacamo`.
- [ ] Replace `api project(':ccrs-core')` in the standalone JaCaMo build with the aligned core coordinate.
- [ ] Keep `CcrsAgent` and `CcrsAgentArch` together.
- [ ] Ensure `examples.asl` is no longer packaged as a supported JaCaMo library resource; preserve it for WP8 relocation.
- [ ] Keep the old multi-project wiring only until standalone and consumer checks pass, then remove duplicate inclusion safely.

Concrete steps: From each module directory, with sibling modules unavailable through project paths, run:

    cd S:\dev\ma\ccrs-bdi\ccrs-core
    .\gradlew.bat build publishToMavenLocal

    cd S:\dev\ma\ccrs-bdi\ccrs-jacamo
    .\gradlew.bat build publishToMavenLocal

Use a fresh Gradle user home for at least one CI job so undeclared inherited state cannot hide missing repositories or dependencies.

Validation and acceptance: Both commands succeed independently. The JaCaMo POM declares `ccrs-core` and `org.jacamo:jacamo` at compile scope. No standalone build reads the application root build script, `.jcm-deps.gradle`, application source sets, `lib`, `.jcm`, `.asl`, or experiment paths.

Outcome and notes: Record the final build layout and whether wrappers are duplicated or generated through a documented maintenance task.

### WP5: Make `ccrs-langchain4j` and `ccrs-a2a` independent builds

Status: Next

Purpose: Make each optional provider independently buildable, publishable, and usable through `ServiceLoader` without the BDI application or the other optional capability.

Local context: Both modules depend only on core at the CCRS layer. They own their dotenv bridges and separate service descriptors. Their providers must tolerate missing runtime configuration without preventing core-only operation. Their standalone builds and test fixtures use Java 21 and produce Java 21 bytecode.

Discussion: Preserve provider independence. `ccrs-a2a` must not depend on `ccrs-langchain4j`, and neither module may cause `ccrs-jacamo` to import capability classes. The accepted A2A discovery and projection simplification remains unchanged and is verified by characterization tests.

Todos:

- [ ] Give each capability a complete standalone settings/build/wrapper configuration.
- [ ] Replace core project dependencies with the published core coordinate.
- [ ] Carry forward the corrected API and implementation dependency scopes from WP2.
- [ ] Ensure each service descriptor is packaged exactly once.
- [ ] Verify absent configuration produces a controlled “not registered” outcome rather than build or startup failure.
- [ ] Verify each provider can be selected alone and both can coexist on the same classpath.

Concrete steps:

    cd S:\dev\ma\ccrs-bdi\ccrs-langchain4j
    .\gradlew.bat build publishToMavenLocal

    cd S:\dev\ma\ccrs-bdi\ccrs-a2a
    .\gradlew.bat build publishToMavenLocal

Run published consumer fixtures with core plus each optional module and with all three together.

Validation and acceptance: Both builds pass without sibling project paths. Clean fixtures discover only providers whose artifacts are present, register configured strategies, tolerate missing secrets, and preserve the documented A2A behavior.

Outcome and notes: Record service-loader results and any SDK repository requirements.

### WP6: Make `ccrs-hypermedea` an independent build

Status: Next

Purpose: Complete the library set with an independently buildable Hypermedea adapter and prove its transitive CCRS and framework metadata.

Local context: Hypermedea depends on core, JaCaMo, and `org.hypermedea:hypermedea`. It implements the replaceable interaction history provider and packages `META-INF/services/org.hypermedea.op.ProtocolBinding`. Its standalone build uses the same Java 21 toolchain and `--release 21` contract as the other modules.

Discussion: This module follows core and JaCaMo because both must already exist as remote/local coordinates. Keep `ccrs.hypermedea` as the implementation package. Do not move the implementation back under `ccrs.jacamo`; add compatibility wrappers only if a released consumer requires them.

Todos:

- [ ] Add complete standalone settings/build/wrapper configuration.
- [ ] Replace core and JaCaMo project dependencies with coordinates.
- [ ] Preserve required Hypermedea repository configuration within this build.
- [ ] Verify public signatures and POM scopes for JaCaMo and Hypermedea APIs.
- [ ] Verify the protocol-binding service descriptor and runtime provider installation.

Concrete steps:

    cd S:\dev\ma\ccrs-bdi\ccrs-hypermedea
    .\gradlew.bat build publishToMavenLocal

Run a fixture that instantiates or discovers `ccrs.hypermedea.CcrsHttpBinding` using only published artifacts.

Validation and acceptance: The standalone build passes, its POM exposes required compile dependencies, the SPI file is in the runtime jar, and no application `.jcm`, `.asl`, log, or experiment content is packaged.

Outcome and notes: Record the final transitive dependency graph and SPI validation result.

### WP7: Add a composite workspace for local multi-module development

Status: Next

Purpose: Restore the convenience of one-command cross-module development without weakening standalone boundaries.

Local context: By this point each `ccrs-*` directory is a complete Java 21 Gradle build and declares other CCRS modules by coordinate. Gradle composite builds can substitute those coordinates with local included builds when group and project name match; substitution must not change the Java 21 toolchain or release target.

Discussion: The composite root owns no production code and publishes nothing. It is allowed to orchestrate build/test tasks, but no included build may depend on configuration inherited from it. Explicit dependency substitution may be used if publication coordinates differ from `group:rootProject.name`.

Todos:

- [ ] Replace the old subproject includes with an aggregator that uses `includeBuild` for every independent CCRS build.
- [ ] Add substitutions for all five coordinates if automatic matching is insufficient.
- [ ] Add aggregate verification tasks without duplicating module build logic.
- [ ] Document how to build one module in isolation, all libraries together, and a consumer with local substitutions.
- [ ] Add a CI comparison that validates both composite-source and published-artifact paths.

Concrete steps: From the workspace root, run the composite aggregate verification task, then run each module build directly. Also disable composite substitution for one consumer configuration and confirm it resolves the published artifact rather than local source.

Validation and acceptance: A source edit in core is visible to a locally included JaCaMo build without publishing. The same JaCaMo build succeeds alone by resolving the configured core coordinate. Published and substituted dependency graphs remain behaviorally equivalent.

Outcome and notes: Record the aggregator layout and any explicit substitutions.

### WP8: Extract the BDI application as a package-only consumer

Status: Next

Purpose: Prove the final ownership boundary by making the JaCaMo BDI application independent of library source, like the sibling `ccrs-react` repository, while consuming GitHub Packages rather than Maven Local.

Local context: Application-owned files include root `.jcm` files, `src/agt/**/*.asl`, `src/env`, `src/org`, `.env.example`, logs, experiments, and app documentation. The current root [build.gradle](build.gradle) declares project dependencies on all five modules. The extracted BDI build must retain the Java 21 toolchain and `--release 21`. `ccrs-react` is already a separate application repository that loads CCRS Maven artifacts through JPype, but its current resolver is Maven-Local-oriented.

Discussion: First make an independent application build inside the current repository or a sibling staging directory. Move it to a separate Git repository only after its coordinate-only build and run pass. Select dependencies by actual application usage: compile-time APIs use `implementation`; modules reached only through `ServiceLoader`, SPI, or reflection use `runtimeOnly`. Keep application profiles explicit so a core/JaCaMo-only run does not pull every optional capability.

Todos:

- [ ] Create the standalone BDI application build and move all app-owned source, resources, `.jcm`, `.asl`, and experiment references into it.
- [ ] Move `ccrs-jacamo/src/main/resources/ccrs/jacamo/jason/contingency/examples.asl` into the application example location and update links.
- [ ] Replace all CCRS project dependencies with GitHub Packages coordinates.
- [ ] Add authenticated repository configuration without tracked credentials.
- [ ] Classify selected modules as `implementation` or `runtimeOnly` based on actual compile-time use.
- [ ] Add capability profiles or properties for core/JaCaMo, Hypermedea, LangChain4j, and A2A combinations.
- [ ] Repair or replace the known broken JaCaMo AgentSpeak test fixture so application `test` has a trustworthy result.
- [ ] Run application smoke configurations without sibling CCRS sources or Maven Local.
- [ ] After validation, extract the application to its intended repository while preserving relevant history.

Concrete steps: In a clean application checkout, configure GitHub Packages credentials and run:

    .\gradlew.bat --refresh-dependencies classes
    .\gradlew.bat test
    .\gradlew.bat run

Also run the existing DFS configurations documented in the application README. For an offline verification, pre-populate only the declared remote dependencies, disable network access, and ensure no filesystem reference points back to the library source repository.

Validation and acceptance: The BDI application compiles and runs from a checkout that contains no `ccrs-core`, `ccrs-jacamo`, `ccrs-hypermedea`, `ccrs-langchain4j`, or `ccrs-a2a` source directories. It resolves the selected versions from GitHub Packages, loads expected service providers, and keeps all `.jcm`, `.asl`, environment, logging, and experiment ownership in the application repository.

Outcome and notes: Record the final application repository, selected default capability set, and successful run transcripts.

### WP9: Harden versioned releases and evaluate source-repository splitting

Status: Later

Purpose: Turn the proven snapshot pipeline into a maintainable release process and decide whether independent source repositories add value.

Local context: GitHub Packages Maven/Gradle packages are repository-scoped. All modules currently share group and version and publish Java 21 bytecode. GitHub Packages authentication is required for consumers, which is acceptable for the selected target but should be reconsidered if broad anonymous public consumption becomes a goal.

Discussion: Independent builds do not require independent release versions. Begin with an aligned release train and optionally publish a `ccrs-platform` Java platform/BOM. Split Git repositories only if ownership, release cadence, or permissions differ enough to justify multiple coordinated workflows and package repository URLs.

Todos:

- [ ] Add complete POM name, description, project URL, SCM, license, and developer metadata.
- [ ] Add a license and changelog/release notes appropriate to the chosen distribution policy.
- [ ] Add tag-driven non-snapshot publication with version/tag consistency checks.
- [ ] Add binary/source API compatibility checks and dependency vulnerability reporting.
- [ ] Add reproducible clean consumer jobs for Gradle, and Maven if Maven consumers are supported.
- [ ] Decide whether to publish a `ccrs-platform` alignment artifact.
- [ ] Compare GitHub Packages authentication friction with the intended audience and record whether Maven Central should become an additional later target.
- [ ] Decide whether any module needs its own source repository; if so, preserve history and do not change coordinates in the same step.

Concrete steps: Publish a release candidate version from a protected tag, consume it from clean BDI and fixture checkouts, then publish the first stable `0.x` version only when every plan-wide acceptance check passes.

Validation and acceptance: A release tag produces immutable versioned artifacts once, clean consumers resolve that version, API checks compare it with the previous release, and no snapshot/local repository is required.

Outcome and notes: Record the first stable version, package links, compatibility report, and repository-topology decision.

### WP10: Migrate additional consumers from Maven Local

Status: Later

Purpose: Make other CCRS consumers, beginning with `ccrs-react`, consume the same remotely published metadata and remove duplicated dependency knowledge where practical.

Local context: `../ccrs-react/react_agent/ccrs/java_runtime.py` currently searches Maven Local and Gradle caches and hardcodes runtime dependency coordinates. Its separation from `ccrs-bdi` is the desired repository ownership pattern, but its resolver predates remote GitHub Packages distribution. Its JPype runtime and materialized Java classpath must support the published Java 21 baseline.

Discussion: This work belongs primarily in each consumer repository. Do not change `ccrs-react` as part of the BDI extraction unless its own active plan is updated. Prefer resolving a generated classpath from Gradle/Maven metadata rather than maintaining a second hand-written transitive dependency list in Python.

Todos:

- [ ] Add a `ccrs-react` work package to its active plan for GitHub Packages authentication and dependency resolution.
- [ ] Replace Maven-Local-only assumptions with a remote-capable dependency materialization step.
- [ ] Remove hardcoded dependency entries after published metadata is proven sufficient.
- [ ] Retain an explicit local development override for snapshots or composite-produced jars.
- [ ] Repeat the clean consumer and provider-discovery tests in `ccrs-react`.

Concrete steps: Work from the `ccrs-react` repository under its own guidance. Materialize the requested CCRS modules and their runtime dependencies into a deterministic classpath directory using Gradle or Maven metadata, then point JPype at that directory.

Validation and acceptance: A clean `ccrs-react` checkout authenticates to GitHub Packages, downloads the requested CCRS version and transitive dependencies, runs its JPype integration tests, and does not require a sibling `ccrs-bdi` checkout or Maven Local publication.

Outcome and notes: Record changes in the `ccrs-react` execution plan rather than duplicating detailed progress here.

## Validation and Acceptance

Plan-wide acceptance requires all of the following observable behavior:

1. Each of the five module directories can run its own wrapper `build` and `publishToMavenLocal` with no application root configuration and no Gradle project dependency on a sibling CCRS source directory.
2. The dependency graph remains one-directional: core has no adapter/capability dependencies; JaCaMo depends only on core at the CCRS layer; Hypermedea depends on core and JaCaMo; LangChain4j and A2A depend only on core.
3. Every generated POM and Gradle module metadata file accurately distinguishes compile API from implementation/runtime dependencies. In particular, a clean consumer can compile code using `Langchain4jLlmClient.fromModel(ChatModel)` without separately declaring LangChain4j.
4. Module tests cover core behavior, JaCaMo parsing/adaptation, Hypermedea SPI/history, LangChain4j provider behavior, and A2A provider/characterization behavior without live external services.
5. GitHub Packages hosts sources, Javadocs, POM, Gradle module metadata, and runtime jars for all five coordinates.
6. A fresh authenticated Gradle user home resolves and runs the published consumer fixtures without Maven Local, `build/local-maven-repo`, sibling builds, or cached CCRS jars.
7. Service loading registers only optional providers present on the runtime classpath and tolerates missing API keys or endpoints.
8. The BDI application builds and runs as a separate coordinate-only consumer and owns all `.jcm`, `.asl`, environment, logging, and experiment content, including the relocated `examples.asl`.
9. The accepted semantic constraints remain unchanged: `strategies.internal` public classes stay accessible, and the current A2A discovery/projection behavior remains documented and tested as a simplification.
10. The composite workspace is optional: local substitutions work, but disabling them yields the same dependency shape and observable behavior from published artifacts.

At every work-package stopping point, run `git status --short`, record validation in `Progress` and the package's outcome, and update discoveries or decisions that changed the approach.

## Idempotence and Recovery

Build and test commands are safe to repeat. Local publication of snapshots may replace or add local snapshot artifacts and should be treated as disposable verification output. Repository-local artifacts under `build/local-maven-repo` are generated output and may be regenerated.

Do not delete the old multi-project wiring when introducing a standalone build. First make the new build additive, validate it in isolation, then remove the old include or inheritance in a later focused change. If a module extraction fails, restore the last green configuration by reverting only that focused change; do not reset unrelated user work.

GitHub Packages publication is externally visible and is not a disposable local action. Use new snapshot builds during experimentation and protected tags for releases. Never reuse or overwrite a stable release version. If incorrect metadata is published under a snapshot, fix the build and publish a newer snapshot. If an incorrect stable version is published, leave it immutable, document it, and publish a corrected patch version.

When moving the BDI application or a module to another repository, first create and validate a copy or history-preserving split. Do not remove the original source until the new repository's CI and consumer checks pass. Coordinate changes, package-host changes, and source-repository moves must be separate changes so failures can be diagnosed and reversed independently.

## Artifacts and Notes

Baseline verification from 2026-08-08:

    .\gradlew.bat classes :ccrs-core:test :ccrs-jacamo:test :ccrs-hypermedea:jar :ccrs-langchain4j:jar :ccrs-a2a:jar

    BUILD SUCCESSFUL

WP1 repeated the build with `--rerun-tasks` after enforcing the Java baseline:

    .\gradlew.bat classes :ccrs-core:test :ccrs-jacamo:test --rerun-tasks

    BUILD SUCCESSFUL
    ccrs-core tests: 80 passed
    ccrs-jacamo tests: 8 passed
    ContingencyCcrs.class major version: 65 (Java 21)

The wrapper reports Gradle 9.2.0 and the configured/launcher JDK is Java 21.

WP2 rebuilt the application and ran every focused module suite:

    .\gradlew.bat classes :ccrs-core:test :ccrs-jacamo:test :ccrs-hypermedea:test :ccrs-langchain4j:test :ccrs-a2a:test --rerun-tasks

    BUILD SUCCESSFUL
    ccrs-core tests: 80 passed
    ccrs-jacamo tests: 8 passed
    ccrs-hypermedea tests: 4 passed
    ccrs-langchain4j tests: 4 passed
    ccrs-a2a tests: 7 passed
    total: 103 passed

All five Javadoc tasks passed under `-Xdoclint:all,-missing` and `-Werror`.
All five Maven publications, including main, sources, Javadocs, POM, and Gradle
module metadata, were written to `build/local-maven-repo`. The expanded
standalone consumer then reported:

    Published module contracts verified
    - strategy providers: [ccrs.capabilities.a2a.A2aConsultationStrategyProvider, ccrs.capabilities.llm.langchain4j.Langchain4jPredictionStrategyProvider]
    - protocol binding: ccrs.hypermedea.CcrsHttpBinding
    - LangChain4j ChatModel API: compile and invocation passed
    CCRS suggestions
    - retry suggests retry target=https://example.org/api/orders confidence=0.80

The repository-local publications for all five modules also succeeded. Running the separate core consumer produced:

    CCRS suggestions
    - retry suggests retry target=https://example.org/api/orders confidence=0.80

WP3 published the aligned snapshots with:

    .\gradlew.bat publishCcrsSnapshotsToGitHubPackages

GitHub's authenticated package API confirmed the five repository-associated
package records and the `0.1.0-SNAPSHOT` version:

    io.github.stefanmhsg.ccrs.ccrs-core
    io.github.stefanmhsg.ccrs.ccrs-jacamo
    io.github.stefanmhsg.ccrs.ccrs-hypermedea
    io.github.stefanmhsg.ccrs.ccrs-langchain4j
    io.github.stefanmhsg.ccrs.ccrs-a2a

The clean remote fixture used a newly downloaded Gradle 9.2.0 distribution and
an otherwise empty Gradle user home. It reported:

    - sources: 5 resolved
    - javadocs: 5 resolved
    - poms: 5 resolved
    - Gradle module metadata: 5 resolved
    Published module contracts verified
    - strategy providers: [ccrs.capabilities.a2a.A2aConsultationStrategyProvider, ccrs.capabilities.llm.langchain4j.Langchain4jPredictionStrategyProvider]
    - protocol binding: ccrs.hypermedea.CcrsHttpBinding
    - LangChain4j ChatModel API: compile and invocation passed
    BUILD SUCCESSFUL

The manually dispatched
[Publish CCRS snapshots run 31264049379](https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31264049379)
then reproduced the complete path from a clean GitHub Actions checkout. The
build, 103 focused tests, five Javadoc tasks, and five-module publication passed
in the first job; the dependent fresh-Gradle-home consumer passed in the second
job.

Current coordinates:

    io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-jacamo:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-hypermedea:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-langchain4j:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-a2a:0.1.0-SNAPSHOT

Initial GitHub Packages endpoint:

    https://maven.pkg.github.com/stefanmhsg/ccrs-bdi

Documentation references for implementation details are GitHub's current [Working with the Gradle registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry) and [About permissions for GitHub Packages](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages) pages. This plan embeds the required operational facts so future work does not depend on those pages remaining unchanged; recheck them when implementing WP3 because authentication and workflow behavior can evolve.

## Interfaces and Dependencies

The published group remains:

    io.github.stefanmhsg.ccrs

All standalone builds must set `rootProject.name` equal to the artifact ID and initially use the same version. Dependent modules declare coordinates, not project paths:

    ccrs-jacamo       -> ccrs-core
    ccrs-hypermedea   -> ccrs-core, ccrs-jacamo
    ccrs-langchain4j  -> ccrs-core
    ccrs-a2a          -> ccrs-core

The stable optional capability extension interfaces remain:

- [CcrsStrategy.java](ccrs-core/src/main/java/ccrs/core/contingency/CcrsStrategy.java) for strategies.
- [CcrsStrategyProvider.java](ccrs-core/src/main/java/ccrs/core/contingency/CcrsStrategyProvider.java) for service-loaded strategy registration.
- [CcrsStrategyProviderContext.java](ccrs-core/src/main/java/ccrs/core/contingency/CcrsStrategyProviderContext.java) for configuration and class-loader context.
- [ContingencyCcrsFactory.java](ccrs-core/src/main/java/ccrs/core/contingency/ContingencyCcrsFactory.java) for core and discovered-provider assembly.
- [LlmClient.java](ccrs-core/src/main/java/ccrs/core/contingency/LlmClient.java) for provider-neutral completion.
- `ConsultationStrategy.ConsultationChannel` for consultation capability wiring during this plan; changing its shape is outside the physical-separation scope.
- [InteractionHistoryProvider.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/InteractionHistoryProvider.java) and [CcrsJacamoRuntime.java](ccrs-jacamo/src/main/java/ccrs/jacamo/CcrsJacamoRuntime.java) for replaceable JaCaMo interaction history.

The required service files remain:

    ccrs-langchain4j/src/main/resources/META-INF/services/ccrs.core.contingency.CcrsStrategyProvider
    ccrs-a2a/src/main/resources/META-INF/services/ccrs.core.contingency.CcrsStrategyProvider
    ccrs-hypermedea/src/main/resources/META-INF/services/org.hypermedea.op.ProtocolBinding

Every standalone module must publish a main jar, sources jar, Javadocs jar, POM, and Gradle module metadata. GitHub Packages credentials are deployment/consumption configuration and must not appear in any Java interface or published source.

Revision note (2026-08-08): Created this plan from the repository readiness assessment. It turns the identified build, metadata, testing, distribution, and consumer gaps into work packages; selects GitHub Packages; records that `strategies.internal` is conceptual rather than access control; keeps the current A2A behavior as a documented simplification; classifies `examples.asl` as application-owned; and makes BDI separation into a coordinate-only consumer an explicit acceptance condition.

Revision note (2026-08-08, WP1 completion): Completed the standalone contract and baseline package. The plan now records Java 21 across every later work package, moves WP1 out of the Now matrix, checks every WP1 todo, adds build/test/bytecode evidence, and aligns its progress, discoveries, decisions, outcome, and artifacts with the implementation and documentation changes.

Revision note (2026-08-08, WP2 completion): Corrected the published API/runtime scopes, replaced the Jena aggregate with explicit modules, added the missing Hypermedea/LangChain4j/A2A tests and fake seams, enforced the Javadoc warning baseline, expanded the coordinate-only consumer and repository requirements, recorded 103 passing tests and service-loader evidence, and moved WP2 out of the Now matrix.

Revision note (2026-08-08, WP3 implementation): Added the authenticated GitHub
Packages repository, snapshot-only aggregate task, manually dispatched Actions
workflow, exclusive local/remote consumer selection, and complete publication
artifact verification. Published and remotely consumed all five snapshots and
recorded the package evidence. WP3 remains Now only because the new workflow
cannot be dispatched until the currently uncommitted source changes are
committed and pushed; its first successful run URL is still required.

Revision note (2026-08-08, WP3 workflow context correction): Moved the clean
consumer's `GRADLE_USER_HOME` expression from job-level `env`, where the
`runner` context is unavailable, to the remote-consumer execution step, where
the assigned runner exposes `runner.temp`.

Revision note (2026-08-08, WP3 completion): Recorded the initial clean-checkout
failure caused by the ignored Gradle wrapper jar, the narrow wrapper recovery
commit, and successful workflow run 31264049379. Marked every WP3 todo complete,
moved WP3 out of the Now matrix, and made WP4 the sole active work package.
