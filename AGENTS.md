# AGENTS.md

## Scope

- Applies to the entire `ccrs` research-artifact repository.
- This repository owns reusable CCRS libraries and library verification only.
- The JaCaMo application, `.jcm` files, AgentSpeak programs, environments,
  experiments, and application tests belong in `stefanmhsg/ccrs-bdi`.

## Architecture

- Preserve `core <- jacamo <- hypermedea`, `core <- langchain4j`, and
  `core <- a2a`.
- Keep `ccrs-core` agent-agnostic. It must not depend on JaCaMo, Jason,
  CArtAgO, Hypermedea, LangChain4j, A2A, dotenv, or application code.
- Keep provider-neutral policy, prompts, parsing, and strategy selection in
  core. Optional modules implement only their external integration.
- Contribute optional strategies through `CcrsStrategyProvider` and Java
  `ServiceLoader`; do not hard-wire them into `ccrs-jacamo`.
- Treat `strategies.internal` as conceptual grouping, not an access-control
  instruction. Preserve the public contracts documented in the repository.

## Build Boundaries

- Each of `ccrs-core`, `ccrs-jacamo`, `ccrs-hypermedea`,
  `ccrs-langchain4j`, and `ccrs-a2a` is an authoritative standalone Gradle
  build with Java 21 and `--release 21`.
- Declare CCRS dependencies by `io.github.stefanmhsg.ccrs` coordinate. Do not
  introduce Gradle project dependencies, Maven Local resolution, or filesystem
  fallback repositories.
- Use `ccrs-workspace` only for optional local composite substitution. It must
  own no production configuration or publication.
- Use explicit isolated staging through `-PccrsRepositoryUrl=...` when proving
  artifact behavior without publishing remotely.

## Verification

- Run a module's wrapper from its own directory.
- Run all module builds with `cd ccrs-workspace && ./gradlew verifyAll`.
- Validate published metadata, service descriptors, sources, Javadocs, and
  consumer behavior with `examples/ccrs-library-consumer`.
- Do not add secrets, API keys, usernames, tokens, or private endpoints to
  tracked files.

## Documentation And Plans

- Read [CCRS_LIBRARY.md](CCRS_LIBRARY.md) before changing module boundaries.
- For complex work, consult relevant `PLAN_*.md` files first and keep active
  plans current with progress, decisions, discoveries, validation, and outcomes.
- Update module READMEs, examples, service-loader documentation, and the active
  plan in the same change as a boundary or public-contract change.
