# Connector API split — design plan (P1)

**Status:** Plan only. No code in this PR. Implementation should land in a follow-up PR after
**issue #5305** (architecture guard) is in place to enforce the boundary.

## Goal

Move the connector SPI interfaces out of `eventmesh-connector-runtime` into a new
`eventmesh-connector-api` module so plugins depend on a stable, minimal API jar and the runtime
implements / orchestrates against that API.

## Interfaces to move

From `eventmesh-connector-runtime/src/main/java/org/apache/eventmesh/connector/`:

- `SourceConnector`
- `SinkConnector`
- `EventMeshEndpoint`
- `HttpCaller`
- `ConnectorOffsetStore`
- `CloudEventSerializer`
- `PollEntry` (value type used by both sides)

## Target layout

```
eventmesh-connector-api/
  src/main/java/org/apache/eventmesh/connector/api/
    SourceConnector.java
    SinkConnector.java
    EventMeshEndpoint.java
    HttpCaller.java
    ConnectorOffsetStore.java
    CloudEventSerializer.java
    PollEntry.java
    package-info.java
  build.gradle (deps: cloudevents-core only — no plugin imports, no HTTP libs)
```

`eventmesh-connector-runtime` depends on `:eventmesh-connector-api` and continues to provide
implementations (`EventMeshHttpEndpoint`, `RocksDBConnectorOffsetStore`, `RemoteOffsetStore`,
`InMemoryOffsetStore`, `ConnectorRuntime`, `ConnectorManager`, `ConnectorAdminServer`,
`ConnectorApplication`, `ConnectorDef`).

Each plugin under `eventmesh-connector-plugin/eventmesh-connector-*` should depend on
`:eventmesh-connector-api` instead of `:eventmesh-connector-runtime`.

## Plugin changes (mechanical)

For each of the 23 plugins:

1. `build.gradle`: replace `implementation project(":eventmesh-connector-runtime")` with
   `implementation project(":eventmesh-connector-api")`.
2. Source code: if the plugin imports `org.apache.eventmesh.connector.ConnectorRuntime` (it should
   not — plugins only use the SPI), add `implementation project(":eventmesh-connector-runtime")`
   back. Initial audit shows no plugin currently touches runtime internals.

## ArchUnit enforcement (depends on #5305)

Add a rule:

```
noClasses().that().resideInAPackage("..eventmesh.connector.plugin..")
    .should().dependOnClassesThat().resideInAPackage("..eventmesh.connector.runtime..")
    .because("plugins must depend only on the connector-api SPI, not on runtime internals")
```

This is the contract — once it passes, every plugin author who reaches into runtime internals
will fail the architecture guard.

## Migration order (sub-PRs)

- **M1 — create the module + move 7 interfaces + move package-info.** Mechanical. Touches ~24
  build.gradles but no production logic. No behaviour change.
- **M2 — switch 23 plugins from runtime to api dependency.** Each plugin's `build.gradle` swap.
  CI matrix must stay green; existing plugin tests are non-existent today (this PR adds them).
- **M3 — add ArchUnit rule (depends on #5305 having `B` mode enabled — `rule.check()` fails the
  build).** This is the enforcement moment. Without it the split is informational only.

## Risks

- **Sub-package collisions**: if any plugin imports `org.apache.eventmesh.connector.X` from
  runtime, the import path will break. Audit by `git grep "org.apache.eventmesh.connector" -- '*/src/main/'`
  before M1.
- **Javadoc / package-info drift**: the current `package org.apache.eventmesh.connector;` (no
  `.api`) means a lot of plugin code will see its `package-info.java` change. Acceptable — it's
  API contract clarification.
- **Build time**: 23 `build.gradle` edits are mechanical but the Gradle dependency graph will
  shift; expect one or two of the ~30 modules to need a transitive adjustment.

## Acceptance criteria for the implementation PR(s)

- [ ] `eventmesh-connector-api` jar builds standalone (deps: cloudevents-core only).
- [ ] `eventmesh-connector-runtime` depends on `:eventmesh-connector-api`.
- [ ] All 23 plugin modules depend on `:eventmesh-connector-api`, not on `:eventmesh-connector-runtime`.
- [ ] ArchUnit rule is added and **fails** the build if any plugin reaches into runtime internals.
- [ ] `:eventmesh-architecture-guard:test` passes.
- [ ] Existing runtime + plugin tests stay green (this PR added the baseline tests they will
      now run alongside).

## Open question

Should `PollEntry` and `CloudEventSerializer` stay in the SPI jar, or split into a
`connector-api-types` sub-jar? Recommendation: keep them together in this PR; revisit if a second
downstream consumer (e.g. a webhook sink SDK) materialises.