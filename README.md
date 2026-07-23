# kotlin-local-metrics

Local build, application startup, and test metrics for Kotlin projects.

This repository is currently scaffolded as a Gradle monorepo following the design in
[issue #1](https://github.com/agoda-com/kotlin-local-metrics/issues/1).

## Modules

- `metrics-core`: shared metrics payloads and transport
- `gradle-build-metrics-plugin`: settings-scoped local build metrics
- `ktor-startup-metrics`: Ktor application startup metrics
- `junit5-test-metrics`: auto-discovered JUnit Platform test metrics

The module boundaries are in place; metrics collection APIs and behavior will be added
incrementally.

## Build

The project requires a Java 17 or newer runtime to run Gradle and produces Java 11
compatible artifacts.

```shell
./gradlew build
```

## Publishing

The GitHub Actions workflow follows the release setup from
[java-local-metrics](https://github.com/agoda-com/java-local-metrics):

- every push builds and tests all modules;
- non-`main` pushes exercise a JReleaser dry-run;
- `main` pushes stage and publish Maven Central artifacts.
