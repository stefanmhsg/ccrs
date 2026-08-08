# CCRS Library Consumer

This is a small standalone Gradle project that consumes the CCRS modules as
published Maven libraries. It is intentionally not included in the root
[settings.gradle](../../settings.gradle), so it behaves like a separate user
repository.

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

## Run It

First publish the CCRS modules to the repository-local Maven directory from
the repository root:

```powershell
./gradlew :ccrs-core:publishMavenJavaPublicationToCcrsLocalRepository `
  :ccrs-jacamo:publishMavenJavaPublicationToCcrsLocalRepository `
  :ccrs-hypermedea:publishMavenJavaPublicationToCcrsLocalRepository `
  :ccrs-langchain4j:publishMavenJavaPublicationToCcrsLocalRepository `
  :ccrs-a2a:publishMavenJavaPublicationToCcrsLocalRepository
```

Then run this consumer project:

```powershell
./gradlew -p examples/ccrs-library-consumer run
```

The project resolves CCRS only from
[build/local-maven-repo](../../build/local-maven-repo); it does not declare
Maven Local, so this check cannot select an older local snapshot.

## Use The Same Pattern Elsewhere

In another Gradle project, add the repositories required by the CCRS modules:

```gradle
repositories {
    mavenLocal()
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
