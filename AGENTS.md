# AGENTS.md

Apache EventMesh — a serverless event-driven middleware. The main deliverable is the
Java **runtime** (`eventmesh-runtime`); everything else is plugins, connectors, SDKs, or ops tooling.

## Toolchain

- **JDK:** build targets **Java 8** (`sourceCompatibility = "1.8"`). Do not use Java 9+ APIs.
  `.sdkmanrc` pins `java=8.0.492-tem`. CI additionally builds on JDK 11.
- **ANTLR codegen needs JDK 11:** `./gradlew generateGrammarSource` (used by
  `eventmesh-connector-jdbc`) does not run on JDK 8. CI runs codegen under JDK 11 first,
  then switches to JDK 8/11 for the build. If building on pure JDK 8, pre-run codegen on 11
  or `clean build` may fail on the jdbc connector.
- **Gradle wrapper is the source of truth:** use `./gradlew`, not system `gradle`.

## Common commands

```bash
# Full CI-equivalent build (what .github/workflows/ci.yml runs):
./gradlew clean build dist jacocoTestReport --parallel --daemon \
  -x spotlessJava -x generateGrammarSource -x generateDistLicense -x checkDeniedLicense

# ANTLR codegen (JDK 11):
./gradlew generateGrammarSource

# Format / verify formatting (Spotless; NOT auto-run during build):
./gradlew spotlessApply          # fix
./gradlew spotlessJavaCheck      # verify

# Run all tests / one module / one test:
./gradlew test
./gradlew :eventmesh-runtime:test
./gradlew :eventmesh-common:test --tests "org.apache.eventmesh.common.SomeTest"
./gradlew :eventmesh-common:test --tests "*.methodName"

# Assemble the distribution (dist/apps, dist/bin, dist/conf, dist/lib):
./gradlew dist
# Populate dist/plugin/<type>/<name>/ from every plugin module:
./gradlew installPlugin
# Package dist/ into tar.gz / zip:
./gradlew tar zip

# Release packaging (from install.sh):
./gradlew clean -Pdev=true -Pjdk=1.8 dist tar zip
```

Suggested verification order after changes: **spotless → checkstyle → build/test**. Note
`build` excludes `spotlessJava`, so formatting must be checked explicitly.

## Repo layout (Gradle modules)

Only modules listed in `settings.gradle` are part of the Gradle build. Within it:

- `eventmesh-runtime` — the server. Real entrypoint:
  `runtime/.../boot/EventMeshStartup.java` (`eventmesh-starter` is a thin launcher that calls it).
- `eventmesh-common`, `eventmesh-spi` — shared code and SPI loader.
- `*-plugin` trees — each has an `*-api` module plus implementations. Plugin modules carry
  `gradle.properties` declaring `pluginType` + `pluginName` (e.g. storage/rocketmq).
  `installPlugin` discovers modules by those two properties — a new plugin/connector won't
  ship without them.
- `eventmesh-storage-plugin` (rocketmq, kafka, pulsar, redis, rabbitmq, standalone),
  `eventmesh-security-plugin`, `eventmesh-meta` (nacos/etcd/consul/zookeeper/raft),
  `eventmesh-protocol-plugin`, `eventmesh-metrics-plugin`, `eventmesh-trace-plugin`,
  `eventmesh-retry`, `eventmesh-registry`.
- `eventmesh-openconnect` + `eventmesh-connectors/*` — connector framework and the many
  source/sink connectors built on it.
- `eventmesh-function` (filter + transformer), `eventmesh-admin-server` (Spring Boot),
  `eventmesh-runtime-v2`.

### NOT part of the Gradle build (separate toolchains)

- `eventmesh-operator/` — Go/Kubebuilder K8s operator. Use `make build`, `make test`,
  `make run`, `make manifests`, `make generate`. Tests need `setup-envtest` kubebuilder assets.
- `eventmesh-sdks/eventmesh-sdk-go/` — Go module: `go test ./...`, `make lint` (golangci-lint).
- `eventmesh-sdks/eventmesh-sdk-rust/` — Cargo crate (MSRV 1.75.0).
- `eventmesh-sdks/eventmesh-sdk-c/` — C SDK built with `make -C ./eventmesh-sdks/eventmesh-sdk-c`;
  has git submodules (`json-c`, `curl`) — checkout with `--recurse-submodules` / `submodules: true`.
- Only `eventmesh-sdks/eventmesh-sdk-java` is a Gradle module.

## Code style & license headers (build-enforced)

- **Apache license header is required** on virtually every source file. Checkstyle enforces
  per-extension headers from `style/checkstyle-header-*.txt`; `.licenserc.yaml` (run via
  apache/skywalking-eyes in CI) double-checks. Exempt extensions include `.md`, `.json`,
  `.txt`, `.iml`, `LICENSE`, `NOTICE`. When creating a file, copy the header from a neighbor.
- **Checkstyle is strict:** `style/checkStyle.xml`, `maxWarnings = 0`, `ignoreFailures = false`.
  Any warning fails the build. Max line length **150**. `UnusedImports`, `RedundantImport`,
  and `AvoidStarImport` are active.
- **Import order** (enforced by both checkstyle and Spotless): static groups first, then
  `org.apache.eventmesh, org.apache, java, javax, org, io, net, junit, com, lombok`, each
  group separated by a blank line.
- **Spotless** uses the Eclipse formatter at `style/task/eventmesh-spotless-formatter.xml`.
  Run `./gradlew spotlessApply` before committing; `enforceCheck = false` means it is NOT
  triggered automatically by `check`/`build`.
- **Never hand-edit generated code.** Excluded from checkstyle/spotless: gRPC protos
  (`**/protos**`, `common/protocol/grpc/**`), `connector/jdbc/antlr4/autogeneration/**`,
  `connector/openfunction/client/*Grpc*`, `meta/raft/rpc/**`.

## Dependencies

Versions are centralized via the Spring `dependency-management` plugin in the root
`build.gradle` (`dependencyManagement { ... }`). For managed coordinates, **do not specify a
version** in subproject `build.gradle` files. To find an artifact's managed version, grep the
root `build.gradle`.

## Docker / runtime

- `docker-compose.yml` requires a profile: `--profile standalone` (in-memory) or
  `--profile rocketmq`. `docker compose up` with no profile starts nothing by design.
- Runtime ports: TCP `10000`, HTTP `10105`, gRPC `10205`, Admin `10106`.
- Dockerfiles: `docker/Dockerfile_jdk8`, `docker/Dockerfile_jdk11` (run `./gradlew build dist`
  then `installPlugin`).
- Runtime config: `eventmesh-runtime/conf/eventmesh.properties`; start script `bin/start.sh`.

## License compatibility

`./gradlew checkDeniedLicense` (depends on `generateDistLicense`) fails the build if any
dependency uses an incompatible license (GPL/AGPL/LGPL, SSPL, BUSL, etc.). If you add a
dependency that trips this, either remove it or extend the `allowedArtifacts` list in the
`checkDeniedLicense` task in `build.gradle` (current exemptions documented there).

## Build telemetry

Gradle is wired to Apache Develocity (`https://develocity.apache.org`, projectId `eventmesh`).
`DEVELOCITY_ACCESS_KEY` is only needed in CI for authenticated build scans; local builds work
without it and uploads run in the background.
