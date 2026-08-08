# CCRS Composite Workspace

This Gradle composite provides one-command development across all five
standalone CCRS libraries. It includes each complete build and substitutes its
published Maven coordinate with the matching local source project.

The workspace is optional development tooling. It owns no production source,
dependencies, repositories, Java configuration, or publication. Removing this
directory does not change any module or consumer build; their dependency
declarations remain Maven coordinates.

## Verify All Local Sources

From this directory, run:

```powershell
.\gradlew.bat verifyAll
```

`verifyAll` delegates to the `build` task in
[ccrs-core](../ccrs-core), [ccrs-jacamo](../ccrs-jacamo),
[ccrs-hypermedea](../ccrs-hypermedea),
[ccrs-langchain4j](../ccrs-langchain4j), and [ccrs-a2a](../ccrs-a2a).
Dependencies such as
`io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT` resolve to the included
local `ccrs-core` build for this invocation. Use `testAll` when only the module
test tasks are needed.

Open this directory as the Gradle project in an IDE to edit and navigate all
five included builds together.

## Build One Module In Isolation

Run a module's own wrapper from that module directory:

```powershell
cd ..\ccrs-core
.\gradlew.bat build
```

A dependent build invoked directly is not part of this composite. For example,
running [ccrs-jacamo's wrapper](../ccrs-jacamo/gradlew.bat) from
`ccrs-jacamo` resolves `ccrs-core` from GitHub Packages, or from the one
explicit staging repository selected with `-PccrsRepositoryUrl=...`. It never
falls back to sibling source or Maven Local.

## Verify Published Artifacts

The standalone
[CCRS library consumer](../examples/ccrs-library-consumer/README.md) is not
included here. Run it separately to verify published artifacts rather than
local source substitution. The
[snapshot workflow](../.github/workflows/publish-ccrs-snapshots.yml) verifies
both paths: it runs this workspace first, then builds modules and consumers
through an isolated Maven staging repository, publishes the snapshots, and
runs the consumer with a fresh Gradle user home.
