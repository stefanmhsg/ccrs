# CCRS Library Consumer

This is a small standalone Gradle project that consumes the CCRS modules as
published Maven libraries. It is intentionally not included in the root
[settings.gradle](../../settings.gradle), so it behaves like a separate user
repository. It can resolve from either an explicitly selected staging
repository or GitHub Packages, without using Maven Local.

## What It Shows

- Depends only on the five published `io.github.stefanmhsg.ccrs` coordinates.
- Uses `ContingencyConfiguration` with non-default strategy options.
- Creates CCRS through `ContingencyCcrsFactory.withCoreDefaults(config)`.
- Provides a minimal in-memory `CcrsContext`.
- Builds a retryable `Situation` and prints the selected `StrategyResult`.
- Compiles and invokes `Langchain4jLlmClient.fromModel(ChatModel)` without an
  extra LangChain4j dependency, proving the published API scope.
- Discovers the LangChain4j and A2A strategy providers and the Hypermedea
  protocol binding from the service descriptors in published jars.
- Resolves the sources jar, Javadocs jar, POM, and Gradle module metadata for
  every CCRS coordinate as part of `check` and `build`.

## Run It From An Isolated Staging Repository

From the repository root, select one ignored staging directory and publish the
standalone modules to it in dependency order:

```powershell
$stagingRepository = "$PWD/.gradle/ccrs-staging-repo"

.\ccrs-core\gradlew.bat -p ccrs-core `
  "-PccrsRepositoryUrl=$stagingRepository" `
  clean build publishMavenJavaPublicationToCcrsStagingRepository

.\ccrs-jacamo\gradlew.bat -p ccrs-jacamo `
  "-PccrsRepositoryUrl=$stagingRepository" `
  --refresh-dependencies clean build `
  publishMavenJavaPublicationToCcrsStagingRepository

.\ccrs-langchain4j\gradlew.bat -p ccrs-langchain4j `
  "-PccrsRepositoryUrl=$stagingRepository" `
  --refresh-dependencies clean build `
  publishMavenJavaPublicationToCcrsStagingRepository

.\ccrs-a2a\gradlew.bat -p ccrs-a2a `
  "-PccrsRepositoryUrl=$stagingRepository" `
  --refresh-dependencies clean build `
  publishMavenJavaPublicationToCcrsStagingRepository

.\ccrs-hypermedea\gradlew.bat -p ccrs-hypermedea `
  "-PccrsRepositoryUrl=$stagingRepository" `
  --refresh-dependencies clean build `
  publishMavenJavaPublicationToCcrsStagingRepository
```

Then resolve only that repository and run this consumer:

```powershell
.\gradlew.bat -p examples/ccrs-library-consumer `
  "-PccrsRepositoryUrl=$stagingRepository" `
  --refresh-dependencies clean build run
```

The staging repository is under the ignored root `.gradle` directory so root
`clean` does not delete it before the application or consumer resolves the
artifacts. The project does not declare Maven Local, so this check cannot
select an older local snapshot.

## Run It From GitHub Packages

GitHub Packages requires authentication for Maven/Gradle package downloads.
Create a classic GitHub personal access token with `read:packages`, then put
the credentials in the user-level Gradle file
`%USERPROFILE%\.gradle\gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_GITHUB_PAT
```

Do not add those properties to this project or any other tracked file. Resolve
only the remote CCRS snapshots, verify every published artifact kind, and run
the example with:

```powershell
.\gradlew.bat -p examples/ccrs-library-consumer `
  --refresh-dependencies `
  clean build run
```

The
[Publish CCRS snapshots workflow](../../.github/workflows/publish-ccrs-snapshots.yml)
performs the same check with a fresh `GRADLE_USER_HOME` after publication. It
uses the workflow-provided `GITHUB_TOKEN`; a separate repository secret is not
required when publishing to and consuming from this repository's package
registry.

## Use The Same Pattern Elsewhere

In another Gradle project, add GitHub Packages and the repositories required by
the selected CCRS modules:

```gradle
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/stefanmhsg/ccrs-bdi')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
        }
        content {
            includeGroup('io.github.stefanmhsg.ccrs')
        }
        mavenContent {
            snapshotsOnly()
        }
    }
    mavenCentral()
    maven { url = uri('https://raw.githubusercontent.com/jacamo-lang/mvn-repo/master') }
    maven { url = uri('https://repo.gradle.org/gradle/libs-releases') }
    maven { url = uri('https://hypermedea.github.io/maven') }
    maven { url = uri('https://jitpack.io') }
}
```

Then add only the modules your project needs:

```gradle
dependencies {
    implementation 'io.github.stefanmhsg.ccrs:ccrs-core:0.1.0-SNAPSHOT'

    // Optional capability modules:
    runtimeOnly 'io.github.stefanmhsg.ccrs:ccrs-langchain4j:0.1.0-SNAPSHOT'
    runtimeOnly 'io.github.stefanmhsg.ccrs:ccrs-a2a:0.1.0-SNAPSHOT'
    runtimeOnly 'io.github.stefanmhsg.ccrs:ccrs-hypermedea:0.1.0-SNAPSHOT'
}
```

Use the same central configuration object to tune built-in strategy behavior:

```java
ContingencyConfiguration config = ContingencyConfiguration.builder()
    .retry(options -> options
        .maxAttempts(5)
        .initialDelayMs(500))
    .predictionLlm(options -> options.maxHistoryActions(20))
    .build();

ContingencyCcrs ccrs = ContingencyCcrsFactory.withDefaultsAndDiscoveredProviders(config);
```
