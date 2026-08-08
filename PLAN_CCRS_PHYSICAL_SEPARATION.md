# PLAN_CCRS_PHYSICAL_SEPARATION: Publish and physically separate the CCRS libraries

This ExecPlan is a living document. The sections `Rules`, `Progress`, `Surprises & Discoveries`, and `Decision Log` must be kept up to date as work proceeds. Work packages must remain current with their local context, discussion, todos, concrete steps, validation, and outcomes.

No repository-local `PLANS.md` or `.agent/PLANS.md` guide is checked in. This plan follows the repository's `PLAN_<SCOPE>.md` convention and the execution-plan guidance in [AGENTS.md](AGENTS.md). It uses [CCRS_LIBRARY.md](CCRS_LIBRARY.md) as the durable architecture note; this file owns the implementation sequence and validation evidence for physical library separation.

## Purpose / Big Picture

The repository began with five CCRS Gradle subprojects whose identity, repositories, and publication configuration came from the JaCaMo application root. WP4 replaces that topology with five independently buildable and publishable libraries, distributed through GitHub Packages, while the BDI application consumes the same Maven coordinates as an external consumer.

After this plan is complete, a developer can enter any CCRS module directory, build and test it without relying on the application root, and publish its artifacts with the same coordinates used by external consumers. A clean checkout of the separated BDI application can authenticate to GitHub Packages, resolve the selected CCRS modules, compile, and run without any sibling CCRS source directories or Maven Local artifacts. A composite workspace may still substitute local source during development, but that workspace is convenience glue rather than a hidden build requirement.

The initial snapshots were hosted under `stefanmhsg/ccrs-bdi`. WP6 moves the
canonical research artifact and its GitHub Packages endpoint to
`stefanmhsg/ccrs`, while the existing `stefanmhsg/ccrs-bdi` repository becomes
the application-only consumer. Maven group and artifact coordinates remain
unchanged.

## Rules

- Rule: Preserve the dependency direction `core <- jacamo <- hypermedea`, `core <- langchain4j`, and `core <- a2a`.
  Reason: This is the established architecture in [CCRS_LIBRARY.md](CCRS_LIBRARY.md), and reversing an edge would reintroduce adapter or provider coupling into core.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Define “standalone module” as an independently buildable source build and a consumable published artifact; do not require one Git repository per module.
  Reason: Independent builds and coordinate-based consumption prove the technical boundary. Keeping the builds co-located initially preserves history without requiring a mixed legacy/standalone production topology.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Build every Java project with a Java 21 toolchain and `--release 21`.
  Reason: Java 21 is the supported source, bytecode, test, publication, and consumer baseline for the separation work; every independent build must preserve it explicitly.
  Added/Updated: 2026-08-08 / Codex

- Rule: Publish CCRS Maven artifacts to GitHub Packages under `stefanmhsg/ccrs`; use only an explicitly selected isolated staging repository for local graph verification.
  Reason: GitHub Packages is the selected distribution target. An explicit staging URL supports fast credential-free smoke tests without making Maven Local or a repository-relative path a hidden fallback.
  Added/Updated: 2026-08-08 / User direction and Codex; updated for WP6

- Rule: Use `ccrs` as the canonical research-artifact repository and retain
  `ccrs-bdi` as the concrete JaCaMo/BDI application repository.
  Reason: The short conceptual name is more suitable for promoting and citing
  Course Check and Revision Strategies, while the application keeps its
  established descriptive name and history.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Never commit GitHub usernames, personal access tokens, `GITHUB_TOKEN` values, API keys, or generated credential files.
  Reason: Local consumers need authenticated GitHub Packages access, and workflows receive credentials at runtime.
  Added/Updated: 2026-08-08 / Codex

- Rule: Treat `ccrs.core.contingency.strategies.internal` as a conceptual CCRS strategy grouping, not a Java visibility declaration.
  Reason: Public classes in that package are intentionally usable by library consumers. Physical separation must not make them package-private or hide them merely because the path contains `internal`.
  Added/Updated: 2026-08-08 / User direction

- Rule: Keep `src/agt/examples/contingency/examples.asl` application-owned example material, not part of the supported library runtime contract.
  Reason: AgentSpeak examples belong with the BDI application or an explicit example project. The reusable JaCaMo adapter should not imply that the example agent program is library API.
  Added/Updated: 2026-08-08 / User direction

- Rule: Retain the current A2A-shaped target discovery and request/response projection in the generic consultation path as a documented simplification during this migration.
  Reason: Separating that policy is not required for physical packaging and would expand this work into a semantic redesign. Documentation and tests must make the limitation visible.
  Added/Updated: 2026-08-08 / User direction

- Rule: Execute each structural work package as a cutover, not as a compatibility phase. WP4 is executed directly in the current worktree at the user's request; the completed work package removes the corresponding root-subproject inclusion, inherited build configuration, and `project(...)` dependency in the same change that introduces the standalone replacement.
  Reason: The supported tree should have exactly one build and dependency path for each migrated module. The project must not carry a legacy and standalone implementation in parallel.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Permit coordinate substitution only through the optional composite workspace; never encode local filesystem fallbacks or Maven Local fallback resolution in a standalone module or application dependency declaration.
  Reason: Composite substitution preserves coordinate-shaped dependencies and is development orchestration, not a second legacy architecture.
  Added/Updated: 2026-08-08 / User direction and Codex

- Rule: Update [CCRS_LIBRARY.md](CCRS_LIBRARY.md), module README files, consumer examples, service-loader documentation, and this plan in the same change as any boundary or package move.
  Reason: Consumers rely on documentation for coordinates, repository authentication, optional capability behavior, and application integration.
  Added/Updated: 2026-08-08 / Existing repository guidance and Codex

- Rule: Validate published metadata and clean consumers, not only project-to-project compilation.
  Reason: Gradle project dependencies can hide incorrect Maven scopes, missing resources, or dependencies that are available only through the root build.
  Added/Updated: 2026-08-08 / Codex

- Rule: Preserve full LLM request and response logging for developer-operated
  multi-agent systems; improve correlation rather than redacting or removing
  those payloads.
  Reason: Shared diagnostic visibility is intentional for this research
  artifact. The separate-JVM rule remains the boundary for mutually untrusted
  tenants.
  Added/Updated: 2026-08-08 / User direction

- Rule: Keep Hypermedea pinned to `0.4.2` during WP7 and treat newer versions
  as source-review subjects, not automatic upgrades.
  Reason: `0.4.2` is the known working application version, and Hypermedea
  `0.5` retains the same process-wide cached representation-handler
  `ServiceLoader`, so upgrading would not remove the concurrency workaround.
  Added/Updated: 2026-08-08 / User direction and source comparison

- Rule: Treat capability-specific caches, credentials, quotas, and client
  concurrency as adapter contracts unless core itself stores or routes that
  state.
  Reason: Core invokes `LlmClient` and `ConsultationChannel` abstractions; the
  LangChain4j and A2A modules own their concrete network clients and caches.
  Core must document the concurrent-call contract but must not silently impose
  one provider's serialization or cache policy on every capability.
  Added/Updated: 2026-08-08 / User direction and Codex

## Now / Next / Later

| NOW | NEXT | LATER |
| --- | --- | --- |
| WP6: Extract the BDI application as a package consumer | WP7: Harden same-JVM multi-agent lifecycle and capability concurrency | WP8: Harden releases and consider repository extraction |
|  |  | WP9: Migrate additional consumers such as `ccrs-react` from Maven Local |

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
- [x] (2026-08-08) Implemented the WP4 atomic cutover directly in the current worktree: all five modules now own complete builds and wrappers, all CCRS edges use Maven coordinates, and the root is an application-only coordinate consumer.
- [x] (2026-08-08) Published all five standalone builds in dependency order to `.gradle/wp4-maven-repo`, built the root application against only that repository, and ran the independent consumer successfully with all artifact and service-loader checks.
- [x] (2026-08-08 17:05Z) Completed WP4 as one all-module cutover at commit `a8cde92b`: [Publish CCRS snapshots run 31268336198](https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31268336198) built, staged, tested, documented, and published all five standalone modules, then passed the fresh remote consumer.
- [x] (2026-08-08 17:31Z) Completed WP5: added the wrapper-owned `ccrs-workspace` composite, verified all five local builds, proved JaCaMo selects local core only inside the composite, and ran JaCaMo plus the full consumer against isolated staged artifacts outside it.
- [x] (2026-08-08 17:58Z) Started WP6 from clean pushed baseline `fbfb48d`: created a full-history sibling `ccrs` clone, removed application-owned paths from its HEAD, moved every active package URL to `stefanmhsg/ccrs`, added an independent consumer wrapper, passed the forced composite build, staged all five standalone publications, and passed the artifact-only consumer.
- [x] (2026-08-08 18:15Z) Established `stefanmhsg/ccrs` as the package-owning research repository at commit `86e6c97`; snapshot run [31271103838](https://github.com/stefanmhsg/ccrs/actions/runs/31271103838) published all five modules and passed the fresh remote consumer.
- [x] (2026-08-08 18:20Z) Created the new application-only `stefanmhsg/ccrs-bdi` repository at commit `b144f4c`; consumer run [31271634626](https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31271634626) passed the `none`, `hypermedea`, `langchain4j`, `a2a`, and `all` package profiles.
- [x] (2026-08-08) Audited process-wide and per-agent mutable state after the
  Hypermedea 0.4.2 concurrent `ServiceLoader` failure. Fixed per-agent history
  races, cross-agent diagnostic disclosure, duplicate JaCaMo runtime
  initialization, mid-evaluation configuration changes, and unsafe registry
  snapshots; added 8 regression tests.
- [x] (2026-08-08) Rebuilt all five standalone modules and the composite
  workspace without launching agents. All 113 tests and all Javadocs passed;
  dependency insight confirmed `org.hypermedea:hypermedea:0.4.2` remains the
  selected runtime version.
- [ ] Delete the redundant `stefanmhsg/ccrs-extraction-staging` repository. Its HEAD was verified as `86e6c97`, identical to the authoritative `ccrs` repository and local clone, but the current GitHub CLI token lacks the required `delete_repo` scope.
- [ ] Complete WP6 after the verified staging repository is removed.
- [x] (2026-08-08) Audited the remaining same-JVM two-agent behavior and
  defined WP7 as one bounded package covering opportunistic-belief lifecycle,
  LLM concurrency, runtime reconfiguration, A2A cache scope, Hypermedea 0.5
  comparison, and agent-identity documentation.
- [ ] Complete WP7 before treating same-JVM multi-agent behavior as a stable
  public contract.
- [ ] Complete WP8 release gates before publishing the first non-snapshot version.

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

- Observation: A staging repository below the root `build` directory is not safe for the final application check.
  Evidence: The first coordinate-only root `clean classes` removed `build/wp4-maven-repo` before dependency resolution. Republishing to the ignored `.gradle/wp4-maven-repo` kept staging independent of root build output and the application plus consumer then passed.

- Observation: The completed WP4 workflow is functionally green but its v4 setup actions now emit maintenance warnings.
  Evidence: Run 31268336198 reports that Node.js 20 actions are being forced onto Node.js 24 and that `actions/setup-java@v4` is deprecated in favor of v5. This does not affect WP4 acceptance, but the workflow action versions should be refreshed during release hardening.

- Observation: Explicit composite substitution and direct artifact resolution preserve the same coordinate declaration while selecting different component forms only at invocation time.
  Evidence: `ccrs-workspace :ccrs-jacamo:dependencyInsight` reported `io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT -> project :ccrs-core (by composite build)`. Direct `ccrs-jacamo dependencyInsight` against `.gradle/wp5-maven-repo` reported the timestamped Maven snapshot and no project component; both builds passed.

- Observation: GitHub Maven/Gradle packages are repository-scoped, so pushing the existing coordinates from a separately created `ccrs` repository did not transfer package ownership.
  Evidence: Publication run [31270920866](https://github.com/stefanmhsg/ccrs/actions/runs/31270920866) reached the registry but GitHub rejected `ccrs-core` with HTTP 422. GitHub's package-permissions documentation states that Maven and Gradle packages support only repository-scoped permissions and that associated packages move when their repository is transferred. Renaming the original package-owning repository from `ccrs-bdi` to `ccrs` preserved the package association; run 31271103838 then published successfully.

- Observation: A newly created repository may index a workflow only after a repository-native follow-up push, while also scheduling the initial push retroactively.
  Evidence: `ccrs-bdi` initially returned no workflows or runs. Commit `b144f4c` added a documentation-only workflow comment; GitHub then ran both `55db4d2` and `b144f4c`, and both package-consumer matrices passed.

- Observation: The application repository's read-only `GITHUB_TOKEN` can consume the public packages associated with the sibling `ccrs` repository under the same owner.
  Evidence: Run 31271634626 used only `contents: read`, `packages: read`, `GITHUB_ACTOR`, and `GITHUB_TOKEN`; all five fresh-Gradle-home capability profiles resolved and built successfully without a repository secret.

- Observation: Hypermedea 0.4.2's cached representation-handler
  `ServiceLoader` is not safe for concurrent iteration, and Java's failure mode
  can surface as `ServiceLoader.nextService` attempting to throw a null error.
  Evidence: Direct single-threaded Turtle deserialization passed, while the
  application log failed inside `RepresentationHandlers.loadFromContentType`;
  a serialized payload wrapper now passes 12-caller concurrent regression
  coverage without upgrading Hypermedea.

- Observation: Concurrent maps do not make mutable values thread-safe, and
  synchronizing a method on an instance does not protect a static field across
  different instances.
  Evidence: `JasonInteractionLog` stored unsynchronized `ArrayDeque` values
  read outside its writer lock, while JaCaMo `evaluate` guarded a static CCRS
  cache with an instance monitor. Per-agent synchronized histories and a
  static initialization monitor now pass concurrent reader/writer and
  24-caller initialization tests.

- Observation: The two opportunistic JaCaMo integration paths do not implement
  the same derived-belief lifecycle.
  Evidence: [CcrsAgentArch.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jaca/CcrsAgentArch.java)
  deletes every `ccrs/3` belief carrying `origin(opportunistic-ccrs)` before a
  flush and recreates only sources present in that flush. In contrast,
  [CcrsAgent.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jason/opportunistic/CcrsAgent.java)
  supplies only `source` metadata, so its derived beliefs do not carry that
  origin and are not removed when their source percept disappears.

- Observation: The default LangChain4j provider does not serialize concurrent
  agent requests inside CCRS or LangChain4j.
  Evidence: [Langchain4jLlmClient.java](ccrs-langchain4j/src/main/java/ccrs/capabilities/llm/langchain4j/Langchain4jLlmClient.java)
  calls `chatModel.chat(prompt)` without a lock. LangChain4j 1.10.0's
  `OpenAiChatModel.doChat` builds request and response data in local variables,
  and its default JDK HTTP adapter calls the shared immutable
  `java.net.http.HttpClient` once per caller. A provider quota, connection
  limit, or custom `ChatModel` may still queue calls outside CCRS.

- Observation: The A2A card-object cache currently holds one map monitor while
  resolving a card over the network.
  Evidence: [A2aConsultationChannel.java](ccrs-a2a/src/main/java/ccrs/capabilities/a2a/A2aConsultationChannel.java)
  invokes `resolveCardFromUri(...)` inside `synchronized (cachedCards)`. Agent
  A can therefore delay Agent B's first card resolution even when they use
  different card URIs. The response accumulators and clients themselves are
  per call, so this is adapter-owned head-of-line blocking rather than response
  leakage or a core consultation defect.

- Observation: Hypermedea `0.5` does not remove the representation-handler
  concurrency defect covered by the CCRS wrapper.
  Evidence: Upstream
  [v0.4.2 `RepresentationHandlers.java`](https://github.com/Hypermedea/hypermedea/blob/v0.4.2/hypermedea-lib/src/main/java/org/hypermedea/ct/RepresentationHandlers.java)
  and
  [v0.5 `RepresentationHandlers.java`](https://github.com/Hypermedea/hypermedea/blob/v0.5/hypermedea-lib/src/main/java/org/hypermedea/ct/RepresentationHandlers.java)
  both declare one static `ServiceLoader<RepresentationHandler>` and iterate it
  directly. The current global payload-conversion monitor remains necessary;
  only conversion is serialized, not the preceding HTTP request.

- Observation: JaCaMo runtime configuration is safely published but becomes
  sticky after the first contingency evaluation.
  Evidence: [evaluate.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jason/contingency/evaluate.java)
  caches the first `ContingencyCcrs` in its own static field, while
  [CcrsJacamoRuntime.java](ccrs-jacamo/src/main/java/ccrs/jacamo/CcrsJacamoRuntime.java)
  can subsequently replace the configuration or supplier without invalidating
  that evaluator. Centralized cache ownership and invalidation can correct this
  with a small, deterministic change.

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

- Decision: Resolve CCRS from GitHub Packages by default and allow one explicit
  `-PccrsRepositoryUrl=<url>` override for isolated staging; make the selected
  repository exclusive to the `io.github.stefanmhsg.ccrs` group.
  Rationale: A clean remote check must not fall back to Maven Local or a
  repository-relative directory, while specialized third-party repositories
  must not answer for CCRS coordinates.
  Date/Author: 2026-08-08 / Codex

- Decision: Publish snapshots only from a manually dispatched workflow and
  enforce the `-SNAPSHOT` suffix in Gradle before every GitHub Packages upload.
  Rationale: Pull requests and ordinary branch builds must remain read-only;
  stable versions require the separate tag-driven release gates in WP8.
  Date/Author: 2026-08-08 / Codex

- Decision: Perform the complete library build-topology change in one WP4 cutover instead of a dual or mixed legacy/standalone migration phase.
  Rationale: Per the user's execution direction, WP4 is implemented directly in the current worktree without creating a branch or worktree. Its accepted result moves all five modules, all consumers, and publication together and removes every old root-subproject and `project(...)` path. Rollback comes from Git history, not from maintaining two supported configurations.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Give each standalone module its own checked-in Gradle 9.2.0 wrapper and make the workflow set Unix execute permission before invocation.
  Rationale: A module must be buildable without the root wrapper. The repository is maintained from Windows, so the workflow's explicit `chmod +x` makes clean Linux runner behavior deterministic while every wrapper jar is re-included through `.gitignore`.
  Date/Author: 2026-08-08 / Codex

- Decision: Perform WP6 by preparing both role-specific HEADs from full history,
  renaming the original package-owning repository from `ccrs-bdi` to `ccrs`,
  and creating a new application-only repository at the released `ccrs-bdi`
  name.
  Rationale: GitHub Maven/Gradle packages are repository-scoped. Renaming the
  original repository carries the five established package associations to the
  user-selected research-artifact name; creating the application repository
  afterward restores its established name without preserving a dual source
  topology. Both repositories retain the relevant common history.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Make `ccrs-workspace` a sixth, non-publishing Gradle build with explicit substitutions for all five library coordinates and delegate aggregate tasks to included-build lifecycle tasks.
  Rationale: Explicit mappings make source selection reviewable, while delegation leaves dependency, repository, Java, test, and publication logic exclusively in each standalone module. The existing workflow can compare this source path with isolated staged and fresh remote artifact paths.
  Date/Author: 2026-08-08 / Codex

- Decision: Support concurrent agents inside one trusted application JVM, but
  do not present agent-name partitioning or process-global configuration as a
  multi-tenant security boundary.
  Rationale: Histories, registry snapshots, evaluation configuration, and
  runtime initialization can be made deterministic and thread-safe. Agent
  names are still logical routing values rather than authenticated identities,
  and `CcrsJacamoRuntime`, `CcrsGlobalRegistry`, and dotenv fallback suppliers
  remain application-scoped. Mutually untrusted tenants therefore require
  separate JVMs, credentials, and logs.
  Date/Author: 2026-08-08 / Codex

- Decision: Address the remaining same-JVM findings in one WP7 rather than
  distributing them across capability and release packages.
  Rationale: The observable contract is one question for consumers: whether
  Agent A and Agent B can share a trusted JVM without state leakage or
  accidental library-wide blocking. One package can test that boundary while
  still assigning each fix and document to its owning module.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Keep full LLM payload logs and prove concurrent default-provider
  calls instead of adding a CCRS-wide LLM mutex.
  Rationale: Full context is intentionally useful to developers. Current CCRS,
  LangChain4j 1.10.0, and the default JDK HTTP client do not require global
  serialization; adding it would create the exact Agent-A-blocks-Agent-B
  behavior under investigation. Custom `LlmClient`, `ChatModel`, prompt
  builder, and parser implementations must satisfy the documented
  concurrent-call contract or perform their own narrow synchronization.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Make opportunistic derived beliefs source-scoped materialized
  views and give both producer paths explicit lifecycle metadata.
  Rationale: A refresh of source S1 must not erase still-valid beliefs derived
  from source S2, and removal of a single percept must remove exactly the
  beliefs derived from that percept. `origin`, `source`, and producer/evidence
  identity provide deterministic replacement without touching persistent
  `origin(contingency-ccrs)` notes.
  Date/Author: 2026-08-08 / Codex

- Decision: Fix the A2A cache's broad network lock in `ccrs-a2a`, but keep card
  visibility and freshness policy capability-owned.
  Rationale: Moving network resolution outside the shared map monitor is a
  small concurrency fix. Whether an agent card may vary by credential, tenant,
  or time depends on the supplied A2A capability, not generic CCRS core. The
  default shared channel will explicitly require stable, caller-independent
  card metadata; personalized discovery requires a separately scoped channel
  or process.
  Date/Author: 2026-08-08 / User direction and Codex

- Decision: Centralize JaCaMo evaluator cache ownership in
  `CcrsJacamoRuntime` and invalidate it on runtime configuration changes.
  Rationale: This is the smallest non-sticky design. An evaluation already in
  progress retains its captured evaluator, while the next evaluation after a
  setter or reset deterministically receives a newly constructed evaluator.
  Date/Author: 2026-08-08 / User direction and Codex

## Context and Orientation

At baseline commit `fbfb48d`, `ccrs-bdi` still had two roles: its root was a JaCaMo/Jason application and five `ccrs-*` directories were standalone reusable Java library builds. WP6 creates this `ccrs` repository from that complete history and removes the application-owned paths from its current tree. The original `ccrs-bdi` repository retains the root application and removes the library-owned paths during the coordinated cutover. [AGENTS.md](AGENTS.md) and [CCRS_LIBRARY.md](CCRS_LIBRARY.md) define the resulting library boundaries.

`ccrs-core` contains agent-agnostic RDF, opportunistic CCRS, contingency CCRS, strategy configuration, and provider extension points. `ccrs-jacamo` adapts core to Jason, JaCaMo, and CArtAgO. `ccrs-hypermedea` adds a Hypermedea HTTP artifact and history implementation and depends on both core and JaCaMo. `ccrs-langchain4j` provides a LangChain4j-backed `LlmClient` and strategy provider. `ccrs-a2a` provides an A2A-backed consultation channel and strategy provider.

The `ccrs` repository has no root application Gradle build. Each library owns its settings, wrapper, repositories, artifact identity, Java 21 configuration, tests, documentation artifacts, and publication. The separate [ccrs-bdi application](https://github.com/stefanmhsg/ccrs-bdi) owns its root settings and build and declares selected CCRS packages by aligned coordinate.

A standalone build has its own `settings.gradle`, wrapper, complete build configuration, artifact identity, dependency repositories, tests, and publication definition. Its dependencies on other CCRS modules are Maven coordinates, for example `io.github.stefanmhsg.ccrs:ccrs-core:<version>`, rather than Gradle project paths. It must build when its sibling source directories are unavailable.

WP4 performed one all-library cutover: all five modules left the root subproject build together, all inter-module and application dependencies became coordinates, and all library publication moved to the standalone builds. It was performed directly in the current worktree. An explicitly selected staging repository and CI fixture proved the coordinate graph without creating an intermediate compatibility state.

A composite build is an optional Gradle workspace that includes several complete builds and substitutes matching Maven coordinates with local projects. It preserves fast cross-module development without making any module depend on the workspace. The authoritative proof remains running each build alone and resolving its published form in a clean consumer.

GitHub Packages exposes a Maven-compatible Gradle registry. The current repository URL is:

    https://maven.pkg.github.com/stefanmhsg/ccrs

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
- [x] Mark `examples.asl` for relocation to the application-owned tree during WP4.
- [x] Define a standalone module as passing its own `build`, `test`, `publishToMavenLocal`, and clean coordinate-consumer checks with sibling sources absent.

Concrete steps: From `S:\dev\ma\ccrs-bdi`, review the dependency graph and baseline again:

    .\gradlew.bat classes :ccrs-core:test :ccrs-jacamo:test

Record the Java and Gradle versions:

    .\gradlew.bat --version

Update the documentation named above without moving Java source. Add the exact chosen Java baseline to every later work package.

Validation and acceptance: The existing build remains green. A reader can determine from this plan and [CCRS_LIBRARY.md](CCRS_LIBRARY.md) which files belong to libraries versus the application and can explain why no A2A policy or strategy visibility refactor is part of this migration.

Outcome and notes: Completed on 2026-08-08. [CCRS_LIBRARY.md](CCRS_LIBRARY.md) now defines the standalone source-build and clean-consumer contract and links this plan. The root build configures a Java 21 toolchain and `--release 21` for the application and every `ccrs-*` project. The contingency documentation explains that `strategies.internal` is conceptual, not a visibility boundary. The core social-strategy and A2A README files retain the current A2A/RDF discovery and projection rules as accepted, characterized simplifications. `examples.asl` is explicitly application-owned and scheduled to move during WP4. Gradle 9.2.0 on Java 21 rebuilt the project and reran all 88 focused tests successfully; generated core bytecode has class-file major version 65.

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

Local context: At the start of WP3, Maven publications were created in the root [build.gradle](build.gradle). GitHub Packages uses repository-scoped Maven URLs and authentication for reads and writes. The remote is `https://github.com/stefanmhsg/ccrs-bdi.git`. Publication and clean-consumer workflows provision Java 21 explicitly.

Discussion: Keep local publication tasks alongside GitHub publication. Publishing is an external state change and must occur only from an explicitly invoked release workflow or an authorized local command. Normal pull-request CI builds and tests but does not publish. Snapshot publication may use a manual workflow or a protected branch event; non-snapshot publication must be tag-driven in WP8.

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
1m00s. WP3 was complete at that point, making WP4 the next active work package.

### WP4: Cut all CCRS modules over to standalone builds

Status: Done

Purpose: Replace the five-module root-subproject build with five authoritative standalone Gradle builds in one cutover. After WP4, every `ccrs-*` directory builds and publishes independently, the BDI application consumes all five libraries by coordinate, and the repository contains no supported legacy `project(...)` path.

Local context: `ccrs-core` has no CCRS dependency. `ccrs-jacamo`, `ccrs-langchain4j`, and `ccrs-a2a` depend on the core coordinate; `ccrs-hypermedea` depends on the core and JaCaMo coordinates. All five now own group, version, repositories, publication, sources jars, Javadocs jars, Java 21, `--release 21`, settings, and wrappers. The root application has no included library projects and resolves all five coordinates. The rewritten snapshot workflow invokes each owning wrapper in dependency order.

Discussion: Implement this directly in the current worktree as one atomic cutover, not as separate production phases for core, optional providers, and Hypermedea. Every module has a complete `settings.gradle`, `build.gradle`, wrapper, repository configuration, publication, and test setup. Every inter-module edge uses the aligned Maven coordinate. The root is an application-only build: it no longer includes, configures, or publishes library projects and consumes all five coordinates. The workflow builds in dependency order and publishes through each module's own wrapper. A temporary, explicitly selected staging Maven repository validates the unpublished coordinate graph before remote publication; it is not a default Maven Local or filesystem fallback. Git history is the rollback mechanism after the change is committed.

The dependency-order implementation sequence inside the worktree is core first; JaCaMo, LangChain4j, and A2A second; Hypermedea third; and the root application plus clean consumer last. This is validation sequencing within one uncommitted change, not a supported mixed topology. Keep `CcrsAgent` and `CcrsAgentArch` together, retain the dependency scopes established by WP2, and preserve the accepted A2A characterization without semantic redesign.

Todos:

- [x] Give each of the five `ccrs-*` directories its own `settings.gradle`, Gradle wrapper, artifact identity, Java 21 toolchain, `--release 21`, repositories, tests, Javadocs, sources jar, POM metadata, local verification publication, and GitHub Packages publication.
- [x] Set every standalone `rootProject.name` to its artifact ID and keep the aligned `io.github.stefanmhsg.ccrs:ccrs-*:0.1.0-SNAPSHOT` coordinates.
- [x] Replace all four inter-module `project(...)` dependencies with coordinates while preserving the graph `core <- jacamo <- hypermedea`, `core <- langchain4j`, and `core <- a2a`.
- [x] Carry forward the API/runtime scopes and repository requirements proven by WP2, including JaCaMo, Hypermedea, LangChain4j, A2A, and Jena metadata.
- [x] Keep both optional provider service descriptors and the Hypermedea protocol-binding descriptor packaged exactly once, with controlled behavior when optional secrets or endpoints are absent.
- [x] Keep `CcrsAgent` and `CcrsAgentArch` together.
- [x] Move [examples.asl](src/agt/examples/contingency/examples.asl) to an application-owned example location and update links; do not retain a second library copy.
- [x] Replace all five root-application project dependencies with GitHub Packages coordinates and add authenticated package resolution without tracked credentials.
- [x] Remove all five CCRS `include` declarations, the root `ccrsLibraryProjects` configuration, root-owned library publications, and the aggregate root publication task in the same cutover.
- [x] Rewrite [publish-ccrs-snapshots.yml](.github/workflows/publish-ccrs-snapshots.yml) to invoke the five independent wrappers in dependency order, run all tests and Javadocs, publish all five snapshots, and then run the fresh authenticated remote consumer.
- [x] Update [CCRS_LIBRARY.md](CCRS_LIBRARY.md), the core [opportunistic README.md](ccrs-core/src/main/java/ccrs/core/opportunistic/README.md), [contingency README.md](ccrs-core/src/main/java/ccrs/core/contingency/README.md), and [RDF README.md](ccrs-core/src/main/java/ccrs/core/rdf/README.md), plus [ccrs-jacamo README.md](ccrs-jacamo/README.md), [ccrs-hypermedea README.md](ccrs-hypermedea/README.md), [ccrs-langchain4j README.md](ccrs-langchain4j/README.md), [ccrs-a2a README.md](ccrs-a2a/README.md), consumer instructions, and IDE/import guidance for the standalone layout.
- [x] Track every nested `gradle-wrapper.jar` despite the repository-wide jar ignore rule and make the Linux workflow set every Unix wrapper executable before use.
- [x] Prove that the cutover candidate contains no active CCRS subproject include, `project(...)` dependency, alternate legacy settings file, Maven Local resolution fallback, or duplicate publication path.
- [x] Commit and push the atomic cutover, dispatch the rewritten workflow, and record one successful five-wrapper publication plus fresh remote-consumer run.

Concrete steps: Work directly in the current worktree as requested. Build and publish core to an explicitly selected isolated staging repository under `.gradle`, then build and publish the direct dependents against the same coordinate repository, followed by Hypermedea. The standalone command shape from each module directory is:

    cd S:\dev\ma\ccrs-bdi\ccrs-core
    .\gradlew.bat -PccrsRepositoryUrl=S:/dev/ma/ccrs-bdi/.gradle/wp4-maven-repo clean build publishMavenJavaPublicationToCcrsStagingRepository

    cd S:\dev\ma\ccrs-bdi\ccrs-jacamo
    .\gradlew.bat -PccrsRepositoryUrl=S:/dev/ma/ccrs-bdi/.gradle/wp4-maven-repo --refresh-dependencies clean build publishMavenJavaPublicationToCcrsStagingRepository

    cd S:\dev\ma\ccrs-bdi\ccrs-langchain4j
    .\gradlew.bat -PccrsRepositoryUrl=S:/dev/ma/ccrs-bdi/.gradle/wp4-maven-repo --refresh-dependencies clean build publishMavenJavaPublicationToCcrsStagingRepository

    cd S:\dev\ma\ccrs-bdi\ccrs-a2a
    .\gradlew.bat -PccrsRepositoryUrl=S:/dev/ma/ccrs-bdi/.gradle/wp4-maven-repo --refresh-dependencies clean build publishMavenJavaPublicationToCcrsStagingRepository

    cd S:\dev\ma\ccrs-bdi\ccrs-hypermedea
    .\gradlew.bat -PccrsRepositoryUrl=S:/dev/ma/ccrs-bdi/.gradle/wp4-maven-repo --refresh-dependencies clean build publishMavenJavaPublicationToCcrsStagingRepository

The isolated staging publication verifies each module's standard Maven publication without touching the developer's Maven Local cache. From `S:\dev\ma\ccrs-bdi`, compile the root application against the same explicit coordinate repository without running JaCaMo agents, and run the clean consumer with a fresh Gradle user home.

Before commit, search the complete candidate:

    rg -n "project\(" -g "build.gradle" .
    rg -n "include.*ccrs-|ccrsLibraryProjects|publishCcrsSnapshotsToGitHubPackages" settings.gradle build.gradle .github/workflows

The search must return no active legacy declarations. References in historical documentation are acceptable only when clearly labeled as pre-cutover evidence. Do not retain a property or alternate settings file that can re-enable the old subproject path.

Validation and acceptance: All five standalone `build` and isolated staging publication commands pass from their own directories with no root build inheritance. Generated POM and Gradle metadata preserve the WP2 dependency scopes. The root application compiles using only coordinates. The rewritten manual workflow builds, tests, documents, and publishes all five modules through their own wrappers, then its clean remote consumer resolves the new snapshots and verifies both strategy providers, the Hypermedea binding, and the public LangChain4j API. Removing any sibling module directory does not prevent a module from resolving its declared published dependencies. The root contains no CCRS subproject include, `project(...)` dependency, inherited library configuration, fallback switch, or duplicate publication path.

Outcome and notes: Completed on 2026-08-08. The five library directories now each contain authoritative settings, build logic, Gradle 9.2.0 wrappers, sources, resources, and tests. The root includes no library projects and owns no library publication; its five dependencies are Maven coordinates. The workflow uses each module wrapper in dependency order and applies `chmod +x` in the Linux checkout. Local staging under `.gradle/wp4-maven-repo` produced all five publications; the root app and consumer resolved only those publications, all five sources/Javadocs/POM/module-metadata files were found, both strategy providers and the Hypermedea binding loaded, and the public LangChain4j API passed. At commit `a8cde92b`, [run 31268336198](https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31268336198) repeated the complete path from a clean checkout: build/stage/publication passed in 4m18s and the fresh authenticated remote consumer passed in 1m06s. WP4 is complete.

### WP5: Add a composite workspace for local multi-module development

Status: Done

Purpose: Restore the convenience of one-command cross-module development through a dedicated composite workspace without weakening standalone boundaries or changing the still-present root BDI application.

Local context: By this point each `ccrs-*` directory is a complete Java 21 Gradle build, declares other CCRS modules by coordinate, and has already been removed from the legacy root-subproject topology. Gradle composite builds can substitute those coordinates with local included builds when group and project name match; substitution must not change the Java 21 toolchain or release target.

Discussion: Create the composite as a dedicated workspace build such as `ccrs-workspace/`, not as another mode inside the root BDI application build. The composite owns no production code and publishes nothing. It may orchestrate build/test tasks, but no included build may depend on configuration inherited from it. Explicit dependency substitution may be used if publication coordinates differ from `group:rootProject.name`. This is not a legacy compatibility mode: all production dependency declarations remain Maven coordinates, and deleting the workspace build still leaves every module and application build valid.

Todos:

- [x] Add a dedicated `ccrs-workspace` aggregator and wrapper that uses `includeBuild` for every independent CCRS build; do not preserve or reintroduce any `include`-based CCRS subprojects.
- [x] Add explicit substitutions for all five coordinates.
- [x] Add aggregate verification tasks without duplicating module build logic.
- [x] Document how to build one module in isolation, all libraries together, and a consumer with local substitutions.
- [x] Add a CI comparison that validates both composite-source and published-artifact paths.
- [x] Prove the aggregator contains no module build logic, publication, repository fallback, or `project(...)` dependency.

Concrete steps: From `S:\dev\ma\ccrs-bdi\ccrs-workspace`, run its wrapper and composite aggregate verification task, then run each module build directly. Also run the published consumer outside the composite and confirm it resolves the published artifact rather than local source.

Validation and acceptance: A source edit in core is visible to a locally included JaCaMo build without publishing. The same JaCaMo build succeeds alone by resolving the configured core coordinate. Published and substituted dependency graphs remain behaviorally equivalent.

Outcome and notes: Completed on 2026-08-08. [ccrs-workspace](ccrs-workspace/README.md) is a Gradle 9.2.0 composite containing only settings, aggregate lifecycle tasks, documentation, and its wrapper. Its settings explicitly substitute all five `io.github.stefanmhsg.ccrs:<module>` coordinates with the root project of the matching included build. `verifyAll` delegates to every module's `build`; `testAll` delegates to every module's `test`. The snapshot workflow now runs `verifyAll` before its existing isolated-staging, publication, and fresh-remote-consumer checks. Local `verifyAll` passed all five builds. Dependency insight proved JaCaMo selected `project :ccrs-core` inside the composite and the timestamped staged Maven artifact outside it. A full artifact-only consumer run then resolved all five documentation and metadata artifact sets, loaded both strategy providers plus Hypermedea, compiled the public LangChain4j API, and printed the expected retry suggestion. No workspace file declares a repository, dependency, Java configuration, production source, Maven publication, Maven Local fallback, or Gradle `project(...)` dependency.

### WP6: Split the CCRS research artifact from the BDI application

Status: Now

Purpose: Establish `stefanmhsg/ccrs` as the authoritative library and research-artifact repository and reduce the existing `stefanmhsg/ccrs-bdi` repository to the authoritative JaCaMo application. The application must resolve CCRS only from the new GitHub Packages endpoint.

Local context: Baseline commit `fbfb48d` contains both roles with standalone coordinate dependencies. Library-owned paths are the five `ccrs-*` module builds, `ccrs-workspace`, the artifact consumer, publication workflow, and library architecture documents. Application-owned paths are the root Gradle build, `.jcm` files, `src/agt`, `src/env`, `src/org`, `.env.example`, logging, experiments, and application documentation. Both resulting repositories retain Java 21 and `--release 21`.

Discussion: Clone the complete history into a sibling `ccrs` checkout, remove application ownership from its HEAD, and validate every library build before creating the public repository. Publish snapshots at `https://maven.pkg.github.com/stefanmhsg/ccrs` before removing library source from `ccrs-bdi`. Then remove the library, workspace, publication, and library-plan paths from `ccrs-bdi` in one application cutover commit. The brief pre-cutover clone is staging, not a second supported topology. Select compile-time application APIs with `implementation`; modules reached only through `ServiceLoader`, SPI, `.jcm` class names, or reflection use `runtimeOnly`. Keep capability profiles explicit.

Todos:

- [x] Create `stefanmhsg/ccrs` from a full-history clone and remove every app-owned path from its HEAD.
- [x] Give the artifact consumer its own wrapper so the library repository owns no application-root Gradle build.
- [x] Move the publication endpoint and clean-consumer workflow to `https://maven.pkg.github.com/stefanmhsg/ccrs`, publish all five snapshots, and record a successful run.
- [x] Preserve the application-owned `.jcm`, `.asl`, environment, logging, experiment, and `examples.asl` content only in `ccrs-bdi`.
- [x] Preserve coordinate-only dependencies and authenticated repository configuration without tracked credentials.
- [x] Classify selected modules as `implementation` or `runtimeOnly` based on actual compile-time use.
- [x] Add capability profiles or properties for core/JaCaMo, Hypermedea, LangChain4j, and A2A combinations.
- [x] Repair or replace the known broken JaCaMo AgentSpeak test fixture so application `test` has a trustworthy result.
- [x] Run non-agent application smoke profiles without sibling CCRS sources or Maven Local.
- [x] Remove every library/workspace/publication path from `ccrs-bdi` after the new package endpoint is proven.
- [x] Prove each authoritative repository has one role, relevant history, no filesystem fallback, and no Maven Local fallback.
- [x] Audit and regression-test the concurrent multi-agent path, retain the
  pinned Hypermedea 0.4.2 dependency, and document the trusted-JVM versus
  untrusted-tenant boundary.
- [ ] Delete the temporary `stefanmhsg/ccrs-extraction-staging` repository after confirming its HEAD is preserved in `stefanmhsg/ccrs`.

Concrete steps: In `ccrs`, run the composite, every standalone publication against isolated staging, and the artifact consumer. Create and push the public repository, dispatch publication, and verify its fresh consumer. In a clean `ccrs-bdi` checkout, configure GitHub Packages credentials and run:

    .\gradlew.bat -PccrsCapabilities=none --refresh-dependencies clean test classes
    .\gradlew.bat -PccrsCapabilities=hypermedea --refresh-dependencies clean test classes
    .\gradlew.bat -PccrsCapabilities=langchain4j --refresh-dependencies clean test classes
    .\gradlew.bat -PccrsCapabilities=a2a --refresh-dependencies clean test classes
    .\gradlew.bat -PccrsCapabilities=all --refresh-dependencies clean test classes

Interactive JaCaMo and DFS configurations remain explicit user-operated application runs documented in the application README; automated separation checks do not start agents. For an offline verification, pre-populate only the declared remote dependencies, disable network access, and ensure no filesystem reference points back to the library source repository.

Validation and acceptance: `ccrs` contains no application build, `.jcm`, `.asl`, environment, logging, or experiment content at HEAD and publishes all five modules. `ccrs-bdi` contains no CCRS library/workspace source or publication workflow, compiles from a checkout without those directories, resolves selected versions from the new GitHub Packages endpoint, loads expected service providers, and exclusively owns the application content.

Outcome and notes: Implementation and validation are complete; deletion of the redundant staging repository remains. The authoritative library repository is [stefanmhsg/ccrs](https://github.com/stefanmhsg/ccrs) at `86e6c97`. Its forced composite build executed 39 tasks successfully; direct builds published all five modules to `.gradle/wp6-maven-repo`; the independent consumer resolved all five sources, Javadocs, POMs, and Gradle metadata files, loaded both strategy providers and the Hypermedea binding, compiled the public LangChain4j API, and printed the expected retry suggestion. Remote run 31271103838 then published the same five snapshots from the package-owning repository and passed its fresh consumer.

The authoritative application repository is [stefanmhsg/ccrs-bdi](https://github.com/stefanmhsg/ccrs-bdi) at `b144f4c`. It contains no library, workspace, publication workflow, library plan, Maven Local, `flatDir`, filesystem, `includeBuild`, or Gradle `project(...)` fallback. Core and JaCaMo are compile dependencies; Hypermedea, LangChain4j, and A2A are selectable runtime capabilities. A repaired minimal AgentSpeak fixture participates in ordinary tests without launching agents. All five profiles passed locally and in run 31271634626 against the final package endpoint.

The post-cutover concurrency audit keeps Hypermedea pinned at 0.4.2 and
serializes only its unsafe representation-handler access. It also gives each
agent history an independent lock and immutable read snapshots, prevents
missing-history diagnostics from listing other agents, safely initializes the
shared JaCaMo evaluator once, snapshots core configuration per evaluation,
publishes capability fallback suppliers safely, and makes strategy-registry
queries detached snapshots. Eight new tests exercise those cases. Five direct
standalone `clean build` runs and composite `verifyAll` passed 113 tests and all
Javadocs without launching agents.

Before cleanup, `stefanmhsg/ccrs-extraction-staging`, the final `stefanmhsg/ccrs`, and the local library checkout were all verified at commit `86e6c97`. GitHub refused the exact repository deletion because the current CLI token lacks `delete_repo`; grant that scope and repeat the deletion before marking WP6 Done.

### WP7: Harden same-JVM multi-agent lifecycle and capability concurrency

Status: Next

Purpose: Make the trusted single-JVM contract precise and demonstrable for two
or more agents. After this package, refreshing one opportunistic RDF source
cannot erase another source's valid guidance, removing a percept cannot leave
its derived guidance behind, default LLM calls can overlap without cross-talk,
runtime reconfiguration has deterministic next-call behavior, and the A2A and
Hypermedea adapter limits are both bounded and documented. Full LLM request and
response logs remain available to developers.

Local context: Opportunistic scanning reaches a Jason agent by two paths.
[CcrsAgent.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jason/opportunistic/CcrsAgent.java)
scans an individual percept during belief revision.
[CcrsAgentArch.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jaca/CcrsAgentArch.java)
buffers RDF observable properties by logical source and calls `scanAll` at the
next cycle boundary. Both create `ccrs(Target, Pattern, Utility)` beliefs, but
only the architecture path currently marks them
`origin(opportunistic-ccrs)`. The
[prioritize.java](ccrs-jacamo/src/main/java/ccrs/jacamo/jason/opportunistic/prioritize.java)
internal action considers every `ccrs/3` belief and keeps the highest utility
per target, so stale beliefs can affect later option ordering even though they
never cross from one agent's belief base into another's.

The default LLM provider registers one shared
[PredictionLlmStrategy.java](ccrs-core/src/main/java/ccrs/core/contingency/strategies/internal/prediction/PredictionLlmStrategy.java)
and one [Langchain4jLlmClient.java](ccrs-langchain4j/src/main/java/ccrs/capabilities/llm/langchain4j/Langchain4jLlmClient.java)
per evaluator. Calls contain per-invocation maps, prompts, responses, and parse
results. There is no CCRS mutex around `ChatModel.chat`. LangChain4j 1.10.0's
[OpenAiChatModel.java](https://github.com/langchain4j/langchain4j/blob/1.10.0/langchain4j-open-ai/src/main/java/dev/langchain4j/model/openai/OpenAiChatModel.java)
also uses per-call request/response variables, and its
[JdkHttpClient.java](https://github.com/langchain4j/langchain4j/blob/1.10.0/http-clients/langchain4j-http-client-jdk/src/main/java/dev/langchain4j/http/client/jdk/JdkHttpClient.java)
delegates each caller to the shared immutable JDK HTTP client. This means a
slow request blocks its own agent thread but does not, by library design, hold
a lock needed by the other agent. Provider rate limits, HTTP connection limits,
and custom components may still queue calls.

[A2aConsultationChannel.java](ccrs-a2a/src/main/java/ccrs/capabilities/a2a/A2aConsultationChannel.java)
shares discovered agent-card metadata, not consultation responses. Its response
references and A2A client are created per request. The default card cache is a
capability-level optimization and assumes a card is stable and independent of
the calling agent's credentials. The current card-object lookup nevertheless
holds its cache monitor during network resolution and creates avoidable
cross-agent head-of-line blocking. Hypermedea is a different case:
[CcrsHttpOperation.java](ccrs-hypermedea/src/main/java/ccrs/hypermedea/CcrsHttpOperation.java)
must serialize payload conversion because both upstream `0.4.2` and `0.5`
iterate one static `ServiceLoader`; HTTP dispatch remains concurrent, but one
slow conversion can delay another conversion in the same JVM.

Discussion: Treat opportunistic beliefs as materialized views: derived beliefs
that represent current evidence rather than permanent history. Every
architecture-generated or single-percept-generated belief must carry
`origin(opportunistic-ccrs)`, a logical `source`, a producer identifier, and a
stable evidence identifier when one percept owns the result. The architecture
must maintain a per-instance, per-source snapshot of current RDF triples. Its
existing add hook records triples; override the matching
`removeObsPropertiesBel(ArtifactId, ArtifactObsProperty, Atom)` hook to remove
triples and mark that source dirty. JaCaMo observable updates are represented by
removal/addition events. At flush, replace only the architecture-produced
beliefs for dirty source S, rescan S's complete current snapshot, and leave all
other sources and all `origin(contingency-ccrs)` beliefs untouched. If API
characterization shows the belief base is a more reliable complete snapshot
than an in-memory triple index, rebuild S from the belief base instead; the
source-scoped acceptance behavior does not change.

For the individual-percept path, record which derived beliefs belong to the
canonical identity of the original percept. When Jason deletes that percept,
delete those derived beliefs in the same belief-revision result and generate
the corresponding deletion events. Do not use one process-wide index: lifecycle
state belongs to the `CcrsAgent` instance. Add tests that establish whether a
CArtAgO RDF percept currently reaches both producer paths; if it does, assign
one producer ownership or deduplicate by producer/evidence identity instead of
allowing duplicate beliefs with different cleanup rules.

Do not add a global LLM semaphore. Prove concurrency at two levels: a barrier
fake `LlmClient` demonstrates that two `PredictionLlmStrategy.evaluate` calls
can be active simultaneously and return only their own response, and a local
in-process HTTP server configured as an OpenAI-compatible endpoint demonstrates
that two LangChain4j 1.10.0 requests arrive before either server response is
released. No API key or live model is required. Document that `LlmClient`,
`ChatModel`, `PromptBuilder`, and `LlmResponseParser` instances installed in a
shared evaluator must support concurrent calls and must not be mutated after
registration unless they provide their own synchronization. Preserve full
payload logging, but prefix the request and response records with agent ID and
a per-evaluation correlation ID so interleaved logs remain attributable.

The process-wide JaCaMo evaluator cache is a quick fix, not a redesign. Move
the cached `ContingencyCcrs` from `evaluate` into `CcrsJacamoRuntime`. A
synchronized get-or-create operation owns initialization. Configuration,
supplier, and reset methods invalidate that cache. An in-flight call keeps the
evaluator it already captured; the first later call creates one evaluator from
the new configuration. This avoids both sticky configuration and mid-call
replacement.

Keep A2A policy in `ccrs-a2a`. Replace the broad map monitor with concurrent
lookups that resolve a cache miss outside the map lock and use `putIfAbsent`
after resolution. Two callers may perform the same first resolution, which is
preferable to a JVM-wide network lock and avoids a per-key future/cache
framework. Document the default cache invariant: agent-card discovery and card
content must be public or otherwise caller-independent and stable for the
channel lifetime. If card discovery is credential-personalized, use a
separately scoped channel/process; do not claim core isolation. Card TTL or
credential-scoped cache keys are future A2A features only if a real capability
requires them.

Retain Hypermedea `0.4.2`. Record that upstream `0.5` has the same static
loader and therefore does not justify removing the conversion monitor or
upgrading the dependency. Keep regression coverage for simultaneous response
callbacks and document the narrow performance effect: requests may be in
flight together, while response-to-Jason payload conversions queue within one
JVM. Finally, document that agent names partition trusted in-process history
but are logical strings, not authenticated identities; duplicate names and
direct API access under another name can share/read that partition.

Todos:

- [ ] Add source-scoped opportunistic lifecycle tests for two sources S1 and
  S2: refreshing only S1 preserves S2; removing S1 removes only S1; contingency
  notes remain present.
- [ ] Give both opportunistic producer paths consistent `origin`, `source`,
  producer, and evidence metadata, and replace the architecture's global
  opportunistic-belief sweep with per-source replacement over complete current
  source state.
- [ ] Remove individual-percept-derived beliefs when their original percept is
  deleted, including correct Jason deletion events, and characterize/deduplicate
  any RDF percept handled by both paths.
- [ ] Add a two-caller core LLM concurrency/cross-talk regression and a
  LangChain4j 1.10.0 local-HTTP concurrency test that observes both requests
  before releasing either response.
- [ ] Document the concurrent-call/no-post-registration-mutation contract for
  shared `LlmClient`, `ChatModel`, `PromptBuilder`, `LlmResponseParser`, and
  `ConsultationChannel` implementations; retain full request/response payload
  logs and add agent/correlation fields.
- [ ] Centralize evaluator caching and invalidation in `CcrsJacamoRuntime`,
  remove the duplicate static cache from `evaluate`, and test initialization,
  reconfiguration, reset, and in-flight snapshot behavior.
- [ ] Move A2A card network resolution outside the shared cache lock, test that
  different card URIs resolve concurrently without response cross-talk, and
  document the stable caller-independent card invariant plus the separate-scope
  mitigation.
- [ ] Keep Hypermedea at `0.4.2`, record the source comparison with `0.5`, retain
  the concurrent payload-conversion regression, and document request concurrency
  versus conversion serialization.
- [ ] Add a short JaCaMo/Hypermedea documentation note that agent-name partitions
  are trusted logical routing, not authorization, and that mutually untrusted
  tenants require separate JVMs, credentials, and logs.

Concrete steps: Work from `S:\dev\ma\ccrs`. Start with focused failing
regressions in the owning modules. Use fake agents, belief bases, model servers,
and A2A/Hypermedea responses; do not start an interactive JaCaMo system or call
live external services. Then implement the lifecycle and narrow concurrency
changes and run:

    .\ccrs-core\gradlew.bat -p ccrs-core clean test
    .\ccrs-jacamo\gradlew.bat -p ccrs-jacamo clean test
    .\ccrs-langchain4j\gradlew.bat -p ccrs-langchain4j clean test
    .\ccrs-a2a\gradlew.bat -p ccrs-a2a clean test
    .\ccrs-hypermedea\gradlew.bat -p ccrs-hypermedea clean test
    .\ccrs-workspace\gradlew.bat -p ccrs-workspace --no-daemon --rerun-tasks verifyAll

Confirm the pinned dependency explicitly:

    .\ccrs-hypermedea\gradlew.bat -p ccrs-hypermedea dependencyInsight --dependency hypermedea --configuration runtimeClasspath

Validation and acceptance: A deterministic two-source test fails on the current
global sweep and passes when an S1 refresh leaves S2 guidance intact. A
single-percept test fails on the current missing-origin path and passes when
percept removal also removes its derived guidance. A two-agent LLM test reaches
maximum concurrent calls of two and maps each response to its initiating
agent; the local OpenAI-compatible server sees both requests before either is
released. Runtime tests show one initialization per configuration generation,
old configuration for an already-started call, and new configuration for the
next call after a setter. Two different A2A card URIs resolve concurrently and
responses never cross. Hypermedea's 12-caller conversion test remains green,
dependency insight selects exactly `0.4.2`, all five standalone builds and the
composite pass, and no test launches agents or uses network services outside
the local test process.

Outcome and notes: Not started. The design audit is complete. Current source
inspection establishes that default LLM calls are not library-serialized, the
A2A card cache does contain an avoidable global network critical section,
JaCaMo configuration is sticky after first evaluation, and Hypermedea `0.5`
retains the same unsafe static representation-handler loader as `0.4.2`.

### WP8: Harden versioned releases and evaluate source-repository splitting

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
- [ ] Update GitHub workflow actions to maintained Node.js 24-compatible releases, including replacing deprecated `actions/setup-java@v4`.
- [ ] Decide whether to publish a `ccrs-platform` alignment artifact.
- [ ] Compare GitHub Packages authentication friction with the intended audience and record whether Maven Central should become an additional later target.
- [ ] Decide whether any module needs its own source repository; if so, preserve history and do not change coordinates in the same step.

Concrete steps: Publish a release candidate version from a protected tag, consume it from clean BDI and fixture checkouts, then publish the first stable `0.x` version only when every plan-wide acceptance check passes.

Validation and acceptance: A release tag produces immutable versioned artifacts once, clean consumers resolve that version, API checks compare it with the previous release, and no snapshot/local repository is required.

Outcome and notes: Record the first stable version, package links, compatibility report, and repository-topology decision.

### WP9: Migrate additional consumers from Maven Local

Status: Later

Purpose: Make other CCRS consumers, beginning with `ccrs-react`, consume the same remotely published metadata and remove duplicated dependency knowledge where practical.

Local context: `../ccrs-react/react_agent/ccrs/java_runtime.py` currently searches Maven Local and Gradle caches and hardcodes runtime dependency coordinates. Its separation from `ccrs-bdi` is the desired repository ownership pattern, but its resolver predates remote GitHub Packages distribution. Its JPype runtime and materialized Java classpath must support the published Java 21 baseline.

Discussion: This work belongs primarily in each consumer repository. Do not change `ccrs-react` as part of the BDI extraction unless its own active plan is updated. Prefer resolving a generated classpath from Gradle/Maven metadata rather than maintaining a second hand-written transitive dependency list in Python.

Todos:

- [ ] Add a `ccrs-react` work package to its active plan for GitHub Packages authentication and dependency resolution.
- [ ] Replace Maven-Local-only assumptions with a remote-capable dependency materialization step.
- [ ] Remove hardcoded dependency entries after published metadata is proven sufficient.
- [ ] If local Java source development is required, use coordinate-preserving composite substitution; do not retain Maven Local or filesystem fallback resolution.
- [ ] Repeat the clean consumer and provider-discovery tests in `ccrs-react`.

Concrete steps: Work from the `ccrs-react` repository under its own guidance. Materialize the requested CCRS modules and their runtime dependencies into a deterministic classpath directory using Gradle or Maven metadata, then point JPype at that directory.

Validation and acceptance: A clean `ccrs-react` checkout authenticates to GitHub Packages, downloads the requested CCRS version and transitive dependencies, runs its JPype integration tests, and does not require a sibling `ccrs-bdi` checkout or Maven Local publication.

Outcome and notes: Record changes in the `ccrs-react` execution plan rather than duplicating detailed progress here.

## Validation and Acceptance

Plan-wide acceptance requires all of the following observable behavior:

1. Each of the five module directories can run its own wrapper `build` and publish to an explicitly selected isolated staging repository with no application root configuration and no Gradle project dependency on a sibling CCRS source directory.
2. The dependency graph remains one-directional: core has no adapter/capability dependencies; JaCaMo depends only on core at the CCRS layer; Hypermedea depends on core and JaCaMo; LangChain4j and A2A depend only on core.
3. Every generated POM and Gradle module metadata file accurately distinguishes compile API from implementation/runtime dependencies. In particular, a clean consumer can compile code using `Langchain4jLlmClient.fromModel(ChatModel)` without separately declaring LangChain4j.
4. Module tests cover core behavior, JaCaMo parsing/adaptation, Hypermedea SPI/history, LangChain4j provider behavior, and A2A provider/characterization behavior without live external services.
5. GitHub Packages hosts sources, Javadocs, POM, Gradle module metadata, and runtime jars for all five coordinates.
6. A fresh authenticated Gradle user home resolves and runs the published consumer fixtures without Maven Local, a local staging repository, sibling builds, or cached CCRS jars.
7. Service loading registers only optional providers present on the runtime classpath and tolerates missing API keys or endpoints.
8. The BDI application builds and runs as a separate coordinate-only consumer and owns all `.jcm`, `.asl`, environment, logging, and experiment content, including the relocated `examples.asl`.
9. The accepted semantic constraints remain unchanged: `strategies.internal` public classes stay accessible, and the current A2A discovery/projection behavior remains documented and tested as a simplification.
10. The composite workspace is optional: local substitutions work, but disabling them yields the same dependency shape and observable behavior from published artifacts.
11. Concurrent trusted agents receive consistent per-agent histories and
    evaluation snapshots; an in-flight operation completes once, missing
    diagnostics do not enumerate other agents, and documentation explicitly
    requires process isolation for mutually untrusted tenants.
12. Opportunistic guidance is refreshed by source and evidence: updating S1
    cannot erase S2, removing a percept removes its own derivations, and
    contingency-origin notes remain persistent.
13. Two default LangChain4j evaluations can overlap and return only their own
    results. Full prompts and responses remain logged with agent and evaluation
    correlation, while custom shared LLM components have an explicit
    concurrent-call contract.
14. Changing the JaCaMo runtime configuration or evaluator supplier affects the
    next evaluation but cannot change an already-running evaluation midway.
15. Different A2A card URIs can resolve concurrently. Shared card caching is
    documented as capability-owned and valid only for stable,
    caller-independent card metadata; consultation responses remain per call.
16. Hypermedea remains pinned at `0.4.2`; HTTP requests may overlap, payload
    conversions are intentionally serialized within one JVM, and the plan
    records that upstream `0.5` does not remove the unsafe static loader.

At every work-package stopping point, run `git status --short`, record validation in `Progress` and the package's outcome, and update discoveries or decisions that changed the approach.

## Idempotence and Recovery

Build and test commands are safe to repeat. Local publication of snapshots may replace or add local snapshot artifacts and should be treated as disposable verification output. WP4 staging artifacts under `.gradle/wp4-maven-repo` are ignored generated output and may be regenerated; do not place staging below the root `build` directory because root `clean` removes it.

WP7 uses deterministic in-process fakes and local HTTP servers. It does not
require credentials, live LLM/A2A endpoints, or running agents. Its cache and
lifecycle changes are repeatable. If a source refresh fails after old derived
beliefs are removed, leave that source with no generated snapshot and allow the
next dirty-source flush to rebuild it; never restore a process-wide sweep as a
fallback.

Do not commit a dual-build compatibility state. WP4 was prepared and validated directly in the current worktree at the user's request, with the old include, inheritance, `project(...)` dependency, and publication path removed in the same change. If a later regression requires rollback, use a focused revert rather than preservation of a legacy switch or reset of unrelated user work.

GitHub Packages publication is externally visible and is not a disposable local action. Use new snapshot builds during experimentation and protected tags for releases. Never reuse or overwrite a stable release version. If incorrect metadata is published under a snapshot, fix the build and publish a newer snapshot. If an incorrect stable version is published, leave it immutable, document it, and publish a corrected patch version.

When moving the BDI application or a module to another repository, use a temporary worktree or history-preserving split to validate the target before the ownership cutover. The temporary target is not a second supported implementation. Complete the move by enabling the target and removing the old source ownership in one work package; use Git history for recovery. Keep coordinate changes, package-host changes, and source-repository moves as separately reviewable commits inside that cutover so failures remain diagnosable.

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

The post-WP6 concurrency regression pass executed every standalone wrapper and
the composite aggregate:

    ccrs-core tests: 83 passed
    ccrs-jacamo tests: 9 passed
    ccrs-hypermedea tests: 10 passed
    ccrs-langchain4j tests: 4 passed
    ccrs-a2a tests: 7 passed
    total: 113 passed
    ccrs-workspace verifyAll: 39 tasks passed
    Hypermedea runtime selection: org.hypermedea:hypermedea:0.4.2

All five standalone `clean build --rerun-tasks` runs passed, including
Javadocs. The tests use fake operations, responses, contexts, strategies, and
provider suppliers; they do not launch JaCaMo agents or call live services.

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

WP4 ran the standalone wrappers in dependency order against the explicit
`.gradle/wp4-maven-repo` staging repository: core; JaCaMo, LangChain4j, and A2A;
then Hypermedea. Every module completed `clean build` and its
`publishMavenJavaPublicationToCcrsStagingRepository` task. The application then
completed `clean classes` against those coordinates, and the standalone
consumer completed `clean build run` with:

    - sources: 5 resolved
    - javadocs: 5 resolved
    - poms: 5 resolved
    - Gradle module metadata: 5 resolved
    Published module contracts verified
    - strategy providers: [ccrs.capabilities.a2a.A2aConsultationStrategyProvider, ccrs.capabilities.llm.langchain4j.Langchain4jPredictionStrategyProvider]
    - protocol binding: ccrs.hypermedea.CcrsHttpBinding
    - LangChain4j ChatModel API: compile and invocation passed
    BUILD SUCCESSFUL

Jar inspection found both strategy-provider descriptors and the Hypermedea
protocol-binding descriptor exactly once. The `ccrs-jacamo` jar does not contain
`examples.asl`; its sole copy is application-owned at
`src/agt/examples/contingency/examples.asl`.

The manually dispatched WP4 clean-checkout proof ran at commit `a8cde92b`:

    https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31268336198
    Build, test, and publish: success in 4m18s
    Verify clean remote consumer: success in 1m06s

The run emitted only action-version maintenance warnings; no build,
publication, artifact, or consumer check failed.

WP5 verified local-source substitution with the dedicated composite:

    .\ccrs-workspace\gradlew.bat -p ccrs-workspace \
      --no-daemon --rerun-tasks verifyAll
    40 actionable tasks: 40 executed
    BUILD SUCCESSFUL in 21s

    .\ccrs-workspace\gradlew.bat -p ccrs-workspace \
      :ccrs-jacamo:dependencyInsight --dependency ccrs-core \
      --configuration runtimeClasspath
    io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT
      -> project :ccrs-core (by composite build)

The same module, invoked through its own wrapper against the isolated
`.gradle/wp5-maven-repo`, selected a timestamped Maven snapshot instead and
completed `clean build`. After staging the other module publications, the
standalone consumer reported:

    - sources: 5 resolved
    - javadocs: 5 resolved
    - poms: 5 resolved
    - Gradle module metadata: 5 resolved
    Published module contracts verified
    - strategy providers: [ccrs.capabilities.a2a.A2aConsultationStrategyProvider, ccrs.capabilities.llm.langchain4j.Langchain4jPredictionStrategyProvider]
    - protocol binding: ccrs.hypermedea.CcrsHttpBinding
    - LangChain4j ChatModel API: compile and invocation passed
    BUILD SUCCESSFUL

WP6 established the final two-repository boundary. The package-owning library
repository completed its publication gate at commit `86e6c97`:

    https://github.com/stefanmhsg/ccrs/actions/runs/31271103838
    Build, test, and publish: success in 5m15s
    Verify fresh remote consumer: success in 57s

The application-only repository completed its fresh package-consumer matrix at
commit `b144f4c`:

    https://github.com/stefanmhsg/ccrs-bdi/actions/runs/31271634626
    none: success
    hypermedea: success
    langchain4j: success
    a2a: success
    all: success

Each matrix job used its own fresh Gradle user home and the final `ccrs`
package endpoint. The jobs ran `clean test classes` and fixture verification;
they did not launch JaCaMo agents. Both runs emitted only the already recorded
Node.js/action-version maintenance annotations.

Current coordinates:

    io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-jacamo:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-hypermedea:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-langchain4j:0.1.0-SNAPSHOT
    io.github.stefanmhsg.ccrs:ccrs-a2a:0.1.0-SNAPSHOT

Current GitHub Packages endpoint:

    https://maven.pkg.github.com/stefanmhsg/ccrs

Historical pre-WP6 endpoint:

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

After WP7, any `LlmClient`, `PromptBuilder`, `LlmResponseParser`, or
`ConsultationChannel` installed in a shared `ContingencyCcrs` must accept
concurrent calls or document and implement its own narrow serialization.
Callers must finish configuration before registration and must not mutate a
shared component during evaluations. CCRS core does not add provider-wide
locks.

In [CcrsJacamoRuntime.java](ccrs-jacamo/src/main/java/ccrs/jacamo/CcrsJacamoRuntime.java),
WP7 adds one runtime-owned accessor with this contract:

    public static ContingencyCcrs getOrCreateContingencyCcrs()

`setContingencyConfiguration`, `setContingencyCcrsSupplier`, and `reset`
invalidate the runtime-owned cached evaluator. The next accessor call creates
exactly one replacement. The AgentSpeak `evaluate` action owns no separate
static evaluator after this change.

Every transient opportunistic `ccrs/3` belief created by JaCaMo must carry
`origin(opportunistic-ccrs)`, `source(SourceId)`, and a producer annotation.
Single-percept derivations also carry a stable evidence identifier. These
annotations are lifecycle keys, not authorization data. Persistent contingency
notes continue to carry `origin(contingency-ccrs)` and are excluded from
opportunistic refresh cleanup.

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

Revision note (2026-08-08, cutover strategy): Replaced the planned additive
legacy/standalone transition with one all-library WP4 cutover. All five modules,
all coordinate consumers, and the snapshot workflow switch together; the root
subproject and publication paths are removed in the same completed change. The
later work packages were renumbered: WP5 provides optional composite
development, WP6 cuts the BDI application into its authoritative consumer
repository, WP7 hardens same-JVM multi-agent behavior, WP8 hardens releases,
and WP9 migrates additional consumers.
Explicit staging repositories and CI fixtures remain valid proof mechanisms,
while Git history supplies rollback. WP4 itself was later executed directly in
the current worktree at the user's request.

Revision note (2026-08-08, WP4 implementation): Performed the all-five-module
cutover directly in the current worktree, without a branch or worktree. Added
complete module-owned builds and wrappers, converted every CCRS dependency to a
coordinate, reduced the root to an application-only consumer, moved the
AgentSpeak example into the app tree, rewrote the publication workflow, and
proved the graph through an explicit isolated staging repository. WP4 remains
open only until the uncommitted cutover is pushed and the rewritten workflow
produces a successful clean-checkout run URL.

Revision note (2026-08-08, WP4 completion): Recorded successful clean-checkout
run 31268336198 at commit `a8cde92b`, checked the final WP4 todo, marked WP4
Done, and promoted WP5 to Now. The run's action-version deprecation annotations
were recorded as release-hardening maintenance rather than WP4 failures.

Revision note (2026-08-08, WP5 completion): Added the optional
`ccrs-workspace` composite with explicit coordinate substitutions, delegated
aggregate tasks, its own Gradle 9.2.0 wrapper, developer documentation, and a
source-path workflow check. Recorded local source-versus-artifact dependency
evidence and the full staged consumer result, marked every WP5 todo complete,
and promoted the atomic BDI application extraction in WP6 to Now.

Revision note (2026-08-08, WP6 implementation): Split the full-history baseline
into the authoritative `ccrs` research artifact and `ccrs-bdi` application
repositories. A direct publication from a newly created repository exposed
GitHub's repository-scoped Maven/Gradle package ownership through HTTP 422, so
the cutover preserved package identity by renaming the original repository to
`ccrs` and recreating `ccrs-bdi` from the validated application-only HEAD.
Recorded successful publication run 31271103838, successful five-profile BDI
consumer run 31271634626, role/fallback audits, and the non-agent validation
boundary. WP6 remains Now only until the redundant, commit-identical
`ccrs-extraction-staging` repository is deleted; the current CLI token lacks
the required `delete_repo` scope.

Revision note (2026-08-08, concurrency and isolation hardening): Investigated
the pinned Hypermedea 0.4.2 failure as a concurrent `ServiceLoader` race and
kept the dependency unchanged. Extended the fix to per-agent history safety,
single JaCaMo runtime initialization, per-evaluation configuration snapshots,
thread-safe strategy-registry snapshots, and safely published capability
fallbacks. Added eight deterministic regression tests, documented trusted-JVM
support and the untrusted-tenant process boundary across the architecture and
module READMEs, and recorded 113 passing standalone/composite tests. WP6 still
remains Now solely because the already verified staging repository has not yet
been deleted.

Revision note (2026-08-08, remaining same-JVM work): Added one bounded WP7 for
the remaining two-agent lifecycle and capability findings. It defines
source-scoped opportunistic materialized views, cleanup of single-percept
derivations, overlapping default LangChain4j calls with correlated full logs,
deterministic JaCaMo runtime cache invalidation, removal of the A2A cache's
network-wide critical section, the caller-independent card-cache contract, the
trusted logical agent-name boundary, and continued Hypermedea `0.4.2` pinning.
Upstream source comparison records that Hypermedea `0.5` retains the same
static representation-handler `ServiceLoader`. The former release and
additional-consumer packages are renumbered WP8 and WP9.
