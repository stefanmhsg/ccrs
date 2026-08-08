# CCRS Core

`ccrs-core` is the agent-agnostic CCRS library. It contains opportunistic
matching, contingency evaluation, RDF context contracts, strategy selection,
and the service-provider extension interfaces used by optional capability
modules. It has no dependency on JaCaMo, Hypermedea, LangChain4j, A2A, or
dotenv.

The detailed subsystem documentation is in the
[opportunistic README.md](src/main/java/ccrs/core/opportunistic/README.md),
[contingency README.md](src/main/java/ccrs/core/contingency/README.md), and
[RDF README.md](src/main/java/ccrs/core/rdf/README.md).

## Standalone Build

This directory is a complete Gradle build. It uses Java 21 and publishes
`io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT`.

From this directory, run:

```powershell
.\gradlew.bat build
.\gradlew.bat publishToMavenLocal
```

Neither command reads the repository-root application build. The normal
snapshot publication task is:

```powershell
.\gradlew.bat publishMavenJavaPublicationToGitHubPackagesRepository
```

GitHub publication reads `gpr.user` and `gpr.key` from the user-level Gradle
properties file, or `GITHUB_ACTOR` and `GITHUB_TOKEN` in CI. Never put those
values in this module.

An explicitly selected staging repository can be used for an isolated
coordinate-based build chain:

```powershell
.\gradlew.bat `
  -PccrsRepositoryUrl=S:/path/to/ccrs-staging-repo `
  publishMavenJavaPublicationToCcrsStagingRepository
```

The staging property changes only the Maven publication destination. It does
not introduce a sibling source dependency or an automatic Maven Local fallback.
